package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 提交学号认证请求体（`POST /member/member/student/submit`）。
 *
 * <p>必填与取值校验放 Service（memberId / school / studentNo / realName 必填），
 * 契约错误码为 422 / 409，故此处不加 Jakarta 注解。
 *
 * @author tbgbook
 */
@Data
public class StudentSubmitRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 学校 */
    private String school;

    /** 学号 */
    private String studentNo;

    /** 姓名 */
    private String realName;

    /** 专业 */
    private String major;

    /** 年级 */
    private String grade;

    /** 认证方式（1校方接口 2人工审核），默认 2 */
    private Integer certType;

    /** 学生证照片地址 */
    private String certImageUrl;
}
