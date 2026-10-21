package com.tbgbook.bookstore.analysis.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.analysis.domain.MetricDaily;
import com.tbgbook.bookstore.analysis.service.IMetricDailyService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 数据分析 Controller。Task 2 先落指标只读接口，后续任务追加分析端点。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/analysis")
public class AnalysisController extends BaseController
{
    private final IMetricDailyService metricDailyService;

    @PreAuthorize("@ss.hasPermi('analysis:report:list')")
    @GetMapping("/metric/list")
    public TableDataInfo metricList(MetricDaily query)
    {
        startPage();
        return getDataTable(metricDailyService.selectMetricList(query));
    }

    @PreAuthorize("@ss.hasPermi('analysis:report:list')")
    @GetMapping("/metric/{metricId}")
    public AjaxResult metricInfo(@PathVariable("metricId") Long metricId)
    {
        return success(metricDailyService.getMetric(metricId));
    }
}
