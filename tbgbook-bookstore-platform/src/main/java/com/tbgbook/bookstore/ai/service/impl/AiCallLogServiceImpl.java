package com.tbgbook.bookstore.ai.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.domain.AiCallLog;
import com.tbgbook.bookstore.ai.mapper.AiCallLogMapper;
import com.tbgbook.bookstore.ai.service.IAiCallLogService;

import lombok.RequiredArgsConstructor;

/**
 * AI 调用明细 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AiCallLogServiceImpl extends ServiceImpl<AiCallLogMapper, AiCallLog>
        implements IAiCallLogService
{
    @Override
    public List<AiCallLog> selectAiCallLogList(AiCallLog query)
    {
        LambdaQueryWrapper<AiCallLog> wrapper = new LambdaQueryWrapper<>();
        if (query != null)
        {
            wrapper.eq(query.getSessionId() != null, AiCallLog::getSessionId, query.getSessionId());
            wrapper.eq(query.getMemberId() != null, AiCallLog::getMemberId, query.getMemberId());
            wrapper.eq(query.getScene() != null, AiCallLog::getScene, query.getScene());
            wrapper.eq(StringUtils.hasText(query.getModelName()), AiCallLog::getModelName, query.getModelName());
            wrapper.eq(query.getCacheHit() != null, AiCallLog::getCacheHit, query.getCacheHit());
            wrapper.eq(query.getIsSuccess() != null, AiCallLog::getIsSuccess, query.getIsSuccess());
        }
        wrapper.orderByDesc(AiCallLog::getLogId);
        return list(wrapper);
    }

    @Override
    public Map<String, Object> costSummary(LocalDate start, LocalDate end, String granularity, Integer scene)
    {
        String format = "month".equalsIgnoreCase(granularity) ? "%Y-%m"
                : ("week".equalsIgnoreCase(granularity) ? "%x-%v" : "%Y-%m-%d");
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();
        List<Map<String, Object>> rows = baseMapper.selectCostSummary(startTime, endTime, format, scene);
        Map<String, Object> cacheStats = baseMapper.selectCacheHitStats(startTime, endTime, scene);

        long callCount = 0;
        long totalTokens = 0;
        long latencySum = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        Map<String, Map<String, Object>> trendMap = new LinkedHashMap<>();
        Map<Integer, Map<String, Object>> sceneMap = new LinkedHashMap<>();
        Map<String, Map<String, Object>> modelMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows)
        {
            long rowCalls = asLong(row.get("callCount"));
            long rowTokens = asLong(row.get("totalTokens"));
            BigDecimal rowCost = asDecimal(row.get("totalCost"));
            long rowLatency = asLong(row.get("avgLatencyMs")) * rowCalls;
            callCount += rowCalls;
            totalTokens += rowTokens;
            latencySum += rowLatency;
            totalCost = totalCost.add(rowCost);

            String bucket = String.valueOf(row.get("bucket"));
            accumulate(trendMap, bucket, rowCalls, rowTokens, rowCost, null);
            Integer rowScene = row.get("scene") == null ? null : (int) asLong(row.get("scene"));
            accumulateScene(sceneMap, rowScene, rowCalls, rowTokens, rowCost);
            String model = row.get("modelName") == null ? "" : String.valueOf(row.get("modelName"));
            accumulate(modelMap, model, rowCalls, rowTokens, rowCost, null);
        }

        long cacheHits = cacheStats == null ? 0 : asLong(cacheStats.get("cacheHits"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("callCount", callCount);
        summary.put("totalTokens", totalTokens);
        summary.put("totalCost", totalCost.setScale(4, RoundingMode.HALF_UP));
        summary.put("avgLatencyMs", callCount == 0 ? 0 : latencySum / callCount);
        summary.put("cacheHitRate", callCount == 0 ? 0D
                : Math.round(cacheHits * 10000D / callCount) / 10000D);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("trend", new ArrayList<>(trendMap.values()));
        result.put("byScene", new ArrayList<>(sceneMap.values()));
        result.put("byModel", new ArrayList<>(modelMap.values()));
        return result;
    }

    private void accumulate(Map<String, Map<String, Object>> target, String key, long calls,
            long tokens, BigDecimal cost, Integer scene)
    {
        Map<String, Object> item = target.computeIfAbsent(key, k -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("bucket", k);
            created.put("callCount", 0L);
            created.put("totalTokens", 0L);
            created.put("totalCost", BigDecimal.ZERO);
            return created;
        });
        item.put("callCount", asLong(item.get("callCount")) + calls);
        item.put("totalTokens", asLong(item.get("totalTokens")) + tokens);
        item.put("totalCost", asDecimal(item.get("totalCost")).add(cost));
    }

    private void accumulateScene(Map<Integer, Map<String, Object>> target, Integer scene, long calls,
            long tokens, BigDecimal cost)
    {
        Map<String, Object> item = target.computeIfAbsent(scene == null ? -1 : scene, k -> {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("scene", scene);
            created.put("callCount", 0L);
            created.put("totalTokens", 0L);
            created.put("totalCost", BigDecimal.ZERO);
            return created;
        });
        item.put("callCount", asLong(item.get("callCount")) + calls);
        item.put("totalTokens", asLong(item.get("totalTokens")) + tokens);
        item.put("totalCost", asDecimal(item.get("totalCost")).add(cost));
    }

    private long asLong(Object value)
    {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private BigDecimal asDecimal(Object value)
    {
        if (value instanceof BigDecimal)
        {
            return (BigDecimal) value;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
