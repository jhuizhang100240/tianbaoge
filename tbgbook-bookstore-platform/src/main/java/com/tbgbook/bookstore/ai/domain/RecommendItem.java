package com.tbgbook.bookstore.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 推荐位预览条目（source 区分算法/人工通道，score 可解释）。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class RecommendItem
{
    private Long bookId;

    private String title;

    private String coverUrl;

    /** 算法 / 人工 */
    private String source;

    private double score;

    private String reason;
}
