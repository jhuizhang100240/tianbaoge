package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 解冻会员请求体（`POST /member/member/{memberId}/unfreeze`）。
 *
 * @author tbgbook
 */
@Data
public class UnfreezeRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 解除原因（必填） */
    private String unfreezeReason;
}
