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

import com.tbgbook.bookstore.stock.domain.StockTransferItem;
import com.tbgbook.bookstore.stock.service.IStockTransferItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 库存调拨明细 Controller（A-26 调拨开单页的明细表格）。
 *
 * <p>明细要能独立增删改，是因为调拨单在前端是「头 + 可编辑明细表格」——
 * 每加一行都回传整张单太重，且并发编辑下容易互相覆盖。
 *
 * <p><b>全部写操作都要求所属单据处于草稿状态</b>，由 Service 统一把关
 * （只靠单据侧的校验挡不住「直接对明细发 PUT」这条路径）。
 *
 * <p>权限串全部复用 {@code inventory:transfer:*}，不为明细另设前缀（契约如此）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/transfer/item")
public class StockTransferItemController extends BaseController
{
    private final IStockTransferItemService stockTransferItemService;

    /**
     * 查询库存调拨明细列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockTransferItem item)
    {
        startPage();
        List<StockTransferItem> list = stockTransferItemService.selectStockTransferItemList(item);
        return getDataTable(list);
    }

    /**
     * 获取库存调拨明细详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(stockTransferItemService.selectStockTransferItemById(itemId));
    }

    /**
     * 新增库存调拨明细。
     *
     * <p>{@code isbn} / {@code title} 由服务端按 {@code bookId} 回填，客户端传了会被覆盖。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:add')")
    @Log(title = "库存调拨明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody StockTransferItem item)
    {
        return toAjax(stockTransferItemService.addStockTransferItem(item));
    }

    /**
     * 修改库存调拨明细（主键在请求体，不在路径 —— RuoYi 式非严格 REST）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:edit')")
    @Log(title = "库存调拨明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody StockTransferItem item)
    {
        return toAjax(stockTransferItemService.updateStockTransferItem(item));
    }

    /**
     * 删除库存调拨明细（支持批量，主键逗号分隔；**物理删除**，本表无 del_flag）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:remove')")
    @Log(title = "库存调拨明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(stockTransferItemService.removeStockTransferItems(itemIds));
    }
}
