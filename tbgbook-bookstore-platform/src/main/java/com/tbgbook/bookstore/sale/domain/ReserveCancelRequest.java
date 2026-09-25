package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/** 取消教材预订请求（`POST /sales/reserve/{reserveId}/cancel`）。 */
@Data
public class ReserveCancelRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "取消原因不能为空")
    private String reason;
}
