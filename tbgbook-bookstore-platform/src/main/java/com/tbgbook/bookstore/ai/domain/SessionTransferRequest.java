package com.tbgbook.bookstore.ai.domain;

import lombok.Data;

/**
 * 转人工请求（{@code POST /ai/session/{sessionId}/transfer}）。
 *
 * @author tbgbook
 */
@Data
public class SessionTransferRequest
{
    /** 转人工原因（低置信度/用户主动/兜底），必填 */
    private String transferReason;

    /** 接管客服账号（可空 = 进入待接入队列） */
    private String agentBy;
}
