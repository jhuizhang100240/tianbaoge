package com.tbgbook.bookstore.borrow.domain;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 缴纳罚金 / 赔偿请求（阶段五步骤④第二批）。
 *
 * <p>表内无「已缴金额」列，故**一次性缴清**：{@code payAmount} 必须等于待缴金额。
 *
 * @author tbgbook
 */
@Data
public class PenaltyPayRequest
{
    /** 缴费方式（1现金 2微信 3支付宝 4押金扣除） */
    @NotNull(message = "缴费方式不能为空")
    @Min(value = 1, message = "缴费方式取值必须是 1/2/3/4")
    @Max(value = 4, message = "缴费方式取值必须是 1/2/3/4")
    private Integer payMethod;

    /** 缴费金额 */
    @NotNull(message = "缴费金额不能为空")
    @DecimalMin(value = "0.01", message = "缴费金额必须大于 0")
    private BigDecimal payAmount;

    /** 第三方交易号 */
    private String tradeNo;

    /** 备注 */
    private String remark;
}
