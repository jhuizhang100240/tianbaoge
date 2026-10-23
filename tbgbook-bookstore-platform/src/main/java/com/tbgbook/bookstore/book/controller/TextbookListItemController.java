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

import com.tbgbook.bookstore.book.domain.TextbookListItem;
import com.tbgbook.bookstore.book.service.ITextbookListItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 教材清单明细 Controller（页面 A-13 清单详情主体表格）。
 *
 * <p>路径挂在主资源下（{@code /book/textbook/item}），不另开顶级路径 ——
 * 明细跟随主资源，见 {@code docs/api-gen/spec_resources.py} 里 {@code kind="child"} 的约定。
 *
 * <p>权限串沿用主资源的 {@code book:textbook:*}（接口文档「教材清单明细」权限前缀即
 * {@code book:textbook}），不为明细单开前缀。
 *
 * <p>⚠️ 与主资源 Controller 的路径不冲突：主资源的 {@code /{listId}} 是 3 段，
 * 本控制器的映射都是 4 段（{@code /item/list}、{@code /item/{itemId}}）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/textbook/item")
public class TextbookListItemController extends BaseController
{
    private final ITextbookListItemService textbookListItemService;

    /**
     * 查询教材清单明细列表。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:list')")
    @GetMapping("/list")
    public TableDataInfo list(TextbookListItem textbookListItem)
    {
        startPage();
        List<TextbookListItem> list = textbookListItemService.selectTextbookListItemList(textbookListItem);
        return getDataTable(list);
    }

    /**
     * 获取教材清单明细详情。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(textbookListItemService.selectTextbookListItemById(itemId));
    }

    /**
     * 新增教材清单明细。
     *
     * <p>{@code isbn} / {@code title} / {@code price} 由服务端按 {@code bookId} 回填；
     * {@code pickedQty} / {@code remainQty} / {@code status} 由服务端算出（传了也不算）。
     * 同一清单内重复添加同一本书返回 409。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:add')")
    @Log(title = "教材清单明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody TextbookListItem textbookListItem)
    {
        return toAjax(textbookListItemService.addTextbookListItem(textbookListItem));
    }

    /**
     * 修改教材清单明细（主键在请求体的 {@code itemId}，不在路径）。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:edit')")
    @Log(title = "教材清单明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody TextbookListItem textbookListItem)
    {
        return toAjax(textbookListItemService.updateTextbookListItem(textbookListItem));
    }

    /**
     * 删除教材清单明细。
     *
     * <p>本表无 {@code del_flag}，物理删除；删后重算主表统计列。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:remove')")
    @Log(title = "教材清单明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(textbookListItemService.removeTextbookListItems(itemIds));
    }
}
