package com.tbgbook.bookstore.analysis.service;

import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;

import com.tbgbook.bookstore.analysis.domain.ExportTask;
import com.tbgbook.bookstore.analysis.domain.request.CreateExportRequest;
import com.tbgbook.bookstore.analysis.domain.request.CustomReportRequest;

/**
 * 异步导出任务 Service（A-70）。
 */
public interface IExportTaskService
{
    ExportTask create(CreateExportRequest request);

    ExportTask createForMember(Long memberId, CreateExportRequest request);

    Map<String, Object> createCustom(CustomReportRequest request, long estimatedRows);

    List<ExportTask> selectExportList(ExportTask query);

    ExportTask getTask(Long taskId);

    ExportTask authorize(Long taskId, String reason);

    Resource download(Long taskId);
}
