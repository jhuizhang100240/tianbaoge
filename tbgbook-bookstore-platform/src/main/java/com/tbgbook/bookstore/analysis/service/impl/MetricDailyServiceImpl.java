package com.tbgbook.bookstore.analysis.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.analysis.domain.MetricDaily;
import com.tbgbook.bookstore.analysis.mapper.AnalysisQueryMapper;
import com.tbgbook.bookstore.analysis.mapper.MetricDailyMapper;
import com.tbgbook.bookstore.analysis.service.IMetricDailyService;
import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * 看板指标日预计算 Service 实现。
 *
 * <p>指标口径固定为：销售额=已支付/待取货/已完成订单实付合计（红冲单负数自然抵扣）；
 * 订单数=同口径订单笔数；客流=成交订单数（单店无进店计数设备）；客单价=销售额÷订单数；
 * 会员占比=会员订单数÷订单数×100；毛利=Σ(行分摊实付−成本快照×数量)。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MetricDailyServiceImpl extends ServiceImpl<MetricDailyMapper, MetricDaily>
        implements IMetricDailyService
{
    private static final long STORE_ID = 1L;

    private static final List<String> METRIC_CODES = List.of(
            "sale_amount", "order_count", "guest_count", "avg_order",
            "member_ratio", "gross_profit", "discount_total", "refund_amount");

    private static final Map<String, String> METRIC_NAMES = Map.of(
            "sale_amount", "销售额",
            "order_count", "订单数",
            "guest_count", "客流",
            "avg_order", "客单价",
            "member_ratio", "会员占比",
            "gross_profit", "毛利",
            "discount_total", "优惠合计",
            "refund_amount", "退款金额");

    private final AnalysisQueryMapper analysisQueryMapper;

    @Override
    public Map<String, BigDecimal> ensureAndRead(LocalDate date)
    {
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<String, BigDecimal> values = readDay(day);
        if (!values.keySet().containsAll(METRIC_CODES))
        {
            computeDay(day);
            values = readDay(day);
        }
        return values;
    }

    @Override
    public void ensureRange(LocalDate start, LocalDate end)
    {
        if (start == null || end == null || end.isBefore(start))
        {
            return;
        }
        long days = end.toEpochDay() - start.toEpochDay() + 1;
        if (days > 90)
        {
            return;
        }
        // 以 biz_metric_daily 是否已有完整 8 个指标为准，而不是看 biz_order 有无订单。
        // 否则「当天没有订单」会被反复当成缺失日重算，趋势接口 P95 会被拖到数秒。
        java.util.Set<LocalDate> complete = new java.util.HashSet<>(
                analysisQueryMapper.metricCompleteDates(start, end));
        LocalDate cursor = start;
        while (!cursor.isAfter(end))
        {
            if (!complete.contains(cursor))
            {
                computeDay(cursor);
            }
            cursor = cursor.plusDays(1);
        }
    }

    @Override
    public List<MetricDaily> selectMetricList(MetricDaily query)
    {
        LambdaQueryWrapper<MetricDaily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricDaily::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.eq(query.getMetricDate() != null, MetricDaily::getMetricDate, query.getMetricDate());
            wrapper.eq(query.getMetricCode() != null && !query.getMetricCode().isEmpty(),
                    MetricDaily::getMetricCode, query.getMetricCode());
            wrapper.eq(query.getDimType() != null && !query.getDimType().isEmpty(),
                    MetricDaily::getDimType, query.getDimType());
            wrapper.eq(query.getDimValue() != null && !query.getDimValue().isEmpty(),
                    MetricDaily::getDimValue, query.getDimValue());
            wrapper.ge(query.getBeginDate() != null, MetricDaily::getMetricDate, query.getBeginDate());
            wrapper.le(query.getEndDate() != null, MetricDaily::getMetricDate, query.getEndDate());
        }
        wrapper.orderByDesc(MetricDaily::getMetricDate).orderByAsc(MetricDaily::getMetricId);
        return list(wrapper);
    }

    @Override
    public MetricDaily getMetric(Long metricId)
    {
        MetricDaily metric = getById(metricId);
        if (metric == null)
        {
            throw BizErrors.notFound("指标", metricId);
        }
        return metric;
    }

    private Map<String, BigDecimal> readDay(LocalDate day)
    {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (MetricDaily metric : list(new LambdaQueryWrapper<MetricDaily>()
                .eq(MetricDaily::getStoreId, STORE_ID)
                .eq(MetricDaily::getMetricDate, day)
                .eq(MetricDaily::getDimType, "")
                .eq(MetricDaily::getDimValue, "")))
        {
            values.put(metric.getMetricCode(), metric.getMetricValue());
        }
        return values;
    }

    private void computeDay(LocalDate day)
    {
        Map<String, Object> order = analysisQueryMapper.dayOrderMetrics(day);
        Map<String, Object> profit = analysisQueryMapper.dayProfitMetrics(day);
        BigDecimal sale = decimal(order == null ? null : order.get("sale_amount"));
        BigDecimal count = decimal(order == null ? null : order.get("order_count"));
        BigDecimal memberOrders = decimal(order == null ? null : order.get("member_order_count"));
        BigDecimal avg = count.signum() == 0 ? BigDecimal.ZERO
                : sale.divide(count, 2, RoundingMode.HALF_UP);
        BigDecimal ratio = count.signum() == 0 ? BigDecimal.ZERO
                : memberOrders.multiply(BigDecimal.valueOf(100)).divide(count, 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("sale_amount", sale);
        values.put("order_count", count);
        values.put("guest_count", count);
        values.put("avg_order", avg);
        values.put("member_ratio", ratio);
        values.put("gross_profit", decimal(profit == null ? null : profit.get("gross_profit")));
        values.put("discount_total", decimal(order == null ? null : order.get("discount_total")));
        values.put("refund_amount", decimal(order == null ? null : order.get("refund_amount")));

        for (Map.Entry<String, BigDecimal> entry : values.entrySet())
        {
            MetricDaily metric = new MetricDaily();
            metric.setStoreId(STORE_ID);
            metric.setMetricDate(day);
            metric.setMetricCode(entry.getKey());
            metric.setMetricName(METRIC_NAMES.getOrDefault(entry.getKey(), entry.getKey()));
            metric.setMetricValue(entry.getValue());
            metric.setDimType("");
            metric.setDimValue("");
            metric.setCreateTime(LocalDateTime.now());
            baseMapper.upsert(metric);
        }
    }

    private BigDecimal decimal(Object value)
    {
        if (value == null)
        {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal)
        {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate toDate(Object value)
    {
        if (value instanceof LocalDate date)
        {
            return date;
        }
        return LocalDate.parse(String.valueOf(value));
    }
}
