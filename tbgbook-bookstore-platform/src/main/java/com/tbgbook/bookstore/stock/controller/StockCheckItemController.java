package com.tbgbook.bookstore.stock.controller;

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

import com.tbgbook.bookstore.stock.domain.StockCheckItem;
import com.tbgbook.bookstore.stock.service.IStockCheckItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 库存盘点明细 Controller（A-21 录入页的明细表格）。
 *
 * <p>权限串复用 {@code inventory:stockcheck:*}，不为明细另设前缀（契约如此）。
 *
 * <p>⚠️ **写入前会校验所属盘点单处于「进行中」**，由 Service 统一把关 ——
 * 只靠单据侧的校验挡不住「直接对明细发 PUT」这条路径。
 *
 * <p>⚠️ 盲盘（{@code showBookQty=0}）遮蔽的是**详情与报告**接口的账面数字段；
 * 本控制器是明细的 CRUD，账面数在写入时由服务端快照，读出时按单据状态遮蔽
 * （走 {@code GET /inventory/stockcheck/{checkId}} 更稳妥）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/stockcheck/item")
public class StockCheckItemController extends BaseController
{
    private final IStockCheckItemService stockCheckItemService;

    /**
     * 查询盘点明细列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockCheckItem item)
    {
        startPage();
        List<StockCheckItem> list = stockCheckItemService.selectStockCheckItemList(item);
        return getDataTable(list);
    }

    /**
     * 获取盘点明细详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(stockCheckItemService.selectStockCheckItemById(itemId));
    }

    /**
     * 新增盘点明细（账面数由服务端快照，派生列服务端算）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:add')")
    @Log(title = "盘点明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody StockCheckItem item)
    {
        return toAjax(stockCheckItemService.addStockCheckItem(item));
    }

    /**
     * 修改盘点明细（主键在请求体，不在路径 —— RuoYi 式非严格 REST）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:edit')")
    @Log(title = "盘点明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody StockCheckItem item)
    {
        return toAjax(stockCheckItemService.updateStockCheckItem(item));
    }

    /**
     * 删除盘点明细（支持批量，主键逗号分隔；**物理删除**，本表无 del_flag）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:remove')")
    @Log(title = "盘点明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(stockCheckItemService.removeStockCheckItems(itemIds));
    }
}
