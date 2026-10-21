package com.tbgbook.bookstore.analysis.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.analysis.domain.BizTodo;
import com.tbgbook.bookstore.analysis.domain.request.TodoReadAllRequest;
import com.tbgbook.bookstore.analysis.domain.request.TodoStatusRequest;
import com.tbgbook.bookstore.analysis.service.ITodoService;
import com.tbgbook.bookstore.analysis.service.IDashboardService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 工作台 Controller（A-03/A-04/A-05）。待办中心先落地，驾驶舱与大屏在 Task 2 追加。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/dashboard")
public class DashboardController extends BaseController
{
    private final ITodoService todoService;

    private final IDashboardService dashboardService;

    @PreAuthorize("@ss.hasPermi('dashboard:cockpit:list')")
    @GetMapping("/cockpit/overview")
    public AjaxResult overview(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "costVisible", required = false, defaultValue = "false") Boolean costVisible)
    {
        return success(dashboardService.overview(date, Boolean.TRUE.equals(costVisible)));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:cockpit:list')")
    @GetMapping("/cockpit/trend")
    public AjaxResult trend(
            @RequestParam(value = "granularity", required = false, defaultValue = "day") String granularity,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "compareType", required = false) String compareType)
    {
        return success(dashboardService.trend(granularity, startDate, endDate, compareType));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:screen:list')")
    @GetMapping("/screen/data")
    public AjaxResult screenData()
    {
        return success(dashboardService.screenData());
    }

    @PreAuthorize("@ss.hasPermi('dashboard:todo:list')")
    @GetMapping("/todo/list")
    public TableDataInfo todoList(BizTodo query)
    {
        startPage();
        return getDataTable(todoService.selectTodoList(query));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:todo:list')")
    @GetMapping("/todo/{todoId}")
    public AjaxResult todoInfo(@PathVariable("todoId") Long todoId)
    {
        return success(todoService.getTodo(todoId));
    }

    @PreAuthorize("@ss.hasPermi('dashboard:todo:list')")
    @Log(title = "待办中心", businessType = BusinessType.UPDATE)
    @PutMapping("/todo/status")
    public AjaxResult updateTodoStatus(@Validated @RequestBody TodoStatusRequest request)
    {
        todoService.updateStatus(request.getTodoId(), request.getStatus(), request.getRemark());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('dashboard:todo:list')")
    @Log(title = "待办中心", businessType = BusinessType.UPDATE)
    @PostMapping("/todo/read-all")
    public AjaxResult readAll(@RequestBody(required = false) TodoReadAllRequest request)
    {
        return success(todoService.readAll(request == null ? null : request.getTodoType()));
    }
}
