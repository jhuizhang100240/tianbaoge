package com.tbgbook.bookstore.app.domain.request;

import lombok.Data;

/**
 * 取消订单请求。
 */
@Data
public class CancelOrderRequest
{
    private String reason;
}
