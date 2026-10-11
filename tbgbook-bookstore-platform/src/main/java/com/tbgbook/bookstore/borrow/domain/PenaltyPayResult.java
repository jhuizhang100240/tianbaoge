package com.tbgbook.bookstore.borrow.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 缴纳罚金 / 赔偿结果（阶段五步骤④第二批）。
 *
 * @author tbgbook
 */
@Data
public class PenaltyPayResult
{
    /** 单据号 */
    private String penaltyNo;

    /** 缴费状态（2已缴） */
    private Integer payStatus;

    /** 缴费时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    /** 剩余待缴金额（缴清后为 0.00） */
    private BigDecimal remainAmount;
}
