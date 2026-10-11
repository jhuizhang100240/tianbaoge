package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 储值退款请求体（`POST /member/balance/refund`，C20 裁决后的出账口径）。
 *
 * <p>必填与取值校验放 Service 抛 422（契约错误码如此），故此处不加 Jakarta 注解。
 *
 * @author tbgbook
 */
@Data
public class RefundRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 退款金额（从储值账户出账的金额） */
    private BigDecimal refundAmount;

    /** 是否可退本金（默认 true；false 时只允许核销赠送金） */
    private Boolean refundPrincipal;

    /** 关联退款单号 */
    private String refNo;

    /** 退款原因（必填） */
    private String reason;
}
