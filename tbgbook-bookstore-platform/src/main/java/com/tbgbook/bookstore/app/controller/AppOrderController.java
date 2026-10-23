package com.tbgbook.bookstore.app.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.app.domain.request.CancelOrderRequest;
import com.tbgbook.bookstore.app.domain.request.CartTrialRequest;
import com.tbgbook.bookstore.app.domain.request.OrderSubmitRequest;
import com.tbgbook.bookstore.app.domain.request.PayRequest;
import com.tbgbook.bookstore.app.domain.request.RefundApplyRequest;
import com.tbgbook.bookstore.app.service.IAppOrderService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端交易 Controller（B-07~B-11）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app")
public class AppOrderController extends BaseController
{
    private final IAppOrderService orderService;

    @PostMapping("/cart/trial")
    public AjaxResult trial(@Validated @RequestBody CartTrialRequest request)
    {
        return success(orderService.trial(request));
    }

    @PostMapping("/order/submit")
    public AjaxResult submit(@Validated @RequestBody OrderSubmitRequest request)
    {
        return success(orderService.submit(request));
    }

    @PostMapping("/order/{orderId}/pay")
    public AjaxResult pay(@PathVariable("orderId") Long orderId,
            @Validated @RequestBody PayRequest request)
    {
        return success(orderService.pay(orderId, request));
    }

    @GetMapping("/order/list")
    public AjaxResult list(@RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(orderService.orderList(status, pageNum, pageSize));
    }

    @GetMapping("/order/{orderId}")
    public AjaxResult detail(@PathVariable("orderId") Long orderId)
    {
        return success(orderService.orderDetail(orderId));
    }

    @PostMapping("/order/{orderId}/cancel")
    public AjaxResult cancel(@PathVariable("orderId") Long orderId,
            @RequestBody(required = false) CancelOrderRequest request)
    {
        return success(orderService.cancel(orderId, request == null ? new CancelOrderRequest() : request));
    }

    @PostMapping("/order/{orderId}/refund-apply")
    public AjaxResult refundApply(@PathVariable("orderId") Long orderId,
            @Validated @RequestBody RefundApplyRequest request)
    {
        return success(orderService.refundApply(orderId, request));
    }
}
