package com.tbgbook.bookstore.ai.core;

import lombok.Builder;
import lombok.Data;

/**
 * 一条 AI 调用日志（成功与失败都记）。
 *
 * @author tbgbook
 */
@Data
@Builder
public class CallLogEntry
{
    private Integer scene;

    private String modelName;

    private int promptTokens;

    private int completionTokens;

    private int latencyMs;

    private boolean success;

    private String errorMsg;

    private Long sessionId;

    private Long memberId;
}
