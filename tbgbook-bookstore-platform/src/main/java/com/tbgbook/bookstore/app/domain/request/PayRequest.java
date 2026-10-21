package com.tbgbook.bookstore.app.domain.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发起支付请求。
 */
@Data
public class PayRequest
{
    @NotNull(message = "支付金额不能为空")
    private BigDecimal payAmount;

    private Integer payMethod;
}
