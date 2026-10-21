package com.tbgbook.bookstore.analysis.domain.request;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 自定义报表请求。
 */
@Data
public class CustomReportRequest
{
    @NotEmpty(message = "维度不能为空")
    private List<String> dimensions;

    @NotEmpty(message = "指标不能为空")
    private List<String> metrics;

    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Map<String, Object> filters;

    private Boolean sync;
}
