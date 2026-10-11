package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 借书办理结果（`POST /borrow/lend` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class LendResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 借阅单ID */
    private Long borrowId;

    /** 借阅单号 */
    private String borrowNo;

    /** 应还时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dueTime;

    /** 本单押金快照 */
    private BigDecimal depositAmount;

    /** 等级名 */
    private String levelName;

    /** 借期（天） */
    private Integer borrowDays;

    /** 借阅后该会员剩余可借册数 */
    private Integer remainQuota;
}
