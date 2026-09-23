package com.tbgbook.bookstore.book.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.book.domain.BookTextbook;
import com.tbgbook.bookstore.book.service.IBookTextbookService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 教材扩展信息 Controller（页面 A-07 的「教材扩展字段」区）。
 *
 * <p>权限串沿用图书主档的 {@code book:book:*} —— 教材扩展是图书的一部分，
 * 不为它单开权限前缀（接口文档「教材扩展信息」权限前缀即 {@code book:book}）。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：删掉 export 端点（文档里只有 5 个端点）、
 * 补校验注解、删除走带校验的 Service 方法。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/book/textbook")
public class BookTextbookController extends BaseController
{
    private final IBookTextbookService bookTextbookService;

    /**
     * 查询教材扩展信息列表。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/list")
    public TableDataInfo list(BookTextbook textbook)
    {
        startPage();
        List<BookTextbook> list = bookTextbookService.selectBookTextbookList(textbook);
        return getDataTable(list);
    }

    /**
     * 获取教材扩展信息详情。
     *
     * <p>路径参数是 **bookId**（主键即外键），不是单独的 textbookId。
     */
    @PreAuthorize("@ss.hasPermi('book:book:list')")
    @GetMapping("/{bookId}")
    public AjaxResult getInfo(@PathVariable("bookId") Long bookId)
    {
        return success(bookTextbookService.getById(bookId));
    }

    /**
     * 新增教材扩展信息。
     *
     * <p>只有教材类图书（{@code bookType=2}）能建档；重复建档返回 409。
     */
    @PreAuthorize("@ss.hasPermi('book:book:add')")
    @Log(title = "教材扩展信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BookTextbook textbook)
    {
        return toAjax(bookTextbookService.addBookTextbook(textbook));
    }

    /**
     * 修改教材扩展信息（主键在请求体的 {@code bookId}，不在路径）。
     */
    @PreAuthorize("@ss.hasPermi('book:book:edit')")
    @Log(title = "教材扩展信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BookTextbook textbook)
    {
        return toAjax(bookTextbookService.updateBookTextbook(textbook));
    }

    /**
     * 删除教材扩展信息。
     *
     * <p>本表无 {@code del_flag}，删除为物理删除 —— 它是图书的扩展属性。
     */
    @PreAuthorize("@ss.hasPermi('book:book:remove')")
    @Log(title = "教材扩展信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{bookIds}")
    public AjaxResult remove(@PathVariable Long[] bookIds)
    {
        return toAjax(bookTextbookService.removeBookTextbooks(bookIds));
    }
}
