package com.tbgbook.bookstore.ai.domain;

import lombok.Data;

/**
 * 人工客服回复请求（{@code POST /ai/session/message}）。
 *
 * @author tbgbook
 */
@Data
public class ManualReplyRequest
{
    private Long sessionId;

    private String content;
}
