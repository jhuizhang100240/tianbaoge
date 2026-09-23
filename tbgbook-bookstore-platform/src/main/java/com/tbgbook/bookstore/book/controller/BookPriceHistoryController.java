package com.tbgbook.bookstore.book.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.book.domain.BookPriceHistory;
import com.tbgbook.bookstore.book.service.IBookPriceHistoryService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 图书价格版本历史 Controller（页面 A-08 图书详情 · 价格历史页签）。
 *
 * <p><b>只读资源，因此没有 POST / PUT / DELETE 端点</b>（接口文档只列 2 个端点）。
 * 也**不加 {@code @Log}** —— 读操作不进操作日志，否则日志会被查询淹没（后端 CLAUDE.md §六）。
 *
 * <p>权限串沿用 {@code book:book:list}（接口文档如此），不为历史单开前缀。
 *
 * @author tbgbook
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/book/price-history")
public class BookPriceHistoryController extends BaseController
{
    private final IBookPriceHistoryService bookPriceHistoryService;

    /**
     * 查询图书价格版本历史列表。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/list")
    public TableDataInfo list(BookPriceHistory history)
    {
        startPage();
        List<BookPriceHistory> list = bookPriceHistoryService.selectBookPriceHistoryList(history);
        return getDataTable(list);
    }

    /**
     * 获取价格版本历史详情。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/{historyId}")
    public AjaxResult getInfo(@PathVariable("historyId") Long historyId)
    {
        return success(bookPriceHistoryService.getById(historyId));
    }
}
