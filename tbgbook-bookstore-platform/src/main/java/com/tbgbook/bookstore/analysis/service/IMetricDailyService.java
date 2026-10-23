package com.tbgbook.bookstore.analysis.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.analysis.domain.MetricDaily;

/**
 * 看板指标日预计算 Service。
 */
public interface IMetricDailyService extends IService<MetricDaily>
{
    /**
     * 读取某日指标；缺失时用订单事实表即时补算并 upsert。
     */
    Map<String, BigDecimal> ensureAndRead(LocalDate date);

    /**
     * 批量补齐区间内缺失日（≤90 天）。
     */
    void ensureRange(LocalDate start, LocalDate end);

    List<MetricDaily> selectMetricList(MetricDaily query);

    MetricDaily getMetric(Long metricId);
}
