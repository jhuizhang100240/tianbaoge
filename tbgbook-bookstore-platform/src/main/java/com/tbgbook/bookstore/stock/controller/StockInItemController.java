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

import com.tbgbook.bookstore.stock.domain.StockInItem;
import com.tbgbook.bookstore.stock.service.IStockInItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 采购入库单明细 Controller（A-16 开单页的明细表格）。
 *
 * <p>明细要能独立增删改，是因为入库单在前端是「头 + 可编辑明细表格」——
 * 每加一行都回传整张单太重，且并发编辑下容易互相覆盖。
 *
 * <p><b>全部写操作都要求所属单据处于草稿状态</b>，由 Service 统一把关
 * （只靠单据侧的校验挡不住「直接对明细发 PUT」这条路径）。
 *
 * <p>权限串全部复用 {@code inventory:stockin:*}，不为明细另设前缀（契约如此）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/stockin/item")
public class StockInItemController extends BaseController
{
    private final IStockInItemService stockInItemService;

    /**
     * 查询采购入库单明细列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockInItem item)
    {
        startPage();
        List<StockInItem> list = stockInItemService.selectStockInItemList(item);
        return getDataTable(list);
    }

    /**
     * 获取采购入库单明细详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(stockInItemService.selectStockInItemById(itemId));
    }

    /**
     * 新增采购入库单明细。
     *
     * <p>{@code diffQty} 与 {@code amount} 由服务端算（契约明写），客户端传了会被覆盖。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:add')")
    @Log(title = "采购入库明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody StockInItem item)
    {
        return toAjax(stockInItemService.addStockInItem(item));
    }

    /**
     * 修改采购入库单明细（主键在请求体，不在路径 —— RuoYi 式非严格 REST）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:edit')")
    @Log(title = "采购入库明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody StockInItem item)
    {
        return toAjax(stockInItemService.updateStockInItem(item));
    }

    /**
     * 删除采购入库单明细（支持批量，主键逗号分隔；**物理删除**，本表无 del_flag）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:remove')")
    @Log(title = "采购入库明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(stockInItemService.removeStockInItems(itemIds));
    }
}
