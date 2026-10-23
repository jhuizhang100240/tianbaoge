package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** 收银结算结果（`POST /sales/cashier/checkout` 的 {@code data}）。 */
@Data
public class CheckoutResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private BigDecimal singleDiscount;
    private BigDecimal memberDiscount;
    private BigDecimal couponAmount;
    private BigDecimal pointAmount;
    private BigDecimal discountTotal;
    private BigDecimal payAmount;
    private Integer pointUsed;
    private Integer pointGain;

    /** 取货码（线上下单到店取货用；门店收银当场取书故也可能为空） */
    private String pickupCode;

    /** 状态（1待支付 2已支付 3待取货 4已完成） */
    private Integer status;

    /** 是否触发折扣审批 */
    private Integer needApproval;

    /** 逐行分摊结果 */
    private List<TrialResult.Line> items;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 幂等命中标记：true 表示本次是重复提交、直接返回首次结果 */
    private Boolean idempotentHit;
}
