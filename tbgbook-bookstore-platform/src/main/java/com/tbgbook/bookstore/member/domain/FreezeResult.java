package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 冻结会员 / 拉黑结果（`POST /member/member/{memberId}/freeze` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class FreezeResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long freezeId;

    /** 记录状态（1生效中） */
    private Integer status;

    /** 操作人 */
    private String freezeBy;

    /** 冻结时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime freezeTime;
}
