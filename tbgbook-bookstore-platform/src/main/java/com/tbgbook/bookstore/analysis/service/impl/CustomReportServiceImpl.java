package com.tbgbook.bookstore.analysis.service.impl;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tbgbook.bookstore.analysis.domain.request.CustomReportRequest;
import com.tbgbook.bookstore.analysis.mapper.AnalysisExportMapper;
import com.tbgbook.bookstore.analysis.service.ICustomReportService;
import com.tbgbook.bookstore.analysis.service.IExportTaskService;
import com.tbgbook.bookstore.analysis.support.CustomReportSupport;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 自定义报表 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class CustomReportServiceImpl implements ICustomReportService
{
    private static final int SYNC_ROW_LIMIT = 5000;

    private static final int MAX_RANGE_DAYS = 366;

    private final AnalysisExportMapper analysisExportMapper;

    private final IExportTaskService exportTaskService;

    @Override
    public Map<String, Object> run(CustomReportRequest request)
    {
        boolean costVisible = SecurityUtils.hasPermi("dashboard:cost:view");
        CustomReportSupport.validate(request.getDimensions(), request.getMetrics(), costVisible);
        if (request.getFilters() != null && !request.getFilters().isEmpty())
        {
            throw BizErrors.ruleViolated("附加筛选暂未支持（当前版本仅支持时间窗）");
        }
        if (request.getEndDate().isBefore(request.getStartDate())
                || ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1 > MAX_RANGE_DAYS)
        {
            throw BizErrors.ruleViolated("查询区间不能超过 366 天");
        }
        String dimension = request.getDimensions().get(0);
        List<Map<String, Object>> rows = analysisExportMapper.customReportRows(
                request.getStartDate(), request.getEndDate(), dimension, SYNC_ROW_LIMIT + 1);
        if (Boolean.FALSE.equals(request.getSync()) || rows.size() > SYNC_ROW_LIMIT)
        {
            return exportTaskService.createCustom(request, rows.size());
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("columns", CustomReportSupport.columns(request.getMetrics()));
        data.put("rows", rows.stream().map(row -> CustomReportSupport.row(row, request.getMetrics())).toList());
        return data;
    }
}
