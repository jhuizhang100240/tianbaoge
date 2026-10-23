package com.tbgbook.bookstore.ai.index;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 索引上下文（Redis，TTL 24h）：本代次 gen + 切片范围。
 *
 * <p>进度计数与切换清理都以 {@code chunkIds} 为准——这是重建期间新旧切片行共存时
 * 区分代次的唯一依据（AI 域设计稿 §5.1）。
 *
 * @author tbgbook
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexContext
{
    private long gen;

    private List<Long> chunkIds;

    private long attemptStartedAt;
}
