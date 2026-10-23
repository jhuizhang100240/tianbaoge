package com.tbgbook.bookstore.analysis.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.analysis.domain.BizTodo;
import com.tbgbook.bookstore.analysis.mapper.AnalysisQueryMapper;
import com.tbgbook.bookstore.analysis.service.IDashboardService;
import com.tbgbook.bookstore.analysis.service.IMetricDailyService;
import com.tbgbook.bookstore.analysis.service.ITodoService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 工作台 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService
{
    private static final long STORE_ID = 1L;

    private static final String COST_PERM = "dashboard:cost:view";

    private static final int DEFAULT_REFRESH_SECONDS = 30;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IMetricDailyService metricDailyService;

    private final ITodoService todoService;

    private final AnalysisQueryMapper analysisQueryMapper;

    private final ISysConfigService configService;

    @Override
    public Map<String, Object> overview(LocalDate date, boolean costVisible)
    {
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<String, BigDecimal> today = metricDailyService.ensureAndRead(day);
        Map<String, BigDecimal> yesterday = metricDailyService.ensureAndRead(day.minusDays(1));
        boolean showCost = costVisible && SecurityUtils.hasPermi(COST_PERM);

        Map<String, Object> todayBlock = new LinkedHashMap<>();
        put(todayBlock, "saleAmount", today, "sale_amount");
        put(todayBlock, "orderCount", today, "order_count");
        put(todayBlock, "guestCount", today, "guest_count");
        put(todayBlock, "avgOrder", today, "avg_order");
        put(todayBlock, "memberRatio", today, "member_ratio");
        put(todayBlock, "discountTotal", today, "discount_total");
        put(todayBlock, "refundAmount", today, "refund_amount");
        if (showCost)
        {
            put(todayBlock, "grossProfit", today, "gross_profit");
        }

        Map<String, Object> compare = new LinkedHashMap<>();
        compare.put("saleAmount", change(today, yesterday, "sale_amount"));
        compare.put("orderCount", change(today, yesterday, "order_count"));
        compare.put("avgOrder", change(today, yesterday, "avg_order"));
        compare.put("memberRatio", change(today, yesterday, "member_ratio"));
        if (showCost)
        {
            compare.put("grossProfit", change(today, yesterday, "gross_profit"));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", day.format(DATE_FMT));
        data.put("today", todayBlock);
        data.put("compare", compare);
        data.put("todoCount", todoService.count(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getStoreId, STORE_ID).eq(BizTodo::getStatus, 1)));
        data.put("metricNotes", Map.of(
                "saleAmount", "已支付/待取货/已完成订单实付合计（红冲负数抵扣）",
                "guestCount", "按成交订单笔数计（单店无进店计数设备）",
                "memberRatio", "会员订单数 ÷ 成交订单数 × 100"));
        return data;
    }

    @Override
    public Map<String, Object> trend(String granularity, LocalDate startDate, LocalDate endDate, String compareType)
    {
        String gran = granularity == null || granularity.isBlank() ? "day" : granularity;
        if (!List.of("day", "week", "month", "term").contains(gran))
        {
            throw BizErrors.badRequest("粒度取值 day/week/month/term");
        }
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(29) : startDate;
        if (start.isAfter(end))
        {
            throw BizErrors.badRequest("开始日期不能晚于结束日期");
        }
        if (end.toEpochDay() - start.toEpochDay() > 366)
        {
            throw BizErrors.ruleViolated("趋势查询区间不能超过 366 天");
        }
        metricDailyService.ensureRange(start, end);
        Map<LocalDate, SeriesPoint> series = loadSeries(start, end);
        Map<String, Object> data = aggregate(series, gran);
        List<BigDecimal> compareSeries = new ArrayList<>();
        if (compareType != null && !compareType.isBlank())
        {
            if (!List.of("yoy", "mom").contains(compareType))
            {
                throw BizErrors.badRequest("对比方式取值 yoy 同比 / mom 环比");
            }
            LocalDate shiftedStart = "yoy".equals(compareType) ? start.minusYears(1) : start.minusDays(1);
            LocalDate shiftedEnd = "yoy".equals(compareType) ? end.minusYears(1) : end.minusDays(1);
            metricDailyService.ensureRange(shiftedStart, shiftedEnd);
            Map<String, Object> shifted = aggregate(loadSeries(shiftedStart, shiftedEnd), gran);
            List<?> shiftedSale = (List<?>) shifted.get("saleAmounts");
            List<?> currentSale = (List<?>) data.get("saleAmounts");
            for (int i = 0; i < currentSale.size(); i++)
            {
                compareSeries.add(i < shiftedSale.size()
                        ? BigDecimal.valueOf(((Number) shiftedSale.get(i)).doubleValue())
                        : BigDecimal.ZERO);
            }
        }
        data.put("compareSeries", compareSeries);
        data.put("termLabels", "term".equals(gran) ? data.get("dates") : List.of());
        return data;
    }

    @Override
    public Map<String, Object> screenData()
    {
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> metrics = metricDailyService.ensureAndRead(today);
        Map<String, Object> realtime = new LinkedHashMap<>();
        put(realtime, "saleAmount", metrics, "sale_amount");
        put(realtime, "orderCount", metrics, "order_count");
        put(realtime, "guestCount", metrics, "guest_count");
        put(realtime, "avgOrder", metrics, "avg_order");
        put(realtime, "memberRatio", metrics, "member_ratio");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("realtime", realtime);
        data.put("rank", analysisQueryMapper.topBooks(today, 5));
        data.put("categoryShare", analysisQueryMapper.categoryShare(today));
        Map<String, Object> memberBlock = new LinkedHashMap<>(analysisQueryMapper.screenMemberBlock(today));
        memberBlock.put("memberRatio", metrics.getOrDefault("member_ratio", BigDecimal.ZERO));
        data.put("memberBlock", memberBlock);
        data.put("refreshSeconds", intConfig("biz.dashboard.refresh.seconds", DEFAULT_REFRESH_SECONDS));
        data.put("generatedAt", LocalDateTime.now());
        return data;
    }

    private Map<LocalDate, SeriesPoint> loadSeries(LocalDate start, LocalDate end)
    {
        Map<LocalDate, SeriesPoint> series = new LinkedHashMap<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end))
        {
            series.put(cursor, new SeriesPoint());
            cursor = cursor.plusDays(1);
        }
        for (Map<String, Object> row : analysisQueryMapper.metricRows(start, end))
        {
            LocalDate day = LocalDate.parse(String.valueOf(row.get("metric_date")));
            SeriesPoint point = series.get(day);
            if (point == null)
            {
                continue;
            }
            String code = String.valueOf(row.get("metric_code"));
            BigDecimal value = new BigDecimal(String.valueOf(row.get("metric_value")));
            if ("sale_amount".equals(code))
            {
                point.sale = value;
            }
            else if ("order_count".equals(code))
            {
                point.orders = value;
            }
        }
        return series;
    }

    private Map<String, Object> aggregate(Map<LocalDate, SeriesPoint> series, String granularity)
    {
        Map<String, SeriesPoint> buckets = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, SeriesPoint> entry : series.entrySet())
        {
            LocalDate day = entry.getKey();
            String label;
            if ("week".equals(granularity))
            {
                LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                label = monday.get(IsoFields.WEEK_BASED_YEAR) + "-W"
                        + String.format("%02d", monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            }
            else if ("month".equals(granularity))
            {
                label = day.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            }
            else if ("term".equals(granularity))
            {
                int month = day.getMonthValue();
                boolean spring = month >= 2 && month <= 7;
                label = day.getYear() + (spring ? " 春季" : " 秋季");
                if (month == 1)
                {
                    label = (day.getYear() - 1) + " 秋季";
                }
            }
            else
            {
                label = day.format(DATE_FMT);
            }
            SeriesPoint bucket = buckets.computeIfAbsent(label, key -> new SeriesPoint());
            bucket.sale = bucket.sale.add(entry.getValue().sale);
            bucket.orders = bucket.orders.add(entry.getValue().orders);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dates", new ArrayList<>(buckets.keySet()));
        data.put("saleAmounts", buckets.values().stream().map(p -> p.sale).toList());
        data.put("orderCounts", buckets.values().stream().map(p -> p.orders).toList());
        data.put("guestCounts", buckets.values().stream().map(p -> p.orders).toList());
        return data;
    }

    private BigDecimal change(Map<String, BigDecimal> today, Map<String, BigDecimal> yesterday, String code)
    {
        BigDecimal base = yesterday.getOrDefault(code, BigDecimal.ZERO);
        if (base.signum() == 0)
        {
            return null;
        }
        BigDecimal current = today.getOrDefault(code, BigDecimal.ZERO);
        return current.subtract(base).multiply(BigDecimal.valueOf(100))
                .divide(base.abs(), 2, RoundingMode.HALF_UP);
    }

    private void put(Map<String, Object> target, String key, Map<String, BigDecimal> values, String code)
    {
        target.put(key, values.getOrDefault(code, BigDecimal.ZERO));
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

    private static final class SeriesPoint
    {
        private BigDecimal sale = BigDecimal.ZERO;

        private BigDecimal orders = BigDecimal.ZERO;
    }
}
