package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 解冻会员结果（`POST /member/member/{memberId}/unfreeze` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class UnfreezeResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long freezeId;

    /** 记录状态（2已解除） */
    private Integer status;

    /** 解除人 */
    private String unfreezeBy;

    /** 解除时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unfreezeTime;

    /** 恢复后的会员状态（1正常） */
    private Integer restoredMemberStatus;
}
