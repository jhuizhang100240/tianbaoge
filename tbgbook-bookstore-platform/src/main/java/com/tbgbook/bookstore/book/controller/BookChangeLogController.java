package com.tbgbook.bookstore.book.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.book.domain.BookChangeLog;
import com.tbgbook.bookstore.book.service.IBookChangeLogService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 图书信息变更日志 Controller（页面 A-08 图书详情 · 变更日志页签）。
 *
 * <p><b>只读资源，没有写端点</b>（接口文档只列 2 个端点）。变更日志是审计证据，
 * 不经接口修改 —— 写入路径唯一：{@code BookServiceImpl#writeChangeLogs}。
 *
 * @author tbgbook
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/book/change-log")
public class BookChangeLogController extends BaseController
{
    private final IBookChangeLogService bookChangeLogService;

    /**
     * 查询图书信息变更日志列表。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/list")
    public TableDataInfo list(BookChangeLog log)
    {
        startPage();
        List<BookChangeLog> list = bookChangeLogService.selectBookChangeLogList(log);
        return getDataTable(list);
    }

    /**
     * 获取变更日志详情。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(bookChangeLogService.getById(logId));
    }
}
