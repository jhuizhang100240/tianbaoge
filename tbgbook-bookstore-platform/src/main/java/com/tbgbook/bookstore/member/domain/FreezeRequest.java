package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 冻结会员 / 拉黑请求体（`POST /member/member/{memberId}/freeze`）。
 *
 * <p>必填与取值校验放 Service 抛 422（契约错误码如此）。
 *
 * @author tbgbook
 */
@Data
public class FreezeRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 类型（1冻结 2黑名单） */
    private Integer freezeType;

    /** 原因（必填） */
    private String reason;
}
