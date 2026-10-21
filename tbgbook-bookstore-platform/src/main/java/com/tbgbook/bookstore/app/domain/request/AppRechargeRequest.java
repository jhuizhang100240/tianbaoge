package com.tbgbook.bookstore.app.domain.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员端储值充值请求。
 */
@Data
public class AppRechargeRequest
{
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
    private BigDecimal rechargeAmount;

    private Long activityId;
}
