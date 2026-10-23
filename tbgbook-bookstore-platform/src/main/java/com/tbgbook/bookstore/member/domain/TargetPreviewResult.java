package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 圈选人群预览结果（`POST /member/message/target-preview` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class TargetPreviewResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 命中会员数 */
    private Integer count;

    /** 其中可触达数（存在可用的微信订阅授权） */
    private Integer subscribeAvailable;

    /** 不可触达数 */
    private Integer unreachable;
}
