package com.tbgbook.bookstore.member.domain.aggregate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 会员画像视图（`GET /member/member/{memberId}/portrait`，FR-4.4.8）。
 *
 * <p>成本口径字段（{@code costVisible=false} 时为 null）：
 * {@link Spending#getGrossMarginAmount()} —— 需要 `member:cost:view` 权限（5 个成本权限之一）。
 * 其余指标均为非成本口径，持 `member:member:list` 即可见。
 *
 * <p>统计窗口固定为**近 12 个月**（含当月），时间边界由应用侧计算（A32 同口径）。
 * 口径细节见 A41。
 *
 * @author tbgbook
 */
@Data
public class MemberPortraitView implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 统计窗口（天） */
    private Integer windowDays;

    /** 是否可见成本口径指标 */
    private Boolean costVisible;

    /** 消费频次与金额 */
    private Spending spending;

    /** 品类偏好（按金额占比倒序） */
    private List<CategoryShare> categoryPreference = new ArrayList<>();

    /** 客单价分布（五档固定桶） */
    private List<Bucket> avgOrderDistribution = new ArrayList<>();

    /** 活跃时段分布（四段 + 未知） */
    private List<Bucket> activeDays = new ArrayList<>();

    /** RFM 评分 */
    private Rfm rfm;

    /** 消费频次与金额 */
    @Data
    public static class Spending implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 窗口内订单数 */
        private Integer orderCount;

        /** 窗口内总实付金额 */
        private BigDecimal totalAmount;

        /** 客单价 = 总实付 / 订单数 */
        private BigDecimal avgOrderAmount;

        /** 月均消费频次 = 订单数 / 12 */
        private BigDecimal monthlyFrequency;

        /** 窗口内有消费的天数（去重） */
        private Integer activeDayCount;

        /** 最近一次消费时间 */
        private String lastOrderTime;

        /** 窗口内毛利 = Σ(实付金额 - 成本快照 × 数量)；**成本口径**，无权限为 null */
        private BigDecimal grossMarginAmount;
    }

    /** 品类偏好项 */
    @Data
    public static class CategoryShare implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 分类ID */
        private Long categoryId;

        /** 分类名（分类被删时回退「未分类」） */
        private String categoryName;

        /** 该品类实付金额 */
        private BigDecimal amount;

        /** 占比（0~1，四位小数） */
        private BigDecimal ratio;
    }

    /** 分布桶 */
    @Data
    public static class Bucket implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 桶标签（如 `100-200` / `上午`） */
        private String label;

        /** 计数 */
        private Integer count;

        /** 占比（0~1，四位小数） */
        private BigDecimal ratio;
    }

    /** RFM 评分（1~5，5 最好；样本不足 3 人时为 null） */
    @Data
    public static class Rfm implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 最近消费距今天数 */
        private Integer recencyDays;

        /** R 分（最近越好越高） */
        private Integer recencyScore;

        /** 窗口内订单数 */
        private Integer frequency;

        /** F 分 */
        private Integer frequencyScore;

        /** 窗口内实付金额 */
        private BigDecimal monetary;

        /** M 分 */
        private Integer monetaryScore;

        /** 合计分（3~15） */
        private Integer totalScore;
    }
}
