package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 积分赠送请求体（`POST /member/point/grant`）。
 *
 * <p>必填与取值校验放 Service 抛 422（契约错误码如此），故此处不加 Jakarta 注解。
 *
 * @author tbgbook
 */
@Data
public class PointGrantRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 赠送积分数 */
    private Integer points;

    /** 有效期月数（缺省取 biz.points.valid.months，默认 24） */
    private Integer expireMonths;

    /** 赠送事由（必填） */
    private String reason;
}
