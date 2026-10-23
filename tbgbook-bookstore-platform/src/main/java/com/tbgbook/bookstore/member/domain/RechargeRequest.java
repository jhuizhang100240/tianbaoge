package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 储值充值请求体（`POST /member/balance/recharge`）。
 *
 * <p>必填与取值校验放 Service 抛 422（契约错误码如此），故此处不加 Jakarta 注解。
 *
 * @author tbgbook
 */
@Data
public class RechargeRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 充值本金金额 */
    private BigDecimal rechargeAmount;

    /** 充值活动ID（命中活动时赠送金自动计算） */
    private Long activityId;

    /** 支付方式（1现金 2微信 3支付宝） */
    private Integer payMethod;

    /** 支付流水号 */
    private String refNo;
}
