package com.tbgbook.bookstore.member.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberBalance;

/**
 * 会员储值账户 Mapper 接口。
 *
 * <p>读列表走 XML；余额变动全部走**条件/原子更新**（扣减带 {@code total_balance >= amount}），
 * 保证并发下不超扣、不丢更新。
 *
 * @author tbgbook
 */
public interface MemberBalanceMapper extends BaseMapper<MemberBalance>
{
    /** 查询储值账户列表 */
    List<MemberBalance> selectMemberBalanceList(MemberBalance balance);

    /** 充值入账（本金 / 赠送金分列增加；total 由旧值重算） */
    int addBalance(@Param("memberId") Long memberId,
            @Param("principalDelta") BigDecimal principalDelta,
            @Param("giftDelta") BigDecimal giftDelta);

    /**
     * 消费扣减（**条件更新**：合计余额足够才扣）。
     *
     * @return 影响行数；0=余额不足（或并发被抢）
     */
    int deductBalance(@Param("memberId") Long memberId,
            @Param("principalPart") BigDecimal principalPart,
            @Param("giftPart") BigDecimal giftPart,
            @Param("amount") BigDecimal amount);

    /** 退款回退（本金 / 赠送金按调用方给定的份额回退；total 重算） */
    int refundBalance(@Param("memberId") Long memberId,
            @Param("principalPart") BigDecimal principalPart,
            @Param("giftPart") BigDecimal giftPart);

    /** 把 biz_member 上的余额冗余汇总与 biz_member_balance 对齐 */
    int syncBalanceSummary(@Param("memberId") Long memberId);
}
