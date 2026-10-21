package com.tbgbook.bookstore.analysis.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.analysis.domain.MetricDaily;
import com.tbgbook.bookstore.analysis.service.IAnalysisSaleService;
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

    private final IAnalysisSaleService analysisSaleService;

    @PreAuthorize("@ss.hasPermi('analysis:sale:list')")
    @GetMapping("/sale/summary")
    public AjaxResult saleSummary(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "dimension", required = false) String dimension)
    {
        return success(analysisSaleService.saleSummary(startDate, endDate, dimension));
    }

    @PreAuthorize("@ss.hasPermi('analysis:sale:list')")
    @GetMapping("/sale/trend")
    public AjaxResult saleTrend(
            @RequestParam(value = "granularity", required = false, defaultValue = "day") String granularity)
    {
        return success(analysisSaleService.saleTrend(granularity));
    }

    @PreAuthorize("@ss.hasPermi('analysis:book:list')")
    @GetMapping("/book/rank")
    public AjaxResult bookRank(
            @RequestParam("rankType") String rankType,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "limit", required = false) Integer limit)
    {
        return success(analysisSaleService.bookRank(rankType, startDate, endDate, limit));
    }

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
