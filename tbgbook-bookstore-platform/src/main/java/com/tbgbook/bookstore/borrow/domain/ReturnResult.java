package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 还书办理结果（`POST /borrow/return` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class ReturnResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 借阅单号 */
    private String borrowNo;

    /** 会员昵称 */
    private String memberName;

    /** 书名 */
    private String title;

    /** 借出时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime borrowTime;

    /** 应还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueTime;

    /** 实际归还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime returnTime;

    /** 是否逾期 */
    private Integer isOverdue;

    /** 逾期天数（归还当日不计入） */
    private Integer overdueDays;

    /** 应缴罚金 */
    private BigDecimal fineAmount;

    /** 已缴罚金 */
    private BigDecimal finePaid;

    /** 罚金计算式说明 */
    private String fineDetail;

    /** 按时归还成长值奖励 */
    private Integer growthGain;

    /** 归还后状态（2已归还 / 4已赔偿） */
    private Integer status;
}
