package com.tbgbook.bookstore.ai.core;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 问答结果（已落库的消息 + 指标）。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class ChatOutcome
{
    private Long messageId;

    private String content;

    private double confidence;

    private boolean lowConfidence;

    private boolean fallback;

    /** 引用来源 JSON（[{chunkId,docId,docName,seqNo,score}]） */
    private String citations;

    private int promptTokens;

    private int completionTokens;

    private int firstTokenMs;

    private int latencyMs;
}
