package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 积分变动结果（`POST /member/point/adjust` / `/grant` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class PointChangeResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 调整后积分余额 */
    private Integer pointsAfter;

    /** 关联批次ID（扣减跨多批次时取第一个被扣批次） */
    private Long batchId;

    /** 关联流水ID（同上取第一条） */
    private Long logId;
}
