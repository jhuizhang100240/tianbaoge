package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 退还借阅押金请求（`POST /borrow/deposit/refund`）。
 *
 * @author tbgbook
 */
@Data
public class DepositRefundRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 退还金额 */
    private BigDecimal amount;

    /** 退款方式（1现金 2微信 3支付宝 4储值余额） */
    private Integer payMethod;

    /** 退还原因（必填） */
    private String reason;
}
