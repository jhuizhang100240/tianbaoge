package com.tbgbook.bookstore.member.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.Member;

/**
 * 会员 Mapper 接口。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，此处只声明 XML 覆盖不了的列表查询。
 *
 * @author tbgbook
 */
public interface MemberMapper extends BaseMapper<Member>
{
    /**
     * 查询会员列表（可按卡号 / 手机号 / 昵称 / 姓名 / 等级 / 认证状态 / 状态 / 来源与建档时间区间筛选）。
     *
     * @param member 查询条件
     * @return 会员集合
     */
    List<Member> selectMemberList(Member member);

    /** 积分累加（正数） */
    int addPoints(@Param("memberId") Long memberId, @Param("delta") int delta);

    /**
     * 积分扣减（**条件更新**：余额足够才扣），供积分批次消耗前的总账校验与回写。
     *
     * @return 影响行数；0=积分不足（或并发被抢）
     */
    int deductPoints(@Param("memberId") Long memberId, @Param("amount") int amount);

    /** 读取当前积分余额 */
    Integer selectPoints(@Param("memberId") Long memberId);

    /** 成长值累加（只增不减） */
    int addGrowth(@Param("memberId") Long memberId, @Param("delta") int delta);

    /** 消费汇总：次数 +1、金额累加、最近消费时间刷新 */
    int addConsume(@Param("memberId") Long memberId, @Param("amount") BigDecimal amount);
}
