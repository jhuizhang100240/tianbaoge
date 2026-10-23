package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 生成罚金 / 赔偿单请求（`POST /borrow/penalty`，FR-4.5.7）。
 *
 * @author tbgbook
 */
@Data
public class PenaltyRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 关联借阅单ID（逾期罚金必填；丢失 / 破损可空） */
    private Long borrowId;

    /** 图书ID */
    private Long bookId;

    /** 类型（1逾期罚金 2丢失赔偿 3严重破损赔偿） */
    private Integer penaltyType;

    /** 赔偿 / 罚金金额（不传则由服务端按规则计算） */
    private BigDecimal amount;

    /** 计算基数（图书定价，不传则取图书当前定价） */
    private BigDecimal calcBase;

    /** 是否优先从押金扣除，默认 true */
    private Boolean deductDeposit;

    /** 图书去向（1转残损库存 2直接报损；本步骤只记录，转库动作留阶段六） */
    private Integer stockTo;

    /** 备注 */
    private String remark;
}
