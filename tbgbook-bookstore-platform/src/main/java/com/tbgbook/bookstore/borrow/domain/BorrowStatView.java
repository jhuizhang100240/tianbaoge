package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 借阅统计（`GET /borrow/statistics/summary` 的 `data`，FR-4.5.9、A-60）。
 *
 * @author tbgbook
 */
@Data
public class BorrowStatView implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 借阅量趋势 */
    private List<TrendPoint> trend = new ArrayList<>();

    /** 热门借阅图书 */
    private List<TopBook> topBooks = new ArrayList<>();

    /** 借阅周期分布（借出到归还的天数分档） */
    private List<Bucket> periodDistribution = new ArrayList<>();

    /** 逾期率（0~1） */
    private BigDecimal overdueRate;

    /** 各等级借阅占比 */
    private List<LevelShare> levelDistribution = new ArrayList<>();

    /** 趋势点 */
    @Data
    public static class TrendPoint implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 时间标签（如 2026-10-11 / 2026-10 / 2026-W41） */
        private String label;

        /** 借出册数 */
        private Integer lendCount;

        /** 归还册数 */
        private Integer returnCount;
    }

    /** 热门图书 */
    @Data
    public static class TopBook implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 图书ID */
        private Long bookId;

        /** ISBN */
        private String isbn;

        /** 书名 */
        private String title;

        /** 借阅次数 */
        private Integer lendCount;
    }

    /** 分布桶 */
    @Data
    public static class Bucket implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 标签 */
        private String label;

        /** 计数 */
        private Integer count;

        /** 占比（0~1） */
        private BigDecimal ratio;
    }

    /** 等级占比 */
    @Data
    public static class LevelShare implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 等级ID */
        private Long levelId;

        /** 等级名（历史等级被删时回退 `等级{id}`） */
        private String levelName;

        /** 借出册数 */
        private Integer lendCount;

        /** 占比（0~1） */
        private BigDecimal ratio;
    }
}
