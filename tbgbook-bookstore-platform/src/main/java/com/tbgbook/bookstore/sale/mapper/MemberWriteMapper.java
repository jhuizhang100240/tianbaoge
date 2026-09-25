package com.tbgbook.bookstore.sale.mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Param;

/**
 * 收银结算所需的**会员域写操作**（跨域写，属阶段五的表）。
 *
 * <p>⚠️ 按已确认口径（`plan/待确认项.md` A28 相关问答）：销售域只写「结算本该落地的会员侧结果」——
 * 积分增减、成长值、消费汇总、储值扣减 —— 而不碰会员资料、等级判定等会员域自身的职责。
 *
 * <p>储值扣减用**条件更新**（`total_balance >= ?`）而不是「先查后改」：
 * 后者在并发下会超扣（两人同时读到余额 100、各扣 80 都成功）。影响 0 行即余额不足。
 */
public interface MemberWriteMapper
{
    /** 积分增减（delta 可负） */
    int addPoints(@Param("memberId") Long memberId, @Param("delta") int delta);

    /** 成长值累加（**只增不减**） */
    int addGrowth(@Param("memberId") Long memberId, @Param("delta") int delta);

    /** 消费汇总：次数 +1、金额累加、最近消费时间刷新 */
    int addConsume(@Param("memberId") Long memberId, @Param("amount") BigDecimal amount);

    /**
     * 储值扣减（**条件更新**：余额不足则影响 0 行）。
     *
     * @param principalPart 扣本金部分
     * @param giftPart      扣赠送金部分
     */
    int deductBalance(@Param("memberId") Long memberId,
            @Param("principalPart") BigDecimal principalPart,
            @Param("giftPart") BigDecimal giftPart,
            @Param("amount") BigDecimal amount);

    /** 储值回退（退款 / 红冲用；本金与赠送金分别回退，§3.2 价格规则 6） */
    int refundBalance(@Param("memberId") Long memberId,
            @Param("principalPart") BigDecimal principalPart,
            @Param("giftPart") BigDecimal giftPart);

    /** 把 `biz_member` 上的余额冗余汇总与 `biz_member_balance` 对齐 */
    int syncBalanceSummary(@Param("memberId") Long memberId);

    /** 写储值流水（金额类型：3消费扣本金 4消费扣赠送金 5退款返本金 6退款返赠送金） */
    int insertStoredValueLog(@Param("memberId") Long memberId,
            @Param("amountType") int amountType,
            @Param("changeAmount") BigDecimal changeAmount,
            @Param("refNo") String refNo,
            @Param("remark") String remark);

    /** 写积分流水（变动类型：1消费获得 2抵扣消耗 5退货返还） */
    int insertPointLog(@Param("memberId") Long memberId,
            @Param("changeType") int changeType,
            @Param("pointsChange") int pointsChange,
            @Param("pointsAfter") int pointsAfter,
            @Param("refNo") String refNo,
            @Param("remark") String remark);

    /** 写成长值流水（变更类型：1消费） */
    int insertGrowthLog(@Param("memberId") Long memberId,
            @Param("growthChange") int growthChange,
            @Param("growthAfter") int growthAfter,
            @Param("refNo") String refNo);
}
