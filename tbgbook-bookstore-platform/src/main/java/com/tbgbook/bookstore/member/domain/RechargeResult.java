package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 储值充值结果（`POST /member/balance/recharge` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class RechargeResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 充值后本金余额 */
    private BigDecimal principalAfter;

    /** 充值后赠送金余额 */
    private BigDecimal giftAfter;

    /** 充值后余额合计 */
    private BigDecimal totalAfter;

    /** 本次赠送金 */
    private BigDecimal giftAmount;

    /** 赠送金过期时间（长期有效为 null） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime giftExpireTime;
}
