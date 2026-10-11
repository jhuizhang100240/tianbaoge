package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 发送会员消息结果（`POST /member/message/send` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class MessageSendResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 任务标识（本步骤同步扇出；取首条消息ID，见 A40） */
    private Long taskId;

    /** 实际处理的目标人数 */
    private Integer targetCount;

    /** 发送前可触达数（存在可用的微信订阅授权） */
    private Integer subscribeAvailable;
}
