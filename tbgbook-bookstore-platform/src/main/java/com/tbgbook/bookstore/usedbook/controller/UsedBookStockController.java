package com.tbgbook.bookstore.usedbook.controller;

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

import com.tbgbook.bookstore.usedbook.domain.OffShelfRequest;
import com.tbgbook.bookstore.usedbook.domain.ShelfRequest;
import com.tbgbook.bookstore.usedbook.domain.UsedBook;
import com.tbgbook.bookstore.usedbook.service.IUsedBookStockService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 二手书库存 Controller（页面 A-42，FR-4.2.3）。
 *
 * <p>二手书按册管理、独立库存池与独立 SKU（§3.2 库存规则 5、§4.2 规则 6）。
 * 行由回收验收自动生成；{@code POST} 仅用于存量手工建档补录。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/usedbook/stock")
public class UsedBookStockController extends BaseController
{
    private final IUsedBookStockService usedBookStockService;

    /**
     * 查询二手书库存列表（可按条码 / SKU / ISBN / 书名 / 成色 / 状态 / 来源回收单 / 图书与时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:stock:list')")
    @GetMapping("/list")
    public TableDataInfo list(UsedBook usedBook)
    {
        startPage();
        List<UsedBook> list = usedBookStockService.selectUsedBookList(usedBook);
        return getDataTable(list);
    }

    /**
     * 获取二手书库存详情。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:stock:list')")
    @GetMapping("/{usedBookId}")
    public AjaxResult getInfo(@PathVariable("usedBookId") Long usedBookId)
    {
        return success(usedBookStockService.selectUsedBookById(usedBookId));
    }

    /**
     * 手工补录二手书副本（存量建档；落库为未上架，须走上架动作）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:stock:add')")
    @Log(title = "二手书库存", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody UsedBook usedBook)
    {
        return toAjax(usedBookStockService.addUsedBook(usedBook));
    }

    /**
     * 修改二手书基础信息（库存状态与时间戳由动作端点维护）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:stock:edit')")
    @Log(title = "二手书库存", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody UsedBook usedBook)
    {
        return toAjax(usedBookStockService.updateUsedBook(usedBook));
    }

    /**
     * 删除二手书副本（仅未上架 / 已下架且无订单引用的可删）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:stock:remove')")
    @Log(title = "二手书库存", businessType = BusinessType.DELETE)
    @DeleteMapping("/{usedBookIds}")
    public AjaxResult remove(@PathVariable Long[] usedBookIds)
    {
        return toAjax(usedBookStockService.removeUsedBooks(usedBookIds));
    }

    /**
     * 上架定价（校验售价不高于原价）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:stock:shelf')")
    @Log(title = "二手书上架", businessType = BusinessType.UPDATE)
    @PostMapping("/{usedBookId}/shelf")
    public AjaxResult shelf(@PathVariable("usedBookId") Long usedBookId,
            @RequestBody ShelfRequest request)
    {
        return success(usedBookStockService.shelf(usedBookId, request));
    }

    /**
     * 下架（已售出的副本不可下架）。
     */
    @PreAuthorize("@ss.hasPermi('usedbook:stock:offShelf')")
    @Log(title = "二手书下架", businessType = BusinessType.UPDATE)
    @PostMapping("/{usedBookId}/off-shelf")
    public AjaxResult offShelf(@PathVariable("usedBookId") Long usedBookId,
            @RequestBody OffShelfRequest request)
    {
        return toAjax(usedBookStockService.offShelf(usedBookId, request));
    }
}
