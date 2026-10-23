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

import com.tbgbook.bookstore.sale.domain.ExchangeConfirmRequest;
import com.tbgbook.bookstore.sale.domain.ExchangeOrder;
import com.tbgbook.bookstore.sale.service.IExchangeOrderService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 换货单 Controller（A-32 换货处理，FR-4.3.6）。
 *
 * <p><b>两步走</b>：{@code POST /sales/exchange} 建单（**1待处理**，只算账、不动库存），
 * {@code POST /sales/exchange/{exchangeId}/confirm} 执行（换出出库、换回入库、按差额补退）。
 * 建单时必须挂原订单（{@code originOrderId}）—— 换回的书要对得上「他买过、还没退过」。
 *
 * <p><b>三个金额一律服务端算</b>（{@code outAmount} / {@code inAmount} / {@code diffAmount}，
 * 正数补款、负数退款）；定价口径见 {@link IExchangeOrderService}。
 *
 * <p>{@code PUT} 的语义是**整体替换明细** —— 本表除明细外没有可写字段
 * （操作人、时间、金额都由服务端维护），不传明细就是没有可改的东西（400）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/exchange")
public class ExchangeOrderController extends BaseController
{
    private final IExchangeOrderService exchangeOrderService;

    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/list")
    public TableDataInfo list(ExchangeOrder order)
    {
        startPage();
        List<ExchangeOrder> list = exchangeOrderService.selectExchangeOrderList(order);
        return getDataTable(list);
    }

    /** 详情一次返回明细（同屏展示）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/{exchangeId}")
    public AjaxResult getInfo(@PathVariable("exchangeId") Long exchangeId)
    {
        return success(exchangeOrderService.selectExchangeOrderById(exchangeId));
    }

    /**
     * 新建换货单（**1待处理**：只算账，不动库存；确认时才动）。
     */
    @PreAuthorize("@ss.hasPermi('sales:refund:add')")
    @Log(title = "换货单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ExchangeOrder order)
    {
        return toAjax(exchangeOrderService.addExchangeOrder(order));
    }

    /** 修改换货单（**仅 1待处理**；整体替换明细并重算三个金额）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:edit')")
    @Log(title = "换货单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ExchangeOrder order)
    {
        return toAjax(exchangeOrderService.updateExchangeOrder(order));
    }

    /**
     * 确认换货：**换出扣可售库存 → 换回回补库存 → 按差额补退**（FR-4.3.6）。
     *
     * <p>差额不为 0 时 {@code payMethod} 必填（正数补款、负数退款）；差额为 0 时可不传。
     */
    @PreAuthorize("@ss.hasPermi('sales:refund:confirm')")
    @Log(title = "换货确认", businessType = BusinessType.UPDATE)
    @PostMapping("/{exchangeId}/confirm")
    public AjaxResult confirm(@PathVariable("exchangeId") Long exchangeId,
            @Valid @RequestBody(required = false) ExchangeConfirmRequest request)
    {
        Integer payMethod = (request == null) ? null : request.getPayMethod();
        return success(exchangeOrderService.confirm(exchangeId, payMethod));
    }

    /** 删除换货单（**仅 1待处理**；主从级联删除明细）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:remove')")
    @Log(title = "换货单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{exchangeIds}")
    public AjaxResult remove(@PathVariable Long[] exchangeIds)
    {
        return toAjax(exchangeOrderService.removeExchangeOrders(exchangeIds));
    }
}
