package com.tbgbook.bookstore.borrow.domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 到书通知结果（阶段五步骤④第二批，页面 A-58）。
 *
 * @author tbgbook
 */
@Data
public class ReserveNotifyResult
{
    /** 预约单号 */
    private String reserveNo;

    /** 被通知会员ID */
    private Long memberId;

    /** 到书通知时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime notifyTime;

    /** 取书截止时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 下一位排队预约单号（用于顺延提示；无人排队时为空） */
    private String nextReserveNo;
}
