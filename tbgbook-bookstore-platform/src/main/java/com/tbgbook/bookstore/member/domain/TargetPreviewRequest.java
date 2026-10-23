package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 圈选人群预览请求体（`POST /member/message/target-preview`）。
 *
 * @author tbgbook
 */
@Data
public class TargetPreviewRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 圈选方式（1按标签 2按等级 3按消费区间） */
    private Integer targetType;

    /** 圈选值（标签ID（可逗号分隔多个） / 等级ID / 如 500-2000） */
    private String targetValue;
}
