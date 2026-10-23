package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 缴纳借阅押金请求（`POST /borrow/deposit/pay`）。
 *
 * @author tbgbook
 */
@Data
public class DepositPayRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 缴纳金额 */
    private BigDecimal amount;

    /** 支付方式（1现金 2微信 3支付宝 4储值余额） */
    private Integer payMethod;

    /** 关联单号 */
    private String refNo;

    /** 备注 */
    private String remark;
}
