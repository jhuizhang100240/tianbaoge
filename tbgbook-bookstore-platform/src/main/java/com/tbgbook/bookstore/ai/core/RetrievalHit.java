package com.tbgbook.bookstore.ai.core;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 检索命中项（回表复核后的结果，供引用组装与问答使用）。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class RetrievalHit
{
    private long chunkId;

    private long docId;

    private String docName;

    private int seqNo;

    private String content;

    private double score;
}
