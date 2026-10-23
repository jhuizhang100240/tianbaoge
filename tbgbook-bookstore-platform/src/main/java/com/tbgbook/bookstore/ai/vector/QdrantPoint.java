package com.tbgbook.bookstore.ai.vector;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Qdrant point。
 *
 * <p>{@code id} 为 UUID 字符串，与 {@code biz_kb_chunk.vector_id} 一一对应；
 * {@code payload} 约定键：{@code storeId/docId/chunkId/gen/active}（AI 域设计稿 §4.2）。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class QdrantPoint
{
    private String id;

    private float[] vector;

    private Map<String, Object> payload;
}
