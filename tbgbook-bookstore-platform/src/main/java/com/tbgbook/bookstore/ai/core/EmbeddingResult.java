package com.tbgbook.bookstore.ai.core;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 批量向量化结果（与入参文本顺序一一对应）。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class EmbeddingResult
{
    private List<float[]> vectors;

    /** 供应商返回的 prompt tokens（拿不到记 0） */
    private int promptTokens;

    private String model;
}
