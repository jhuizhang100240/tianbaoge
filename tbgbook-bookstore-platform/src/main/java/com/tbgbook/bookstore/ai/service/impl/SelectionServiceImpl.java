package com.tbgbook.bookstore.ai.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.ai.core.AiCircuitBreaker;
import com.tbgbook.bookstore.ai.core.AiGuard;
import com.tbgbook.bookstore.ai.core.AiUnavailableException;
import com.tbgbook.bookstore.ai.core.CallLogEntry;
import com.tbgbook.bookstore.ai.core.CallLogRecorder;
import com.tbgbook.bookstore.ai.core.PromptResolution;
import com.tbgbook.bookstore.ai.core.PromptResolver;
import com.tbgbook.bookstore.ai.core.QuotaGuard;
import com.tbgbook.bookstore.ai.mapper.AiStatsMapper;
import com.tbgbook.bookstore.ai.service.ISelectionService;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.service.IBookService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.stock.domain.Location;
import com.tbgbook.bookstore.stock.domain.StockIn;
import com.tbgbook.bookstore.stock.domain.StockInItem;
import com.tbgbook.bookstore.stock.service.ILocationService;
import com.tbgbook.bookstore.stock.service.IStockInService;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * AI 选品与补货建议 Service 实现（AI 域设计稿 §5.9）。
 *
 * <p>统计输入 → 模型给出补货/淘汰建议（JSON）→ 解析失败回落规则建议；
 * 采纳只生成采购入库单**草稿**（不动库存），不依赖 AI。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class SelectionServiceImpl implements ISelectionService
{
    private static final long STORE_ID = 1L;

    private static final int SCENE_SELECTION = 5;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SYSTEM_PROMPT = "你是天宝阁书店的采购顾问。"
            + "只能依据给定的经营统计提出补货或淘汰建议；不得编造数据；只输出 JSON，不要输出多余文字。";

    private final AiStatsMapper aiStatsMapper;

    private final PromptResolver promptResolver;

    private final ChatModel chatModel;

    private final AiGuard aiGuard;

    private final AiCircuitBreaker aiCircuitBreaker;

    private final QuotaGuard quotaGuard;

    private final CallLogRecorder callLogRecorder;

    private final ISysConfigService configService;

    private final IBookService bookService;

    private final ILocationService locationService;

    private final IStockInService stockInService;

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> suggest(Integer suggestType, Integer limit)
    {
        int size = limit == null ? 20 : limit;
        if (size < 1 || size > 50)
        {
            throw BizErrors.badRequest("条数上限取值 1-50");
        }
        if (suggestType != null && suggestType != 1 && suggestType != 2)
        {
            throw BizErrors.badRequest("建议类型取值 1补货 / 2淘汰");
        }
        aiGuard.ensureUsable(AiGuard.AiChannel.CHAT);
        quotaGuard.checkGlobalDaily();
        quotaGuard.checkMonthBudget();

        List<Map<String, Object>> metrics = aiStatsMapper.selectSelectionMetrics(size);
        Map<Long, Map<String, Object>> metricByBook = new HashMap<>();
        for (Map<String, Object> row : metrics)
        {
            metricByBook.put(asLong(row.get("bookId")), row);
        }
        double seasonFactor = seasonFactor();
        List<Map<String, Object>> items = new ArrayList<>();
        if (!metrics.isEmpty())
        {
            PromptResolution resolution = promptResolver.resolve(SCENE_SELECTION, null);
            Map<String, Object> variables = new HashMap<>();
            variables.put("metrics", buildMetrics(metrics, seasonFactor));
            variables.put("rules", "补货：库存低且近 90 天有动销；淘汰：库存高且近 90 天无借阅；"
                    + "教材季（2 月 / 8-9 月）适度提前备货。");
            String userPrompt = promptResolver.render(resolution.getContent(), variables);
            String content = callModel(userPrompt);
            items = parseSuggestions(content, metricByBook, seasonFactor, size);
        }
        if (items.isEmpty())
        {
            items = ruleSuggestions(metrics, size, seasonFactor);
        }
        if (suggestType != null)
        {
            int wanted = suggestType;
            items.removeIf(item -> !Integer.valueOf(wanted).equals(item.get("suggestType")));
        }
        if (items.size() > size)
        {
            items = new ArrayList<>(items.subList(0, size));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("seasonFactor", seasonFactor);
        result.put("generatedAt", LocalDateTime.now().format(TIME_FORMAT));
        return result;
    }

    @Override
    public Map<String, Object> adopt(Long[] bookIds, Map<String, Object> suggestQty, String remark)
    {
        if (bookIds == null || bookIds.length == 0)
        {
            throw BizErrors.ruleViolated("采纳数为 0");
        }
        Map<String, Object> quantities = suggestQty == null ? new HashMap<>() : suggestQty;
        List<String> problems = new ArrayList<>();
        List<StockInItem> items = new ArrayList<>();
        Location location = defaultLocation();
        for (Long bookId : bookIds)
        {
            Object rawQty = quantities.get(String.valueOf(bookId));
            int qty = rawQty instanceof Number ? ((Number) rawQty).intValue() : -1;
            if (qty <= 0)
            {
                throw BizErrors.ruleViolated("采纳数为 0：图书 " + bookId + " 缺少有效建议数量");
            }
            Book book = bookService.getById(bookId);
            if (book == null || !Integer.valueOf(1).equals(book.getStatus())
                    || (book.getPublishStatus() != null && !Integer.valueOf(1).equals(book.getPublishStatus())))
            {
                problems.add(book == null ? String.valueOf(bookId) : book.getTitle());
                continue;
            }
            StockInItem item = new StockInItem();
            item.setBookId(bookId);
            item.setLocationId(location.getLocationId());
            item.setOrderQty(qty);
            item.setActualQty(qty);
            items.add(item);
        }
        if (!problems.isEmpty())
        {
            throw BizErrors.ruleViolated("包含已下架或停售的图书：" + String.join("、", problems));
        }

        StockIn stockIn = new StockIn();
        stockIn.setStoreId(STORE_ID);
        stockIn.setInType(1);
        stockIn.setSupplierName("");
        stockIn.setStatus(1);
        stockIn.setRemark(remark == null ? "" : remark);
        stockIn.setItems(items);
        stockInService.addStockIn(stockIn);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockInId", stockIn.getInId());
        result.put("stockInNo", stockIn.getInNo());
        return result;
    }

    // ------------------------------------------------------------------

    private String callModel(String userPrompt)
    {
        List<Message> messages = List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt));
        String sceneModel = quotaGuard.sceneModel("selection");
        Prompt prompt = sceneModel == null ? new Prompt(messages)
                : new Prompt(messages, ChatOptions.builder().model(sceneModel).build());
        long started = System.currentTimeMillis();
        String model = "";
        int promptTokens = 0;
        int completionTokens = 0;
        try
        {
            ChatResponse response = chatModel.call(prompt);
            aiCircuitBreaker.onSuccess(AiGuard.AiChannel.CHAT);
            if (response.getMetadata() != null)
            {
                model = response.getMetadata().getModel() == null ? "" : response.getMetadata().getModel();
                Usage usage = response.getMetadata().getUsage();
                if (usage != null)
                {
                    promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
                    completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
                }
            }
            callLogRecorder.record(CallLogEntry.builder()
                    .scene(SCENE_SELECTION).modelName(model).promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .latencyMs((int) (System.currentTimeMillis() - started)).success(true).build());
            return response.getResult() == null || response.getResult().getOutput() == null
                    ? "" : response.getResult().getOutput().getText();
        }
        catch (RuntimeException e)
        {
            aiCircuitBreaker.onFailure(AiGuard.AiChannel.CHAT);
            callLogRecorder.record(CallLogEntry.builder()
                    .scene(SCENE_SELECTION).modelName(model).promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .latencyMs((int) (System.currentTimeMillis() - started)).success(false)
                    .errorMsg(e.getMessage()).build());
            throw new AiUnavailableException("选品建议生成失败：" + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> parseSuggestions(String content, Map<Long, Map<String, Object>> metricByBook,
            double seasonFactor, int size)
    {
        List<Map<String, Object>> items = new ArrayList<>();
        if (!StringUtils.hasText(content))
        {
            return items;
        }
        String text = content.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start)
        {
            int objectStart = text.indexOf('{');
            if (objectStart >= 0)
            {
                try
                {
                    JsonNode root = objectMapper.readTree(text.substring(objectStart));
                    JsonNode array = root.path("items");
                    if (array.isArray())
                    {
                        for (JsonNode node : array)
                        {
                            addParsedItem(items, node, metricByBook, seasonFactor);
                        }
                    }
                }
                catch (Exception ignored)
                {
                    return items;
                }
            }
            return items;
        }
        try
        {
            JsonNode array = objectMapper.readTree(text.substring(start, end + 1));
            if (array.isArray())
            {
                for (JsonNode node : array)
                {
                    if (items.size() >= size)
                    {
                        break;
                    }
                    addParsedItem(items, node, metricByBook, seasonFactor);
                }
            }
        }
        catch (Exception ignored)
        {
            return new ArrayList<>();
        }
        return items;
    }

    private void addParsedItem(List<Map<String, Object>> items, JsonNode node,
            Map<Long, Map<String, Object>> metricByBook, double seasonFactor)
    {
        long bookId = node.path("bookId").asLong(0);
        Map<String, Object> metric = metricByBook.get(bookId);
        if (metric == null)
        {
            return;
        }
        int suggestType = node.path("suggestType").asInt(0);
        if (suggestType != 1 && suggestType != 2)
        {
            return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("bookId", bookId);
        item.put("isbn", str(metric.get("isbn")));
        item.put("title", str(metric.get("title")));
        item.put("suggestType", suggestType);
        item.put("suggestQty", Math.max(0, node.path("suggestQty").asInt(0)));
        item.put("suggestReason", node.path("suggestReason").asText(""));
        item.put("seasonFactor", node.path("seasonFactor").asDouble(seasonFactor));
        item.put("confidence", node.path("confidence").asDouble(0.6D));
        item.put("referenceData", metric);
        items.add(item);
    }

    private List<Map<String, Object>> ruleSuggestions(List<Map<String, Object>> metrics, int size, double seasonFactor)
    {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> metric : metrics)
        {
            long bookId = asLong(metric.get("bookId"));
            long stock = asLong(metric.get("stock"));
            long heat = asLong(metric.get("saleCount")) + asLong(metric.get("borrow90")) * 2;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookId", bookId);
            item.put("isbn", str(metric.get("isbn")));
            item.put("title", str(metric.get("title")));
            if (stock < 10 && heat > 0)
            {
                item.put("suggestType", 1);
                item.put("suggestQty", Math.max(5, 20 - (int) stock));
                item.put("suggestReason", "库存偏低且近 90 天有动销");
            }
            else if (stock >= 30 && asLong(metric.get("borrow90")) == 0)
            {
                item.put("suggestType", 2);
                item.put("suggestQty", 0);
                item.put("suggestReason", "库存偏高且近 90 天无借阅");
            }
            else
            {
                continue;
            }
            item.put("seasonFactor", seasonFactor);
            item.put("confidence", 0.5D);
            item.put("referenceData", metric);
            items.add(item);
            if (items.size() >= size)
            {
                break;
            }
        }
        if (items.isEmpty() && !metrics.isEmpty())
        {
            Map<String, Object> metric = metrics.get(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookId", asLong(metric.get("bookId")));
            item.put("isbn", str(metric.get("isbn")));
            item.put("title", str(metric.get("title")));
            item.put("suggestType", 1);
            item.put("suggestQty", 10);
            item.put("suggestReason", "按热度与库存的规则兜底建议");
            item.put("seasonFactor", seasonFactor);
            item.put("confidence", 0.4D);
            item.put("referenceData", metric);
            items.add(item);
        }
        return items;
    }

    private String buildMetrics(List<Map<String, Object>> metrics, double seasonFactor)
    {
        StringBuilder text = new StringBuilder();
        text.append("当前季节因子：").append(seasonFactor).append('\n');
        for (Map<String, Object> row : metrics)
        {
            text.append("bookId=").append(asLong(row.get("bookId")))
                    .append(" 书名=").append(str(row.get("title")))
                    .append(" ISBN=").append(str(row.get("isbn")))
                    .append(" 可售库存=").append(asLong(row.get("stock")))
                    .append(" 累计销量=").append(asLong(row.get("saleCount")))
                    .append(" 近90天借阅=").append(asLong(row.get("borrow90")))
                    .append('\n');
        }
        return text.toString();
    }

    private Location defaultLocation()
    {
        Location location = locationService.getOne(new LambdaQueryWrapper<Location>()
                .eq(Location::getStatus, "0")
                .orderByAsc(Location::getLocationId)
                .last("limit 1"), false);
        if (location == null)
        {
            throw BizErrors.ruleViolated("请先维护库位");
        }
        return location;
    }

    private double seasonFactor()
    {
        int month = LocalDate.now().getMonthValue();
        if (month == 2 || month == 8 || month == 9)
        {
            return 1.2D;
        }
        if (month == 7)
        {
            return 1.1D;
        }
        return 1.0D;
    }

    private long asLong(Object value)
    {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String str(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }
}
