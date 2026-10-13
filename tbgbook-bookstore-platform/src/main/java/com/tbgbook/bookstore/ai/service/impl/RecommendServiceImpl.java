package com.tbgbook.bookstore.ai.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.core.AiGuard;
import com.tbgbook.bookstore.ai.domain.RecommendConfig;
import com.tbgbook.bookstore.ai.domain.RecommendItem;
import com.tbgbook.bookstore.ai.domain.RecommendPin;
import com.tbgbook.bookstore.ai.mapper.AiStatsMapper;
import com.tbgbook.bookstore.ai.mapper.RecommendConfigMapper;
import com.tbgbook.bookstore.ai.mapper.RecommendPinMapper;
import com.tbgbook.bookstore.ai.service.IRecommendService;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.service.IBookService;
import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * 推荐位 Service 实现。
 *
 * <p>算法通道 = 统计候选（近 90 天借阅 + 销量热度）与人工通道按权重混合；
 * preview 不调模型（可解释、无远端依赖），模型重排属每日重算任务（阶段十）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl extends ServiceImpl<RecommendConfigMapper, RecommendConfig>
        implements IRecommendService
{
    private static final long STORE_ID = 1L;

    private final RecommendPinMapper recommendPinMapper;

    private final AiStatsMapper aiStatsMapper;

    private final IBookService bookService;

    private final AiGuard aiGuard;

    @Override
    public List<RecommendConfig> selectRecommendConfigList(RecommendConfig query)
    {
        LambdaQueryWrapper<RecommendConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecommendConfig::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.eq(StringUtils.hasText(query.getPositionCode()),
                    RecommendConfig::getPositionCode, query.getPositionCode());
            wrapper.like(StringUtils.hasText(query.getPositionName()),
                    RecommendConfig::getPositionName, query.getPositionName());
            wrapper.eq(query.getStrategy() != null, RecommendConfig::getStrategy, query.getStrategy());
            wrapper.eq(StringUtils.hasText(query.getStatus()), RecommendConfig::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(RecommendConfig::getConfigId);
        return list(wrapper);
    }

    @Override
    public int addRecommendConfig(RecommendConfig config)
    {
        if (config == null || !StringUtils.hasText(config.getPositionCode())
                || !StringUtils.hasText(config.getPositionName()))
        {
            throw BizErrors.badRequest("推荐位编码与名称不能为空");
        }
        config.setConfigId(null);
        config.setStoreId(STORE_ID);
        normalize(config, null);
        return save(config) ? 1 : 0;
    }

    @Override
    public int updateRecommendConfig(RecommendConfig config)
    {
        if (config == null || config.getConfigId() == null)
        {
            throw BizErrors.badRequest("配置ID不能为空");
        }
        RecommendConfig existing = getById(config.getConfigId());
        if (existing == null)
        {
            throw BizErrors.notFound("推荐位配置", config.getConfigId());
        }
        normalize(config, existing);
        config.setStoreId(null);
        return updateById(config) ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeRecommendConfigs(Long[] configIds)
    {
        if (configIds == null || configIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的配置");
        }
        int count = 0;
        for (Long configId : configIds)
        {
            if (getById(configId) == null)
            {
                throw BizErrors.notFound("推荐位配置", configId);
            }
            recommendPinMapper.delete(new LambdaQueryWrapper<RecommendPin>()
                    .eq(RecommendPin::getConfigId, configId));
            removeById(configId);
            count++;
        }
        return count;
    }

    @Override
    public List<RecommendPin> selectRecommendPinList(RecommendPin query)
    {
        LambdaQueryWrapper<RecommendPin> wrapper = new LambdaQueryWrapper<>();
        if (query != null)
        {
            wrapper.eq(query.getPinId() != null, RecommendPin::getPinId, query.getPinId());
            wrapper.eq(query.getConfigId() != null, RecommendPin::getConfigId, query.getConfigId());
            wrapper.eq(query.getBookId() != null, RecommendPin::getBookId, query.getBookId());
        }
        wrapper.orderByAsc(RecommendPin::getConfigId).orderByAsc(RecommendPin::getPinOrder);
        return recommendPinMapper.selectList(wrapper);
    }

    @Override
    public int addRecommendPin(RecommendPin pin)
    {
        validatePinExists(pin, true);
        long exists = recommendPinMapper.selectCount(new LambdaQueryWrapper<RecommendPin>()
                .eq(RecommendPin::getConfigId, pin.getConfigId())
                .eq(RecommendPin::getBookId, pin.getBookId()));
        if (exists > 0)
        {
            throw BizErrors.conflict("该推荐位已置顶此书");
        }
        pin.setPinId(null);
        if (pin.getPinOrder() == null)
        {
            RecommendPin last = recommendPinMapper.selectOne(new LambdaQueryWrapper<RecommendPin>()
                    .eq(RecommendPin::getConfigId, pin.getConfigId())
                    .orderByDesc(RecommendPin::getPinOrder)
                    .last("limit 1"));
            pin.setPinOrder(last == null || last.getPinOrder() == null ? 1 : last.getPinOrder() + 1);
        }
        return recommendPinMapper.insert(pin);
    }

    @Override
    public int updateRecommendPin(RecommendPin pin)
    {
        if (pin == null || pin.getPinId() == null)
        {
            throw BizErrors.badRequest("置顶ID不能为空");
        }
        if (recommendPinMapper.selectById(pin.getPinId()) == null)
        {
            throw BizErrors.notFound("置顶记录", pin.getPinId());
        }
        pin.setConfigId(null);
        pin.setBookId(null);
        pin.setCreateBy(null);
        return recommendPinMapper.updateById(pin);
    }

    @Override
    public int removeRecommendPins(Long[] pinIds)
    {
        if (pinIds == null || pinIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的置顶记录");
        }
        int count = 0;
        for (Long pinId : pinIds)
        {
            if (recommendPinMapper.selectById(pinId) == null)
            {
                throw BizErrors.notFound("置顶记录", pinId);
            }
            recommendPinMapper.deleteById(pinId);
            count++;
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> replacePins(Long configId, List<RecommendPin> pins)
    {
        RecommendConfig config = getById(configId);
        if (config == null)
        {
            throw BizErrors.notFound("推荐位配置", configId);
        }
        List<RecommendPin> target = pins == null ? new ArrayList<>() : pins;
        Set<Integer> orders = new HashSet<>();
        List<String> problemBooks = new ArrayList<>();
        int index = 0;
        for (RecommendPin pin : target)
        {
            if (pin.getBookId() == null)
            {
                throw BizErrors.badRequest("置顶图书ID不能为空");
            }
            int order = pin.getPinOrder() == null ? index + 1 : pin.getPinOrder();
            if (!orders.add(order))
            {
                throw BizErrors.ruleViolated("置顶顺序重复：" + order);
            }
            pin.setPinOrder(order);
            Book book = bookService.getById(pin.getBookId());
            if (book == null || !isOnSale(book))
            {
                problemBooks.add(book == null ? String.valueOf(pin.getBookId()) : book.getTitle());
            }
            index++;
        }
        if (!problemBooks.isEmpty())
        {
            throw BizErrors.ruleViolated("包含已下架或停售的图书：" + String.join("、", problemBooks));
        }

        recommendPinMapper.delete(new LambdaQueryWrapper<RecommendPin>()
                .eq(RecommendPin::getConfigId, configId));
        for (RecommendPin pin : target)
        {
            pin.setPinId(null);
            pin.setConfigId(configId);
            recommendPinMapper.insert(pin);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configId", configId);
        data.put("pinCount", target.size());
        data.put("items", preview(configId).get("items"));
        return data;
    }

    @Override
    public Map<String, Object> preview(Long configId)
    {
        RecommendConfig config = getById(configId);
        if (config == null)
        {
            throw BizErrors.notFound("推荐位配置", configId);
        }
        int display = config.getDisplayQty() == null || config.getDisplayQty() <= 0
                ? 12 : config.getDisplayQty();
        boolean aiEnabled = aiGuard.enabled();
        String fallbackReason = null;

        List<RecommendItem> manualItems = manualItems(configId);
        List<RecommendItem> algoItems = new ArrayList<>();
        if (!aiEnabled)
        {
            fallbackReason = "AI 已停用，按兜底策略：" + fallbackName(config.getFallbackStrategy());
            if (Integer.valueOf(2).equals(config.getFallbackStrategy()))
            {
                algoItems = newBookItems(display);
            }
            else if (!Integer.valueOf(3).equals(config.getFallbackStrategy()))
            {
                algoItems = hotItems(display);
            }
        }
        else
        {
            algoItems = hotItems(display);
        }

        int algoTarget;
        int manualTarget;
        if (!aiEnabled)
        {
            algoTarget = display;
            manualTarget = 0;
        }
        else if (Integer.valueOf(1).equals(config.getStrategy()))
        {
            algoTarget = display;
            manualTarget = 0;
        }
        else if (Integer.valueOf(2).equals(config.getStrategy()))
        {
            algoTarget = 0;
            manualTarget = display;
        }
        else
        {
            manualTarget = Math.round(display * (config.getManualWeight() == null ? 0 : config.getManualWeight()) / 100F);
            algoTarget = display - manualTarget;
        }

        List<RecommendItem> items = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        append(items, seen, manualItems, manualTarget);
        append(items, seen, algoItems, display - items.size());
        append(items, seen, manualItems, display - items.size());
        append(items, seen, algoItems, display - items.size());
        if (items.size() > display)
        {
            items = new ArrayList<>(items.subList(0, display));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configId", configId);
        data.put("fallbackUsed", !aiEnabled);
        if (fallbackReason != null)
        {
            data.put("fallbackReason", fallbackReason);
        }
        data.put("items", items);
        return data;
    }

    // ------------------------------------------------------------------

    private void normalize(RecommendConfig config, RecommendConfig existing)
    {
        int strategy = config.getStrategy() != null ? config.getStrategy()
                : (existing == null ? 3 : existing.getStrategy());
        if (strategy < 1 || strategy > 3)
        {
            throw BizErrors.badRequest("推荐策略取值 1个性化算法 / 2人工指定 / 3混合");
        }
        int algo = config.getAlgoWeight() != null ? config.getAlgoWeight()
                : (existing == null ? 70 : existing.getAlgoWeight());
        int manual = config.getManualWeight() != null ? config.getManualWeight()
                : (existing == null ? 30 : existing.getManualWeight());
        if (strategy == 1)
        {
            algo = 100;
            manual = 0;
        }
        else if (strategy == 2)
        {
            algo = 0;
            manual = 100;
        }
        else if (algo + manual != 100)
        {
            throw BizErrors.ruleViolated("算法权重与人工权重之和必须为 100");
        }
        int fallback = config.getFallbackStrategy() != null ? config.getFallbackStrategy()
                : (existing == null ? 1 : existing.getFallbackStrategy());
        if (fallback < 1 || fallback > 3)
        {
            throw BizErrors.badRequest("降级兜底策略取值 1热销榜 / 2新书上架 / 3关闭推荐位");
        }
        int display = config.getDisplayQty() != null ? config.getDisplayQty()
                : (existing == null ? 12 : existing.getDisplayQty());
        if (display <= 0)
        {
            throw BizErrors.ruleViolated("展示数量必须大于 0");
        }
        if (config.getStatus() != null && !"0".equals(config.getStatus()) && !"1".equals(config.getStatus()))
        {
            throw BizErrors.badRequest("状态取值 0正常 / 1停用");
        }
        config.setStrategy(strategy);
        config.setAlgoWeight(algo);
        config.setManualWeight(manual);
        config.setFallbackStrategy(fallback);
        config.setDisplayQty(display);
        config.setPoolScope(config.getPoolScope() == null ? "" : config.getPoolScope());
        config.setUpdateFrequency(config.getUpdateFrequency() == null ? "" : config.getUpdateFrequency());
        config.setStatus(config.getStatus() == null ? "0" : config.getStatus());
    }

    private void validatePinExists(RecommendPin pin, boolean requireBook)
    {
        if (pin == null || pin.getConfigId() == null || (requireBook && pin.getBookId() == null))
        {
            throw BizErrors.badRequest("推荐位与图书ID不能为空");
        }
        if (getById(pin.getConfigId()) == null)
        {
            throw BizErrors.notFound("推荐位配置", pin.getConfigId());
        }
        if (requireBook)
        {
            Book book = bookService.getById(pin.getBookId());
            if (book == null)
            {
                throw BizErrors.notFound("图书", pin.getBookId());
            }
            if (!isOnSale(book))
            {
                throw BizErrors.ruleViolated("已下架或停售的图书不能置顶");
            }
        }
    }

    private boolean isOnSale(Book book)
    {
        return Integer.valueOf(1).equals(book.getStatus())
                && (book.getPublishStatus() == null || Integer.valueOf(1).equals(book.getPublishStatus()));
    }

    private List<RecommendItem> manualItems(Long configId)
    {
        List<RecommendPin> pins = recommendPinMapper.selectList(new LambdaQueryWrapper<RecommendPin>()
                .eq(RecommendPin::getConfigId, configId)
                .orderByAsc(RecommendPin::getPinOrder));
        List<RecommendItem> items = new ArrayList<>();
        for (RecommendPin pin : pins)
        {
            Book book = bookService.getById(pin.getBookId());
            if (book != null && isOnSale(book))
            {
                items.add(new RecommendItem(book.getBookId(), book.getTitle(), book.getCoverUrl(),
                        "人工", 1.0D, "运营置顶"));
            }
        }
        return items;
    }

    private List<RecommendItem> hotItems(int limit)
    {
        List<Map<String, Object>> rows = aiStatsMapper.selectHotBooks(Math.max(limit, 1));
        double maxHeat = 1D;
        for (Map<String, Object> row : rows)
        {
            maxHeat = Math.max(maxHeat, asDouble(row.get("heat")));
        }
        List<RecommendItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            double score = Math.round(asDouble(row.get("heat")) / maxHeat * 10000D) / 10000D;
            items.add(new RecommendItem(asLong(row.get("bookId")), str(row.get("title")),
                    str(row.get("coverUrl")), "算法", score, "热销与借阅热度"));
        }
        return items;
    }

    private List<RecommendItem> newBookItems(int limit)
    {
        List<Map<String, Object>> rows = aiStatsMapper.selectNewBooks(Math.max(limit, 1));
        List<RecommendItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            items.add(new RecommendItem(asLong(row.get("bookId")), str(row.get("title")),
                    str(row.get("coverUrl")), "算法", 0.8D, "新书上架"));
        }
        return items;
    }

    private void append(List<RecommendItem> target, Set<Long> seen, List<RecommendItem> source, int slots)
    {
        if (slots <= 0)
        {
            return;
        }
        int added = 0;
        for (RecommendItem item : source)
        {
            if (added >= slots)
            {
                break;
            }
            if (item.getBookId() != null && seen.add(item.getBookId()))
            {
                target.add(item);
                added++;
            }
        }
    }

    private String fallbackName(Integer fallbackStrategy)
    {
        if (Integer.valueOf(2).equals(fallbackStrategy))
        {
            return "新书上架";
        }
        if (Integer.valueOf(3).equals(fallbackStrategy))
        {
            return "关闭推荐位";
        }
        return "热销榜";
    }

    private long asLong(Object value)
    {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private double asDouble(Object value)
    {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    private String str(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }
}
