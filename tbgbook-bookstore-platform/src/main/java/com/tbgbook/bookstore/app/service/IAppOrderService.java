package com.tbgbook.bookstore.app.service;

import java.util.Map;

import com.tbgbook.bookstore.app.domain.request.CancelOrderRequest;
import com.tbgbook.bookstore.app.domain.request.CartTrialRequest;
import com.tbgbook.bookstore.app.domain.request.OrderSubmitRequest;
import com.tbgbook.bookstore.app.domain.request.PayRequest;
import com.tbgbook.bookstore.app.domain.request.RefundApplyRequest;

/**
 * 会员端交易 Service（B-07~B-11）。
 */
public interface IAppOrderService
{
    Map<String, Object> trial(CartTrialRequest request);

    Map<String, Object> submit(OrderSubmitRequest request);

    Map<String, Object> pay(Long orderId, PayRequest request);

    Map<String, Object> orderList(Integer status, int pageNum, int pageSize);

    Map<String, Object> orderDetail(Long orderId);

    Map<String, Object> cancel(Long orderId, CancelOrderRequest request);

    Map<String, Object> refundApply(Long orderId, RefundApplyRequest request);

    /**
     * 支付超时取消（系统调用，不校验会员上下文）。
     */
    void cancelExpired(Long orderId);
}
