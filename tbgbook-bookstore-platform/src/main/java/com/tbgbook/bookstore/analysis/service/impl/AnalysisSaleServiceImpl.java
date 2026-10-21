package com.tbgbook.bookstore.analysis.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tbgbook.bookstore.analysis.mapper.AnalysisQueryMapper;
import com.tbgbook.bookstore.analysis.service.IAnalysisSaleService;
import com.tbgbook.bookstore.analysis.service.IDashboardService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 销售分析与商品排行 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AnalysisSaleServiceImpl implements IAnalysisSaleService
{
    private static final String COST_PERM = "dashboard:cost:view";

    private static final int DEFAULT_RANK_LIMIT = 20;

    private static final int MAX_RANGE_DAYS = 30;

    private final AnalysisQueryMapper analysisQueryMapper;

    private final IDashboardService dashboardService;

    private final ISysConfigService configService;

    @Override
    public Map<String, Object> saleSummary(LocalDate startDate, LocalDate endDate, String dimension)
    {
        requireRange(startDate, endDate);
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_RANGE_DAYS)
        {
            throw BizErrors.ruleViolated("查询区间超过 30 天，请改走异步导出");
        }
        boolean costVisible = SecurityUtils.hasPermi(COST_PERM);
        Map<String, Object> raw = analysisQueryMapper.saleSummary(startDate, endDate);
        Map<String, Object> summary = buildSummary(raw, costVisible);

        List<Map<String, Object>> items = new ArrayList<>();
        if (dimension != null && !dimension.isBlank())
        {
            if (!List.of("category", "member_level", "channel", "cashier").contains(dimension))
            {
                throw BizErrors.badRequest("维度取值 category/member_level/channel/cashier");
            }
            items = analysisQueryMapper.saleByDimension(startDate, endDate, dimension);
            if (!costVisible)
            {
                items.forEach(item -> item.put("grossProfit", null));
            }
        }
        Map<String, Object> compare = compareSummary(startDate, endDate, costVisible);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("items", items);
        data.put("compare", compare);
        return data;
    }

    @Override
    public Map<String, Object> saleTrend(String granularity)
    {
        return dashboardService.trend(granularity, null, null, null);
    }

    @Override
    public Map<String, Object> bookRank(String rankType, LocalDate startDate, LocalDate endDate, Integer limit)
    {
        if (rankType == null || !List.of("sale", "slow", "profit", "category").contains(rankType))
        {
            throw BizErrors.badRequest("榜单类型取值 sale/slow/profit/category");
        }
        int size = limit == null ? DEFAULT_RANK_LIMIT : Math.max(1, Math.min(limit, 100));
        boolean costVisible = SecurityUtils.hasPermi(COST_PERM);
        if ("profit".equals(rankType) && !costVisible)
        {
            throw BizErrors.forbidden("请求毛利榜需要成本查看权限");
        }
        if ("slow".equals(rankType))
        {
            int days = intConfig("biz.stock.slowmoving.days", 60);
            List<Map<String, Object>> items = analysisQueryMapper.bookRankSlow(
                    LocalDateTime.now().minusDays(days), size);
            return rankData(items, costVisible);
        }
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(29) : startDate;
        requireRange(start, end);
        List<Map<String, Object>> items = "category".equals(rankType)
                ? analysisQueryMapper.categoryShareRange(start, end)
                : analysisQueryMapper.bookRankSale(start, end, size, "profit".equals(rankType));
        return rankData(items, costVisible);
    }

    private Map<String, Object> rankData(List<Map<String, Object>> items, boolean costVisible)
    {
        if (!costVisible)
        {
            items.forEach(item -> item.put("grossProfit", null));
        }
        BigDecimal saleTotal = BigDecimal.ZERO;
        for (Map<String, Object> item : items)
        {
            Object value = item.get("saleAmount");
            if (value != null)
            {
                saleTotal = saleTotal.add(new BigDecimal(String.valueOf(value)));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("saleAmountTotal", saleTotal);
        return data;
    }

    private Map<String, Object> buildSummary(Map<String, Object> raw, boolean costVisible)
    {
        BigDecimal sale = decimal(raw == null ? null : raw.get("saleAmount"));
        BigDecimal orders = decimal(raw == null ? null : raw.get("orderCount"));
        BigDecimal memberOrders = decimal(raw == null ? null : raw.get("memberOrderCount"));
        BigDecimal avg = orders.signum() == 0 ? BigDecimal.ZERO
                : sale.divide(orders, 2, RoundingMode.HALF_UP);
        BigDecimal ratio = orders.signum() == 0 ? BigDecimal.ZERO
                : memberOrders.multiply(BigDecimal.valueOf(100)).divide(orders, 2, RoundingMode.HALF_UP);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("saleAmount", sale);
        summary.put("orderCount", orders);
        summary.put("avgOrder", avg);
        summary.put("discountTotal", decimal(raw == null ? null : raw.get("discountTotal")));
        summary.put("refundAmount", decimal(raw == null ? null : raw.get("refundAmount")));
        summary.put("memberRatio", ratio);
        summary.put("grossProfit", costVisible ? decimal(raw == null ? null : raw.get("grossProfit")) : null);
        if (costVisible && sale.signum() != 0)
        {
            summary.put("margin", decimal(raw == null ? null : raw.get("grossProfit"))
                    .multiply(BigDecimal.valueOf(100)).divide(sale, 2, RoundingMode.HALF_UP));
        }
        else
        {
            summary.put("margin", null);
        }
        return summary;
    }

    private Map<String, Object> compareSummary(LocalDate start, LocalDate end, boolean costVisible)
    {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Map<String, Object> current = analysisQueryMapper.saleSummary(start, end);
        Map<String, Object> previous = analysisQueryMapper.saleSummary(
                start.minusDays(days), start.minusDays(1));
        Map<String, Object> compare = new LinkedHashMap<>();
        compare.put("saleAmount", percentChange(previous == null ? null : previous.get("saleAmount"),
                current == null ? null : current.get("saleAmount")));
        if (costVisible)
        {
            compare.put("grossProfit", percentChange(
                    previous == null ? null : previous.get("grossProfit"),
                    current == null ? null : current.get("grossProfit")));
        }
        return compare;
    }

    private BigDecimal percentChange(Object beforeValue, Object afterValue)
    {
        BigDecimal before = decimal(beforeValue);
        if (before.signum() == 0)
        {
            return null;
        }
        return decimal(afterValue).subtract(before).multiply(BigDecimal.valueOf(100))
                .divide(before.abs(), 2, RoundingMode.HALF_UP);
    }

    private void requireRange(LocalDate start, LocalDate end)
    {
        if (start == null || end == null)
        {
            throw BizErrors.badRequest("开始日期与结束日期不能为空");
        }
        if (end.isBefore(start))
        {
            throw BizErrors.badRequest("结束日期不能早于开始日期");
        }
    }

    private BigDecimal decimal(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (raw == null || raw.isBlank())
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
}
