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

import com.tbgbook.bookstore.sale.domain.ReturnAuditRequest;
import com.tbgbook.bookstore.sale.domain.ReturnNoOrderRequest;
import com.tbgbook.bookstore.sale.domain.ReturnOrder;
import com.tbgbook.bookstore.sale.service.IReturnOrderService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 退货单 Controller（A-31 退货处理，FR-4.3.5）。
 *
 * <p><b>两条入口，两条路径</b>：
 * <ul>
 *   <li>{@code POST /sales/return} —— **有原单退货**，提交即完成（{@code status=2}）：
 *       校验退货时限与可退数量、回退库存、按支付方式分流退款、返还积分与券中可恢复部分</li>
 *   <li>{@code POST /sales/return/no-order} —— **无单退货**，{@code isNoOrder=1}、
 *       {@code status=1待审核}，库存与退款等审批通过后才执行（§4.3 规则 4），
 *       审批走 {@code /{returnId}/approve}、驳回走 {@code /{returnId}/reject}</li>
 * </ul>
 *
 * <p><b>权限只用一个前缀 {@code sales:refund}</b>（菜单 2308 退换货）：
 * 读是 {@code :list}、写按动作分（{@code :add} / {@code :edit} / {@code :remove} /
 * {@code :approve} / {@code :reject} / {@code :noOrder}）。
 * ⚠️ {@code :approve} **只授店长**（无单退货的金额是收银员填的，得有人对账负责）；
 * {@code :noOrder} 授收银员 —— 谁受理谁提交，审批另说。
 *
 * <p>⚠️ 字面量 {@code /list}、{@code /no-order} 与路径变量 {@code /{returnId}} 的段数不同，
 * 且 {@code /no-order} 是 POST、{@code /{returnId}} 是 GET，不会互相吞掉。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/return")
public class ReturnOrderController extends BaseController
{
    private final IReturnOrderService returnOrderService;

    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/list")
    public TableDataInfo list(ReturnOrder order)
    {
        startPage();
        List<ReturnOrder> list = returnOrderService.selectReturnOrderList(order);
        return getDataTable(list);
    }

    /** 详情一次返回明细（同屏展示，避免页面先出骨架再跳动）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/{returnId}")
    public AjaxResult getInfo(@PathVariable("returnId") Long returnId)
    {
        return success(returnOrderService.selectReturnOrderById(returnId));
    }

    /**
     * 有原单退货（**提交即完成**）。
     *
     * <p>退款金额由服务端按原明细的**分摊后实付金额**算，**不接受客户端传值**（NFR-S9）。
     */
    @PreAuthorize("@ss.hasPermi('sales:refund:add')")
    @Log(title = "退货单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody ReturnOrder order)
    {
        return toAjax(returnOrderService.addReturnOrder(order));
    }

    /**
     * 无单退货（**需店长审批**；{@code isNoOrder=1}、{@code status=1待审核}）。
     *
     * <p>⚠️ 退款额由收银员填 —— 无原单可依据，金额无从算起（与有原单退货的口径不同）。
     */
    @PreAuthorize("@ss.hasPermi('sales:refund:noOrder')")
    @Log(title = "无单退货", businessType = BusinessType.INSERT)
    @PostMapping("/no-order")
    public AjaxResult noOrder(@Valid @RequestBody ReturnNoOrderRequest request)
    {
        return success(returnOrderService.addNoOrderReturn(request));
    }

    /**
     * 审批通过无单退货：回退库存、返还**可恢复**的积分与券（已过期券不返还、已清零积分不返还）。
     */
    @PreAuthorize("@ss.hasPermi('sales:refund:approve')")
    @Log(title = "退货审批", businessType = BusinessType.UPDATE)
    @PostMapping("/{returnId}/approve")
    public AjaxResult approve(@PathVariable("returnId") Long returnId,
            @RequestBody(required = false) ReturnAuditRequest request)
    {
        String remark = (request == null) ? null : request.getAuditRemark();
        return toAjax(returnOrderService.approve(returnId, remark));
    }

    /** 驳回退货申请（**不退库存、不返款**；驳回原因必填，由 service 判定）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:reject')")
    @Log(title = "退货驳回", businessType = BusinessType.UPDATE)
    @PostMapping("/{returnId}/reject")
    public AjaxResult reject(@PathVariable("returnId") Long returnId,
            @Valid @RequestBody ReturnAuditRequest request)
    {
        return toAjax(returnOrderService.reject(returnId, request.getAuditRemark()));
    }

    /** 修改退货单（**仅 1待审核**；金额与状态不接受写入）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:edit')")
    @Log(title = "退货单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ReturnOrder order)
    {
        return toAjax(returnOrderService.updateReturnOrder(order));
    }

    /** 删除退货单（**仅 1待审核**；已完成的是库存与资金的凭证，主从级联删除明细）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:remove')")
    @Log(title = "退货单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{returnIds}")
    public AjaxResult remove(@PathVariable Long[] returnIds)
    {
        return toAjax(returnOrderService.removeReturnOrders(returnIds));
    }
}
