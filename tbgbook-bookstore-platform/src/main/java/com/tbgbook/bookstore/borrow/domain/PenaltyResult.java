package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 生成罚金 / 赔偿单结果（`POST /borrow/penalty` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class PenaltyResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 单据ID */
    private Long penaltyId;

    /** 单据号 */
    private String penaltyNo;

    /** 金额 */
    private BigDecimal amount;

    /** 计算式说明 */
    private String calcDetail;

    /** 已从押金扣除金额 */
    private BigDecimal deductDeposit;

    /** 缴费状态（1待缴 2已缴 3部分缴纳 4已减免） */
    private Integer payStatus;

    /** 待缴金额 = amount - deductDeposit */
    private BigDecimal remainAmount;
}
