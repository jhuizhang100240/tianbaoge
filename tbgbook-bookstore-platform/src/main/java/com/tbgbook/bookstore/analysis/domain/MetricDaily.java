package com.tbgbook.bookstore.analysis.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 看板指标日预计算（NFR-P2 约束 3）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_metric_daily")
public class MetricDaily
{
    @TableId(value = "metric_id", type = IdType.AUTO)
    private Long metricId;

    private Long storeId;

    private LocalDate metricDate;

    private String metricCode;

    private String metricName;

    private BigDecimal metricValue;

    private String dimType;

    private String dimValue;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private LocalDate beginDate;

    @TableField(exist = false)
    private LocalDate endDate;
}
