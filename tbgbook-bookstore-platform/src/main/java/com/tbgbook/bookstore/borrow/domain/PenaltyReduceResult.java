package com.tbgbook.bookstore.borrow.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 罚金减免结果（阶段五步骤④第二批）。
 *
 * @author tbgbook
 */
@Data
public class PenaltyReduceResult
{
    /** 罚金单ID */
    private Long penaltyId;

    /** 单据号 */
    private String penaltyNo;

    /** 缴费状态（4已减免） */
    private Integer payStatus;

    /** 本次减免金额 */
    private BigDecimal reduceAmount;

    /** 减免后剩余待缴金额 */
    private BigDecimal remainAmount;

    /** 审批人 */
    private String auditBy;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;
}
