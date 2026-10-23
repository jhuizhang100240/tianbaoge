package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 审核学号认证请求体（`POST /member/member/student/{memberId}/audit`）。
 *
 * <p>必填与取值校验放 Service（拒绝必填原因），契约错误码为 422 / 409。
 *
 * @author tbgbook
 */
@Data
public class StudentAuditRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 审核结果（2通过 3拒绝） */
    private Integer certStatus;

    /** 拒绝原因（拒绝时必填） */
    private String rejectReason;
}
