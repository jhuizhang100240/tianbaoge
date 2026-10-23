package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 发送会员消息请求体（`POST /member/message/send`）。
 *
 * @author tbgbook
 */
@Data
public class MessageSendRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 消息模板编码 */
    private String templateCode;

    /** 圈选方式（1按标签 2按等级 3手工名单） */
    private Integer targetType;

    /** 圈选值（标签ID数组逗号分隔 / 等级ID / 会员ID数组逗号分隔） */
    private String targetValue;

    /** 发送渠道（1微信订阅消息 2站内信 3短信） */
    private Integer sendChannel;

    /** 深链地址 */
    private String linkUrl;

    /** 关联业务单号 */
    private String bizNo;

    /** 是否仅试推（取人群中的第一个会员），默认 false */
    private Boolean testSend;
}
