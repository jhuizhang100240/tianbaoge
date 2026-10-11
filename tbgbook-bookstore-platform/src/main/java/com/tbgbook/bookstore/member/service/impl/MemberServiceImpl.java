package com.tbgbook.bookstore.member.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.mapper.MemberBalanceMapper;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.service.IMemberService;

import lombok.RequiredArgsConstructor;

/**
 * 会员 Service 实现（阶段五步骤①，页面 A-44~A-52）。
 *
 * <p><b>建档（{@link #addMember}）</b>：卡号 = {@code M} + 手机号；分配默认等级
 * （{@code isDefault=1} 且启用的那一档）；同一事务初始化 1:1 储值账户空行
 * （{@code biz_member_balance}，{@code uk_member} 保证一会员一账户）。
 *
 * <p><b>修改（{@link #updateMember}）</b>：契约 `skip` 里的汇总类与状态字段
 * **一律从库里现有值拷回**（客户端传了也不认）；换绑手机号时同步重算卡号，
 * 维持「卡号 = M + 当前手机号」不变量。
 *
 * <p><b>删除（{@link #removeMembers}）—— 自定口径（登记于 A33）</b>：
 * 本表没有 {@code del_flag}，业务离场走注销；{@code DELETE} 只删**无任何业务痕迹**的
 * 误录会员。痕迹面取自 {@code information_schema} 的全部 {@code member_id} 引用表
 * （{@link #MEMBER_TRACES}），任一表有行即 422「建议改为注销」；同时级联删除
 * 1:1 / 关系子表（student / balance / tag_rel）。这些引用表均无 {@code del_flag}，
 * 软删列统一传 {@code null}。**后续步骤新增 member_id 引用表时要同步补进清单**。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements IMemberService
{
    /** 会员卡号前缀（需求 §3.2 编码表：会员卡 M） */
    private static final String MEMBER_NO_PREFIX = "M";

    /** 状态：1正常 */
    private static final int STATUS_NORMAL = 1;

    /** 状态：4已注销 */
    private static final int STATUS_CANCELLED = 4;

    /**
     * 删除保护的引用面：{@code {表名, 中文名}}。
     *
     * <p>取自 {@code information_schema.columns} 中全部含 {@code member_id} 的表，
     * 去掉主表本身与三张随主行级联删除的子表（balance / student / tag_rel）。
     */
    private static final String[][] MEMBER_TRACES = {
            { "biz_order", "订单" },
            { "biz_order_hold", "挂单" },
            { "biz_return_order", "退货单" },
            { "biz_exchange_order", "换货单" },
            { "biz_borrow", "借阅单" },
            { "biz_borrow_reserve", "借阅预约" },
            { "biz_penalty", "逾期罚金" },
            { "biz_point_batch", "积分批次" },
            { "biz_point_log", "积分流水" },
            { "biz_stored_value_log", "储值流水" },
            { "biz_deposit_log", "押金流水" },
            { "biz_member_growth_log", "成长值流水" },
            { "biz_member_level_log", "等级变更记录" },
            { "biz_member_freeze", "冻结记录" },
            { "biz_coupon", "优惠券" },
            { "biz_recycle", "二手回收单" },
            { "biz_textbook_reserve", "教材预订" },
            { "biz_message", "会员消息" },
            { "biz_subscribe_auth", "订阅授权" },
            { "biz_ai_session", "AI 会话" },
            { "biz_ai_call_log", "AI 调用记录" },
    };

    private final ReferenceGuard referenceGuard;

    private final MemberLevelMapper memberLevelMapper;

    private final MemberBalanceMapper memberBalanceMapper;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Member> selectMemberList(Member member)
    {
        return baseMapper.selectMemberList(member);
    }

    @Override
    public Member selectMemberById(Long memberId)
    {
        return referenceGuard.requireExists(baseMapper, memberId, "会员");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMember(Member member)
    {
        String phone = normalizePhone(member.getPhone());
        member.setPhone(phone);
        requirePhoneUnique(phone, null);

        member.setMemberId(null);
        member.setMemberNo(MEMBER_NO_PREFIX + phone);
        // 服务端接管字段（契约 skip）：状态 / 认证 / 匿名化 / 注册时间都不接受客户端写入
        member.setStatus(STATUS_NORMAL);
        member.setStudentStatus(0);
        member.setAnonymized(0);
        member.setCancelTime(null);
        member.setRegisterTime(LocalDateTime.now());

        MemberLevel defaultLevel = findDefaultLevel();
        member.setLevelId(defaultLevel.getLevelId());
        member.setLevelName(defaultLevel.getLevelName());

        int rows = baseMapper.insert(member);

        // 建档即初始化储值账户空行：后续充值 / 消费 / 退款都落在这行上（步骤②）
        LocalDateTime now = LocalDateTime.now();
        MemberBalance balance = new MemberBalance();
        balance.setMemberId(member.getMemberId());
        balance.setCreateTime(now);
        balance.setUpdateTime(now);
        memberBalanceMapper.insert(balance);
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMember(Member member)
    {
        if (member.getMemberId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的会员");
        }
        Member existing = referenceGuard.requireExists(baseMapper, member.getMemberId(), "会员");
        if ((existing.getStatus() != null && existing.getStatus() == STATUS_CANCELLED)
                || Integer.valueOf(1).equals(existing.getAnonymized()))
        {
            throw BizErrors.ruleViolated("会员已注销，不能再修改");
        }

        String phone = normalizePhone(member.getPhone());
        member.setPhone(phone);
        if (phone.equals(existing.getPhone()))
        {
            member.setMemberNo(existing.getMemberNo());
        }
        else
        {
            requirePhoneUnique(phone, member.getMemberId());
            // 卡号恒等于 M+当前手机号 —— 换绑手机号时同步重算，保持不变量
            member.setMemberNo(MEMBER_NO_PREFIX + phone);
        }

        // 汇总类与状态字段一律从库中现有值拷回（契约 skip；准数据在流水表）
        member.setGrowthValue(existing.getGrowthValue());
        member.setPoints(existing.getPoints());
        member.setBalancePrincipal(existing.getBalancePrincipal());
        member.setBalanceGift(existing.getBalanceGift());
        member.setDepositBalance(existing.getDepositBalance());
        member.setConsumeCount(existing.getConsumeCount());
        member.setConsumeAmount(existing.getConsumeAmount());
        member.setLastConsumeTime(existing.getLastConsumeTime());
        member.setStatus(existing.getStatus());
        member.setCancelTime(existing.getCancelTime());
        member.setAnonymized(existing.getAnonymized());
        return baseMapper.updateById(member);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMembers(Long[] memberIds)
    {
        List<Long> idList = (memberIds == null) ? Collections.emptyList() : Arrays.asList(memberIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "会员");

        for (Serializable id : ids)
        {
            referenceGuard.requireExists(baseMapper, id, "会员");
            for (String[] trace : MEMBER_TRACES)
            {
                referenceGuard.requireNoReference(
                        trace[0], "member_id", id, null,
                        trace[1], "会员", "改为注销");
            }
            // 1:1 / 关系子表随主行级联删除（student 与 tag/rel 的实体在后续步骤落地，先参数化物理删除）
            memberBalanceMapper.delete(new LambdaQueryWrapper<MemberBalance>()
                    .eq(MemberBalance::getMemberId, id));
            jdbcTemplate.update("delete from biz_member_student where member_id = ?", id);
            jdbcTemplate.update("delete from biz_member_tag_rel where member_id = ?", id);
        }
        // biz_member 无 @TableLogic —— 此为物理删除，仅对「无任何痕迹」的误录会员开放
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int addConsume(Long memberId, BigDecimal amount)
    {
        return baseMapper.addConsume(memberId, amount == null ? BigDecimal.ZERO : amount);
    }

    /** 手机号去空格并做非空校验（格式由实体注解校验） */
    private String normalizePhone(String phone)
    {
        String value = (phone == null) ? null : phone.trim();
        if (value == null || value.isEmpty())
        {
            throw BizErrors.badRequest("手机号不能为空");
        }
        return value;
    }

    /** 手机号唯一（uk_phone；预检给友好 409，竞态由全局 DuplicateKeyException 兜底） */
    private void requirePhoneUnique(String phone, Long excludeId)
    {
        LambdaQueryWrapper<Member> wrapper = new LambdaQueryWrapper<Member>()
                .eq(Member::getPhone, phone);
        if (excludeId != null)
        {
            wrapper.ne(Member::getMemberId, excludeId);
        }
        if (count(wrapper) > 0)
        {
            throw BizErrors.duplicate("会员", "手机号", phone);
        }
    }

    /** 注册初始等级 = 启用中的默认等级（有且仅有一个；没有则明确报 422） */
    private MemberLevel findDefaultLevel()
    {
        List<MemberLevel> defaults = memberLevelMapper.selectList(
                new LambdaQueryWrapper<MemberLevel>()
                        .eq(MemberLevel::getIsDefault, 1)
                        .eq(MemberLevel::getStatus, "0")
                        .orderByAsc(MemberLevel::getLevelRank));
        if (defaults.isEmpty())
        {
            throw BizErrors.ruleViolated("尚未配置默认会员等级；请先在「会员等级」中将某一等级设为默认");
        }
        return defaults.get(0);
    }
}
