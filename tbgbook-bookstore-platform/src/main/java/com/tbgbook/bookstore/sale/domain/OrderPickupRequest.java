package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/** 订单取货核销请求（`POST /sales/order/{orderId}/pickup`）。 */
@Data
public class OrderPickupRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 取货码（必填） */
    @NotBlank(message = "取货码不能为空")
    private String pickupCode;

    /** 核销人（不传取当前登录用户） */
    private String pickupBy;
}
