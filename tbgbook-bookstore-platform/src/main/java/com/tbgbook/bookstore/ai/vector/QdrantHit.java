package com.tbgbook.bookstore.ai.vector;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Qdrant 检索命中项（score 越大越相似，Cosine）。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class QdrantHit
{
    private String id;

    private double score;

    private Map<String, Object> payload;
}
