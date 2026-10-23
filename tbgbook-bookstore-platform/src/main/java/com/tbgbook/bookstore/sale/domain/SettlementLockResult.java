package com.tbgbook.bookstore.sale.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/** 锁定日结结果：单号/状态/差异额/差异说明/**被锁定的订单笔数**。 */
@Data
public class SettlementLockResult
{
    private Long settleId;
    private String settleNo;
    private Integer status;

    /** 对账差异（正数=实点多于账面） */
    private BigDecimal diffAmount;

    private String diffReason;

    /** 本次锁定的订单笔数（窗口内 status 未取消、未日结的订单） */
    private Integer lockedOrderCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settleTime;
}
