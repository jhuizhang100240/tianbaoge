package com.tbgbook.bookstore.analysis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.analysis.domain.MetricDaily;

/**
 * 看板指标日预计算 Mapper。
 */
public interface MetricDailyMapper extends BaseMapper<MetricDaily>
{
    /**
     * 按唯一键 (store_id, metric_date, metric_code, dim_type, dim_value) 幂等写入。
     */
    int upsert(MetricDaily metric);
}
