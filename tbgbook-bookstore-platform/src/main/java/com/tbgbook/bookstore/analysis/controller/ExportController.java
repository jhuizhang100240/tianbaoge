package com.tbgbook.bookstore.analysis.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.analysis.domain.ExportTask;
import com.tbgbook.bookstore.analysis.domain.request.CreateExportRequest;
import com.tbgbook.bookstore.analysis.domain.request.CustomReportRequest;
import com.tbgbook.bookstore.analysis.domain.request.ExportAuthorizeRequest;
import com.tbgbook.bookstore.analysis.service.ICustomReportService;
import com.tbgbook.bookstore.analysis.service.IExportTaskService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 自定义报表与异步导出 Controller（A-70）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/analysis")
public class ExportController extends BaseController
{
    private final ICustomReportService customReportService;

    private final IExportTaskService exportTaskService;

    @PreAuthorize("@ss.hasPermi('analysis:report:custom')")
    @Log(title = "自定义报表", businessType = BusinessType.OTHER)
    @PostMapping("/report/custom")
    public AjaxResult custom(@Validated @RequestBody CustomReportRequest request)
    {
        return success(customReportService.run(request));
    }

    @PreAuthorize("@ss.hasPermi('analysis:report:export')")
    @Log(title = "异步导出", businessType = BusinessType.INSERT)
    @PostMapping("/export")
    public AjaxResult create(@Validated @RequestBody CreateExportRequest request)
    {
        return success(exportTaskService.create(request));
    }

    @PreAuthorize("@ss.hasPermi('analysis:report:list')")
    @GetMapping("/export/list")
    public TableDataInfo list(ExportTask query)
    {
        startPage();
        return getDataTable(exportTaskService.selectExportList(query));
    }

    @PreAuthorize("@ss.hasPermi('analysis:report:list')")
    @GetMapping("/export/{taskId}")
    public AjaxResult detail(@PathVariable("taskId") Long taskId)
    {
        ExportTask task = exportTaskService.getTask(taskId);
        task.setFileUrl(null);
        return success(task);
    }

    @PreAuthorize("@ss.hasPermi('analysis:report:authorize')")
    @Log(title = "导出二次授权", businessType = BusinessType.GRANT)
    @PostMapping("/export/{taskId}/authorize")
    public AjaxResult authorize(@PathVariable("taskId") Long taskId,
            @Validated @RequestBody ExportAuthorizeRequest request)
    {
        return success(exportTaskService.authorize(taskId, request.getAuthReason()));
    }

    @PreAuthorize("@ss.hasPermi('analysis:report:list')")
    @GetMapping("/export/{taskId}/download")
    public ResponseEntity<Resource> download(@PathVariable("taskId") Long taskId)
    {
        ExportTask task = exportTaskService.getTask(taskId);
        Resource resource = exportTaskService.download(taskId);
        String name = task.getTaskName() == null ? "export" : task.getTaskName();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".xlsx";
        String disposition = ContentDisposition.attachment()
                .filename(name, StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }
}
