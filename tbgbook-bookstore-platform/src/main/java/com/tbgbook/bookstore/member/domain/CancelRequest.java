package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 注销会员请求体（`POST /member/member/{memberId}/cancel`）。
 *
 * @author tbgbook
 */
@Data
public class CancelRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 注销原因（必填） */
    private String reason;
}
