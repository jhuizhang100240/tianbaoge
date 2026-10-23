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

import com.tbgbook.bookstore.sale.domain.OrderHold;
import com.tbgbook.bookstore.sale.service.IOrderHoldService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 收银挂单 Controller（A-28 收银台，FR-4.3.2）。
 *
 * <p>⚠️ 列表**不带 cartData**（json 列）：列表按单号/金额/时间/会员筛，不需要购物车内容；详情才带。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/hold")
public class OrderHoldController extends BaseController
{
    private final IOrderHoldService orderHoldService;

    @PreAuthorize("@ss.hasPermi('sales:hold:list')")
    @GetMapping("/list")
    public TableDataInfo list(OrderHold hold)
    {
        startPage();
        List<OrderHold> list = orderHoldService.selectOrderHoldList(hold);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('sales:hold:list')")
    @GetMapping("/{holdId}")
    public AjaxResult getInfo(@PathVariable("holdId") Long holdId)
    {
        return success(orderHoldService.selectOrderHoldById(holdId));
    }

    @PreAuthorize("@ss.hasPermi('sales:hold:add')")
    @Log(title = "收银挂单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody OrderHold hold)
    {
        return toAjax(orderHoldService.addOrderHold(hold));
    }

    @PreAuthorize("@ss.hasPermi('sales:hold:edit')")
    @Log(title = "收银挂单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody OrderHold hold)
    {
        return toAjax(orderHoldService.updateOrderHold(hold));
    }

    @PreAuthorize("@ss.hasPermi('sales:hold:remove')")
    @Log(title = "收银挂单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{holdIds}")
    public AjaxResult remove(@PathVariable Long[] holdIds)
    {
        return toAjax(orderHoldService.removeOrderHolds(holdIds));
    }

    /** 作废挂单 → status=3（不涉库存与资金，无副作用）。 */
    @PreAuthorize("@ss.hasPermi('sales:hold:cancel')")
    @Log(title = "收银挂单", businessType = BusinessType.UPDATE)
    @PostMapping("/{holdId}/cancel")
    public AjaxResult cancel(@PathVariable("holdId") Long holdId)
    {
        return toAjax(orderHoldService.cancelHold(holdId));
    }

    /** 取单 → status=2。**重新校验库存与价格**，变动写进 changes 交收银员确认。 */
    @PreAuthorize("@ss.hasPermi('sales:hold:resume')")
    @Log(title = "收银挂单", businessType = BusinessType.UPDATE)
    @PostMapping("/{holdId}/resume")
    public AjaxResult resume(@PathVariable("holdId") Long holdId)
    {
        return success(orderHoldService.resumeHold(holdId));
    }
}
