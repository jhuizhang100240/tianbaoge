package com.tbgbook.bookstore.ai.core;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问答请求（阶段九 /app/ai/chat 与内部复用）。
 *
 * @author tbgbook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest
{
    private Long sessionId;

    private Long memberId;

    private String question;

    /** 调用方注入的实时业务数据（库存/订单/余额，含 snapshotTime） */
    private Map<String, Object> realtimeData;
}
