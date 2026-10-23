package com.tbgbook.bookstore.analysis.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tbgbook.bookstore.common.exception.BizErrors;

/**
 * 自定义报表的维度/指标白名单与列映射。
 */
public final class CustomReportSupport
{
    public static final List<String> DIMENSIONS = List.of(
            "date", "category", "member_level", "channel", "cashier");

    public static final List<String> METRICS = List.of(
            "sale_amount", "order_count", "gross_profit", "discount_total", "refund_amount", "avg_order");

    private static final Map<String, String> METRIC_LABELS = Map.of(
            "sale_amount", "销售额",
            "order_count", "订单数",
            "gross_profit", "毛利",
            "discount_total", "优惠合计",
            "refund_amount", "退款金额",
            "avg_order", "客单价");

    private CustomReportSupport()
    {
    }

    public static void validate(List<String> dimensions, List<String> metrics, boolean costVisible)
    {
        if (dimensions == null || dimensions.isEmpty() || dimensions.size() != 1)
        {
            throw BizErrors.ruleViolated("当前版本自定义报表仅支持单维度查询");
        }
        if (!DIMENSIONS.contains(dimensions.get(0)))
        {
            throw BizErrors.ruleViolated("维度超出允许范围");
        }
        if (metrics == null || metrics.isEmpty())
        {
            throw BizErrors.ruleViolated("指标不能为空");
        }
        for (String metric : metrics)
        {
            if (!METRICS.contains(metric))
            {
                throw BizErrors.ruleViolated("指标超出允许范围：" + metric);
            }
        }
        if (metrics.contains("gross_profit") && !costVisible)
        {
            throw BizErrors.ruleViolated("含成本指标但无查看权限");
        }
    }

    public static List<Map<String, Object>> columns(List<String> metrics)
    {
        List<Map<String, Object>> columns = new ArrayList<>();
        Map<String, Object> dimension = new LinkedHashMap<>();
        dimension.put("key", "dimension");
        dimension.put("label", "维度");
        columns.add(dimension);
        for (String metric : metrics)
        {
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("key", metric);
            column.put("label", METRIC_LABELS.getOrDefault(metric, metric));
            columns.add(column);
        }
        return columns;
    }

    public static List<Object> row(Map<String, Object> raw, List<String> metrics)
    {
        List<Object> row = new ArrayList<>();
        row.add(raw.get("dimensionName") == null ? "" : raw.get("dimensionName"));
        for (String metric : metrics)
        {
            row.add(metricValue(raw, metric));
        }
        return row;
    }

    public static Object metricValue(Map<String, Object> raw, String metric)
    {
        if ("avg_order".equals(metric))
        {
            BigDecimal orders = decimal(raw.get("orderCount"));
            return orders.signum() == 0 ? BigDecimal.ZERO
                    : decimal(raw.get("saleAmount")).divide(orders, 2, RoundingMode.HALF_UP);
        }
        return switch (metric)
        {
            case "sale_amount" -> decimal(raw.get("saleAmount"));
            case "order_count" -> decimal(raw.get("orderCount"));
            case "gross_profit" -> decimal(raw.get("grossProfit"));
            case "discount_total" -> decimal(raw.get("discountTotal"));
            case "refund_amount" -> decimal(raw.get("refundAmount"));
            default -> BigDecimal.ZERO;
        };
    }

    public static String metricLabel(String metric)
    {
        return METRIC_LABELS.getOrDefault(metric, metric);
    }

    public static BigDecimal decimal(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
