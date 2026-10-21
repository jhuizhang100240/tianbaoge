package com.tbgbook.bookstore.app.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.RScoredSortedSet;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.app.mapper.AppCatalogMapper;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.service.IAppCatalogService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.member.domain.MemberMessage;
import com.tbgbook.bookstore.member.service.IMemberMessageService;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 会员端目录 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AppCatalogServiceImpl implements IAppCatalogService
{
    private static final String CATEGORY_CACHE = "app:catalog:categories";

    private static final String HOT_COUNTS = "app:catalog:hot-keywords";

    private final AppCatalogMapper catalogMapper;

    private final ISysConfigService configService;

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    private final IMemberMessageService memberMessageService;

    @Override
    public Map<String, Object> home()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> promotions = catalogMapper.activePromotions();
        data.put("banners", promotions);
        data.put("recommend", recommendModule("home_guess", 8));
        data.put("categories", categoryTree());
        data.put("promotions", promotions);
        Long memberId = AppContext.getMemberId();
        long unread = 0;
        if (memberId != null)
        {
            MemberMessage query = new MemberMessage();
            query.setMemberId(memberId);
            query.setIsRead(0);
            unread = memberMessageService.selectMemberMessageList(query).size();
        }
        data.put("unreadMessageCount", unread);
        return data;
    }

    @Override
    public Map<String, Object> recommend(String positionCode, int pageNum, int pageSize)
    {
        int page = Math.max(1, pageNum);
        int size = clampPageSize(pageSize);
        List<Map<String, Object>> all = recommendModule(positionCode, Math.min(size * page, 100));
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", all.subList(from, to));
        data.put("strategy", all.isEmpty() ? "fallback" : all.get(0).get("strategy"));
        return data;
    }

    @Override
    public Map<String, Object> search(String keyword, Long categoryId, Integer bookType,
            BigDecimal minPrice, BigDecimal maxPrice, boolean onlyAvailable, boolean includeUsed,
            String sortBy, int pageNum, int pageSize)
    {
        int page = Math.max(1, pageNum);
        int size = clampPageSize(pageSize);
        if (StringUtils.hasText(keyword))
        {
            RScoredSortedSet<String> counts = redissonClient.getScoredSortedSet(HOT_COUNTS);
            Double current = counts.getScore(keyword.trim());
            counts.addScore(keyword.trim(), current == null ? 1D : current + 1D);
        }
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = new ArrayList<>();
        long total = 0;
        if (!includeUsed)
        {
            list.addAll(catalogMapper.searchBooks(keyword, categoryId, bookType, minPrice, maxPrice,
                    onlyAvailable, sortBy, offset, size));
            total = catalogMapper.countBooks(keyword, categoryId, bookType, minPrice, maxPrice,
                    onlyAvailable);
        }
        else
        {
            int fetch = Math.min(page * size, 500);
            List<Map<String, Object>> newBooks = catalogMapper.searchBooks(keyword, categoryId, bookType,
                    minPrice, maxPrice, onlyAvailable, sortBy, 0, fetch);
            List<Map<String, Object>> used = catalogMapper.searchUsedBooks(keyword, categoryId,
                    minPrice, maxPrice, sortBy, 0, fetch);
            newBooks.forEach(item -> item.put("kind", "new"));
            used.forEach(item -> item.put("kind", "used"));
            list.addAll(newBooks);
            list.addAll(used);
            sortCatalog(list, sortBy);
            total = catalogMapper.countBooks(keyword, categoryId, bookType, minPrice, maxPrice, onlyAvailable)
                    + catalogMapper.countSearchUsedBooks(keyword, categoryId, minPrice, maxPrice);
            list = list.size() > offset
                    ? new ArrayList<>(list.subList(offset, Math.min(offset + size, list.size())))
                    : new ArrayList<>();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    @Override
    public Map<String, Object> scan(String code)
    {
        if (!StringUtils.hasText(code))
        {
            throw BizErrors.badRequest("扫码内容不能为空");
        }
        Map<String, Object> used = catalogMapper.usedBookByBarCode(code.trim());
        if (used != null)
        {
            Long usedBookId = toLong(used.get("usedBookId"));
            return usedBookDetail(usedBookId);
        }
        Map<String, Object> bookRef = catalogMapper.bookByIsbn(code.trim());
        if (bookRef == null)
        {
            throw BizErrors.notFound("条码未命中");
        }
        return bookDetail(toLong(bookRef.get("bookId")));
    }

    @Override
    public Map<String, Object> bookDetail(Long bookId)
    {
        Map<String, Object> book = catalogMapper.bookById(bookId);
        if (book == null)
        {
            throw BizErrors.notFound("图书", bookId);
        }
        Map<String, Object> stock = catalogMapper.bookStockSummary(bookId);
        List<Map<String, Object>> stocks = catalogMapper.stockByLocation(bookId);
        List<Map<String, Object>> summaries = catalogMapper.publishedSummaries(bookId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("book", book);
        data.put("stock", stock);
        data.put("stocks", stocks);
        data.put("summary", summaryContent(book, summaries, 1));
        data.put("audience", summaryContent(book, summaries, 2));
        data.put("sellingPoints", splitTags(summaryContent(book, summaries, 3)));
        data.put("reviews", summaries.stream().filter(item -> toInt(item.get("genType")) == 4).toList());
        data.put("sourceType", summarySource(book, summaries));
        data.put("usedBooks", catalogMapper.usedBooksByBook(bookId));
        Long categoryId = toLong(book.get("categoryId"));
        data.put("relatedBooks", categoryId == null ? List.of()
                : catalogMapper.relatedBooks(categoryId, bookId, 6));
        data.put("borrowable", toLong(stock == null ? null : stock.get("qtyAvailable")) > 0);
        return data;
    }

    @Override
    public Map<String, Object> bookSummary(Long bookId)
    {
        Map<String, Object> book = catalogMapper.bookById(bookId);
        if (book == null)
        {
            throw BizErrors.notFound("图书", bookId);
        }
        List<Map<String, Object>> summaries = catalogMapper.publishedSummaries(bookId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summaryContent(book, summaries, 1));
        data.put("audience", summaryContent(book, summaries, 2));
        data.put("sellingPoints", splitTags(summaryContent(book, summaries, 3)));
        data.put("reviews", summaries.stream().filter(item -> toInt(item.get("genType")) == 4).toList());
        data.put("sourceType", summarySource(book, summaries));
        return data;
    }

    @Override
    public List<Map<String, Object>> hotKeywords()
    {
        RScoredSortedSet<String> counts = redissonClient.getScoredSortedSet(HOT_COUNTS);
        List<String> words = new ArrayList<>(counts.valueRangeReversed(0, 10));
        List<Map<String, Object>> result = new ArrayList<>();
        if (words.isEmpty())
        {
            List<String> seeds = List.of("考研英语", "高等数学", "四级词汇", "教材", "小说");
            for (int i = 0; i < seeds.size(); i++)
            {
                result.add(keywordItem(seeds.get(i), i + 1, i < 3));
            }
            return result;
        }
        int rank = 1;
        for (String word : words)
        {
            result.add(keywordItem(word, rank, rank <= 3));
            rank++;
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> categoryTree()
    {
        RBucket<String> cache = redissonClient.getBucket(CATEGORY_CACHE);
        String cached = cache.get();
        if (StringUtils.hasText(cached))
        {
            try
            {
                return objectMapper.readValue(cached, new TypeReference<List<Map<String, Object>>>() {});
            }
            catch (Exception ignored)
            {
                // 重新构建
            }
        }
        List<Map<String, Object>> rows = catalogMapper.categories();
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> node = new LinkedHashMap<>(row);
            node.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(toLong(row.get("categoryId")), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> node : nodes.values())
        {
            Long parentId = toLong(node.get("parentId"));
            Map<String, Object> parent = parentId == null ? null : nodes.get(parentId);
            if (parent == null)
            {
                roots.add(node);
            }
            else
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(node);
            }
        }
        try
        {
            cache.set(objectMapper.writeValueAsString(roots), Duration.ofMinutes(10));
        }
        catch (Exception ignored)
        {
            // 缓存失败不影响响应
        }
        return roots;
    }

    @Override
    public Map<String, Object> usedBookList(String keyword, Long categoryId, Long conditionGradeId,
            BigDecimal minPrice, BigDecimal maxPrice, String sortBy, int pageNum, int pageSize)
    {
        int size = clampPageSize(pageSize);
        int offset = (Math.max(1, pageNum) - 1) * size;
        List<Map<String, Object>> list = catalogMapper.usedBookList(keyword, categoryId, conditionGradeId,
                minPrice, maxPrice, sortBy, offset, size);
        long total = catalogMapper.countUsedBookList(keyword, categoryId, conditionGradeId, minPrice, maxPrice);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        return data;
    }

    @Override
    public Map<String, Object> usedBookDetail(Long usedBookId)
    {
        Map<String, Object> used = catalogMapper.usedBookById(usedBookId);
        if (used == null || toInt(used.get("stockStatus")) != 1)
        {
            throw BizErrors.notFound("二手书副本", usedBookId);
        }
        Long bookId = toLong(used.get("bookId"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("usedBook", used);
        data.put("book", bookId == null || bookId == 0 ? null : catalogMapper.bookById(bookId));
        data.put("relatedNewBook", bookId == null || bookId == 0 ? null : catalogMapper.bookById(bookId));
        return data;
    }

    private List<Map<String, Object>> recommendModule(String positionCode, int limit)
    {
        Long memberId = AppContext.getMemberId();
        List<Map<String, Object>> personalized = memberId == null ? List.of()
                : catalogMapper.personalizedBooks(memberId, limit);
        boolean fallback = personalized.isEmpty();
        List<Map<String, Object>> source = fallback ? catalogMapper.hotBooks(limit) : personalized;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : source)
        {
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("recReason", fallback ? "本周热销" : "根据你的购买记录");
            row.put("strategy", fallback ? "fallback" : "personalized");
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> keywordItem(String keyword, int rank, boolean hot)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("keyword", keyword);
        item.put("rank", rank);
        item.put("isHot", hot);
        return item;
    }

    private void sortCatalog(List<Map<String, Object>> list, String sortBy)
    {
        Comparator<Map<String, Object>> comparator;
        if ("price_asc".equals(sortBy))
        {
            comparator = Comparator.comparing(item -> toDecimal(item.get("usedPrice") != null
                    ? item.get("usedPrice") : item.get("price")));
        }
        else if ("price_desc".equals(sortBy))
        {
            comparator = Comparator.comparing(
                    (Map<String, Object> item) -> toDecimal(item.get("usedPrice") != null
                            ? item.get("usedPrice") : item.get("price"))).reversed();
        }
        else
        {
            comparator = Comparator.comparing(
                    (Map<String, Object> item) -> toLong(item.get("saleCount"))).reversed();
        }
        list.sort(comparator);
    }

    private String summaryContent(Map<String, Object> book, List<Map<String, Object>> summaries, int genType)
    {
        for (Map<String, Object> summary : summaries)
        {
            if (toInt(summary.get("genType")) == genType)
            {
                return String.valueOf(summary.get("content") == null ? "" : summary.get("content"));
            }
        }
        if (genType == 1 && toInt(book.get("summarySource")) == 1)
        {
            return String.valueOf(book.get("summary") == null ? "" : book.get("summary"));
        }
        return "";
    }

    private Object summarySource(Map<String, Object> book, List<Map<String, Object>> summaries)
    {
        if (!summaries.isEmpty())
        {
            return summaries.get(0).get("sourceType");
        }
        return toInt(book.get("summarySource")) == 1 ? 1 : null;
    }

    private List<String> splitTags(String content)
    {
        if (!StringUtils.hasText(content))
        {
            return List.of();
        }
        return List.of(content.split("[,，、\\n]")).stream().map(String::trim)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    private int clampPageSize(int pageSize)
    {
        int max = intConfig("biz.page.max.size", 100);
        if (pageSize > max)
        {
            throw BizErrors.ruleViolated("pageSize 超过 biz.page.max.size 上限");
        }
        return Math.max(1, pageSize);
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (!StringUtils.hasText(raw))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return 0L;
        }
        if (value instanceof Boolean bool)
        {
            return bool ? 1L : 0L;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInt(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Boolean bool)
        {
            return bool ? 1 : 0;
        }
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private BigDecimal toDecimal(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
