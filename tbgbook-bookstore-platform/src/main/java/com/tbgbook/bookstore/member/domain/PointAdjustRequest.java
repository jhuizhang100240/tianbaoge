package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 人工调整积分请求体（`POST /member/point/adjust`）。
 *
 * <p>必填与取值校验放 Service 抛 422（契约错误码如此），故此处不加 Jakarta 注解。
 *
 * @author tbgbook
 */
@Data
public class PointAdjustRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 积分变动（正增负减） */
    private Integer pointsChange;

    /** 调整原因（必填） */
    private String reason;
}
