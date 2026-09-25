package com.tbgbook.bookstore.sale.controller;

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

import com.tbgbook.bookstore.sale.domain.ExchangeOrderItem;
import com.tbgbook.bookstore.sale.service.IExchangeOrderItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 换货单明细 Controller（`/sales/exchange/item`）。
 *
 * <p><b>只有「1待处理」的换货单能增删改明细</b>；{@code direction} 区分换出(1)/换回(2)。
 * 单价与小计**一律服务端算**（换出按当前定价、换回按原订单明细的分摊后实付单价），
 * 客户端传的价格一律忽略。改动后父单的换出 / 换回 / 差额三个金额自动重算。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/exchange/item")
public class ExchangeOrderItemController extends BaseController
{
    private final IExchangeOrderItemService exchangeOrderItemService;

    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/list")
    public TableDataInfo list(ExchangeOrderItem item)
    {
        startPage();
        List<ExchangeOrderItem> list = exchangeOrderItemService.selectExchangeOrderItemList(item);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(exchangeOrderItemService.selectExchangeOrderItemById(itemId));
    }

    /** 新增换货单明细（**仅父单待处理**；换回的书必须在原订单里、且不超可换额度）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:add')")
    @Log(title = "换货单明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ExchangeOrderItem item)
    {
        return toAjax(exchangeOrderItemService.addExchangeOrderItem(item));
    }

    /** 修改换货单明细（**仅父单待处理**；只接受方向与数量，价格由服务端重算）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:edit')")
    @Log(title = "换货单明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ExchangeOrderItem item)
    {
        return toAjax(exchangeOrderItemService.updateExchangeOrderItem(item));
    }

    /** 删除换货单明细（**仅父单待处理**，一次只能删同一张换货单下的明细）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:remove')")
    @Log(title = "换货单明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(exchangeOrderItemService.removeExchangeOrderItems(itemIds));
    }
}
