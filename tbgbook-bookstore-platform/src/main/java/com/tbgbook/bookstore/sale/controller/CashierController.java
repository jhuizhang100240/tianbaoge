package com.tbgbook.bookstore.sale.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.sale.domain.CashierHoldRequest;
import com.tbgbook.bookstore.sale.domain.CheckoutRequest;
import com.tbgbook.bookstore.sale.domain.CashierScanRequest;
import com.tbgbook.bookstore.sale.domain.MemberLookupRequest;
import com.tbgbook.bookstore.sale.domain.TrialRequest;
import com.tbgbook.bookstore.sale.service.ICashierService;
import com.tbgbook.bookstore.sale.service.ICheckoutService;
import com.tbgbook.bookstore.sale.service.IOrderHoldService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 收银台 Controller（A-28 收银台，FR-4.3.1 / FR-4.3.3）。
 *
 * <p>扫码 / 会员识别 / 试算三个接口**都不落库、不动库存**；
 * {@code hold} 落一条挂单但**不锁库存**（契约如此，取单时才重校验）。真正的扣减在结算（步骤④）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/cashier")
public class CashierController extends BaseController
{
    private final ICashierService cashierService;

    private final IOrderHoldService orderHoldService;

    private final ICheckoutService checkoutService;

    /** 扫码/输入 ISBN 添加商品。**库存不足只提示不阻断**（拦截在结算时）。 */
    @PreAuthorize("@ss.hasPermi('sales:cashier:scan')")
    @PostMapping("/scan")
    public AjaxResult scan(@Valid @RequestBody CashierScanRequest request)
    {
        return success(cashierService.scan(request));
    }

    /** 会员识别：带出等级折扣、可用券与借阅状态（手机号脱敏返回）。 */
    @PreAuthorize("@ss.hasPermi('sales:cashier:memberLookup')")
    @PostMapping("/member-lookup")
    public AjaxResult memberLookup(@Valid @RequestBody MemberLookupRequest request)
    {
        return success(cashierService.memberLookup(request));
    }

    /** 优惠试算：单品折扣 → 会员等级折扣 → 优惠券 → 积分抵扣（顺序固定）。 */
    @PreAuthorize("@ss.hasPermi('sales:cashier:trial')")
    @PostMapping("/trial")
    public AjaxResult trial(@Valid @RequestBody TrialRequest request)
    {
        return success(cashierService.trial(request));
    }

    /** 挂单：**不锁库存**（契约如此），取单时才重校验。 */
    @PreAuthorize("@ss.hasPermi('sales:hold:list')")
    @Log(title = "收银挂单", businessType = BusinessType.INSERT)
    @PostMapping("/hold")
    public AjaxResult hold(@Valid @RequestBody CashierHoldRequest request)
    {
        return success(orderHoldService.cashierHold(request));
    }

    /**
     * 收银结算 —— <b>本项目最核心的写接口</b>（A-28）。
     *
     * <p>同一事务内完成六步：扣减库存 → 写订单与明细 → 写支付流水 → 扣积分/核销券
     * → 累加成长值与积分 → 更新会员消费汇总。
     *
     * <p><b>必须传 `requestId`（幂等键）</b>：同 requestId 重复提交返回首次结果、业务只执行一次；
     * 并发重复（首次仍在跑）返回 409。这不是 `@RepeatSubmit` 那种短时间窗防重复点击。
     */
    @PreAuthorize("@ss.hasPermi('sales:cashier:checkout')")
    @Log(title = "收银结算", businessType = BusinessType.INSERT)
    @PostMapping("/checkout")
    public AjaxResult checkout(@Valid @RequestBody CheckoutRequest request)
    {
        return success(checkoutService.checkout(request));
    }
}
