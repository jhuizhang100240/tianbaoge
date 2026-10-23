package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 储值退款结果（`POST /member/balance/refund` 的 {@code data}，C20 裁决后的出账口径）。
 *
 * <p>{@code refundPrincipal} 是**本次从本金核销的金额**（与请求里的同名布尔开关含义不同）。
 *
 * @author tbgbook
 */
@Data
public class RefundResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 退款后本金余额 */
    private BigDecimal principalAfter;

    /** 退款后赠送金余额 */
    private BigDecimal giftAfter;

    /** 退款后余额合计 */
    private BigDecimal totalAfter;

    /** 本次核销的赠送金 */
    private BigDecimal refundGift;

    /** 本次核销的本金 */
    private BigDecimal refundPrincipal;
}
