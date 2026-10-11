package com.tbgbook.bookstore.member.domain.aggregate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.common.annotation.Sensitive;
import com.tbgbook.common.enums.DesensitizedType;

import lombok.Data;

/**
 * 会员全景视图（`GET /member/member/{memberId}/profile`，A-45，FR-4.4.8）。
 *
 * <p>一次请求聚合：基础档案 + 教育信息 + 等级进度 + 储值账户 + 积分概览 +
 * 持券统计 + 标签 + 消费统计 + 借阅统计 + 画像摘要。字段口径见 A41。
 *
 * @author tbgbook
 */
@Data
public class MemberPanoramaView implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员基础档案（手机号按平台脱敏规则序列化） */
    private Member member;

    /** 教育信息（未建档时为 null） */
    private StudentView student;

    /** 等级与晋级进度 */
    private LevelView level;

    /** 储值账户（未初始化时为 null） */
    private MemberBalance balance;

    /** 积分概览 */
    private PointsView points;

    /** 持券统计 */
    private CouponsView coupons;

    /** 标签数组 */
    private List<TagView> tags = new ArrayList<>();

    /** 消费统计 */
    private StatsView stats;

    /** 借阅统计 */
    private BorrowStatView borrowStat;

    /** 画像摘要 */
    private PortraitSummaryView portrait;

    /** 教育信息（全景用精简视图，认证状态与学号脱敏沿用实体口径） */
    @Data
    public static class StudentView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 学校 */
        private String school;

        /** 学号（前 4 位 + **** 脱敏） */
        @Sensitive(desensitizedType = DesensitizedType.STUDENT_NO)
        private String studentNo;

        /** 专业 */
        private String major;

        /** 年级 */
        private String grade;

        /** 认证状态（0未认证 1审核中 2已认证 3已拒绝） */
        private Integer certStatus;
    }

    /** 等级与晋级进度 */
    @Data
    public static class LevelView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 当前等级ID */
        private Long levelId;

        /** 当前等级名 */
        private String levelName;

        /** 当前成长值 */
        private Integer growthValue;

        /** 下一等级ID（已是最高档时为 null） */
        private Long nextLevelId;

        /** 下一等级名（已是最高档时为 null） */
        private String nextLevelName;

        /** 下一等级成长门槛（已是最高档时为 null） */
        private Integer nextGrowthThreshold;

        /** 距下一等级还需成长值（已是最高档时为 0） */
        private Integer growthGap;
    }

    /** 积分概览 */
    @Data
    public static class PointsView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 当前可用积分 */
        private Integer points;

        /** 即将到期积分数（窗口取 biz.points.expire.notify.days，默认 30 天） */
        private Integer expiringPoints;

        /** 到期提醒窗口天数 */
        private Integer expiringDays;
    }

    /** 持券统计 */
    @Data
    public static class CouponsView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 持券总数 */
        private Integer total;

        /** 未使用 */
        private Integer unused;

        /** 已使用 */
        private Integer used;

        /** 已过期 */
        private Integer expired;
    }

    /** 标签（全景用精简视图） */
    @Data
    public static class TagView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 标签ID */
        private Long tagId;

        /** 标签名 */
        private String tagName;

        /** 标签类型（1消费偏好 2借阅偏好 3活跃度 4自定义） */
        private Integer tagType;

        /** 颜色（前端标色用） */
        private String color;
    }

    /** 消费统计 */
    @Data
    public static class StatsView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 累计消费次数（biz_member.consume_count） */
        private Integer consumeCount;

        /** 累计消费金额（biz_member.consume_amount） */
        private BigDecimal consumeAmount;

        /** 客单价 = 消费金额 / 消费次数（次数为 0 时为 0.00） */
        private BigDecimal avgOrderAmount;

        /** 最近消费时间 */
        private String lastConsumeTime;
    }

    /** 借阅统计 */
    @Data
    public static class BorrowStatView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 在借册数（status in 1在借 / 3已逾期） */
        private Integer borrowing;

        /** 历史借阅总册数 */
        private Integer total;

        /** 逾期未还册数（status=3） */
        private Integer overdue;
    }

    /** 画像摘要（全景页只给关键几项，完整指标见 /portrait） */
    @Data
    public static class PortraitSummaryView implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 近 12 个月消费频次（订单数） */
        private Integer frequency12m;

        /** 客单价分档标签（如 `100-200`） */
        private String avgOrderBucket;

        /** 最常用消费时段（如 `晚间`；无数据时为 null） */
        private String activePeriod;

        /** RFM 合计分（3~15；样本不足时为 null） */
        private Integer rfmScore;
    }
}
