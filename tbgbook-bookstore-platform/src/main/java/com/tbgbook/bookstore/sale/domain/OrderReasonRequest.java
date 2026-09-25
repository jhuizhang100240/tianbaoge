package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/** 需要「原因」的订单动作共用请求体（取消订单 / 订单红冲）。 */
@Data
public class OrderReasonRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 原因（契约必填） */
    @NotBlank(message = "原因不能为空")
    @Size(max = 200, message = "原因不能超过 200 个字符")
    private String reason;
}
