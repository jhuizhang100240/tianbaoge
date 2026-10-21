package com.tbgbook.bookstore.analysis.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.analysis.domain.BizTodo;
import com.tbgbook.bookstore.analysis.domain.request.TodoReadAllRequest;
import com.tbgbook.bookstore.analysis.domain.request.TodoStatusRequest;
import com.tbgbook.bookstore.analysis.service.ITodoService;
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
