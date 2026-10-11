package com.tbgbook.bookstore.sale.mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Param;

/**
 * 收银结算遗留的**会员域储值写操作**（阶段五步骤②后只剩储值）。
 *
 * <p>⚠️ 积分 / 成长值 / 消费汇总已于阶段五步骤②收编到会员域 Service
 * （{@code IMemberPointService} / {@code IMemberGrowthService} / {@code IMemberService#addConsume}）。
 * 本 Mapper 只剩储值扣减 / 回退 / 汇总同步 / 储值流水，待阶段五步骤②b（储值服务）落地后删除。
 *
 * <p>储值扣减用**条件更新**（`total_balance >= ?`）而不是「先查后改」：
 * 后者在并发下会超扣（两人同时读到余额 100、各扣 80 都成功）。影响 0 行即余额不足。
 */
public interface MemberWriteMapper
{
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

}
