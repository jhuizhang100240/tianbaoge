package com.tbgbook.bookstore.member.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberGrowthLog;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.domain.MemberLevelLog;
import com.tbgbook.bookstore.member.mapper.MemberGrowthLogMapper;
import com.tbgbook.bookstore.member.mapper.MemberLevelLogMapper;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.service.IMemberGrowthService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 会员成长值 Service 实现（阶段五步骤②）。
 *
 * <p><b>自动升级口径（自定，登记 A35）</b>：成长值增加后，取「启用中且门槛 ≤ 当前成长值」里
 * {@code level_rank} 最大的一档作为目标等级；目标等级序号高于会员当前等级时：
 * 同事务更新会员等级并写 {@code biz_member_level_log}（{@code change_type=1 升级}）。
 *
 * <p>降级（连续 12 个月未达门槛才降级的缓冲机制）属定时任务领地，不在本步骤实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberGrowthServiceImpl extends ServiceImpl<MemberGrowthLogMapper, MemberGrowthLog>
        implements IMemberGrowthService
{
    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final MemberLevelMapper memberLevelMapper;

    private final MemberLevelLogMapper memberLevelLogMapper;

    @Override
    public List<MemberGrowthLog> selectMemberGrowthLogList(MemberGrowthLog log)
    {
        return baseMapper.selectMemberGrowthLogList(log);
    }

    @Override
    public MemberGrowthLog selectMemberGrowthLogById(Long logId)
    {
        return referenceGuard.requireExists(baseMapper, logId, "成长值流水");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addGrowth(Long memberId, int changeType, int delta, String refNo, String remark)
    {
        if (delta <= 0)
        {
            throw BizErrors.badRequest("成长值变动必须大于 0（只增不减）");
        }
        if (memberMapper.addGrowth(memberId, delta) == 0)
        {
            throw BizErrors.notFound("会员", memberId);
        }
        Member member = memberMapper.selectById(memberId);
        int after = (member.getGrowthValue() == null) ? 0 : member.getGrowthValue();

        MemberGrowthLog log = new MemberGrowthLog();
        log.setMemberId(memberId);
        log.setChangeType(changeType);
        log.setGrowthChange(delta);
        log.setGrowthAfter(after);
        log.setRefNo(refNo == null ? "" : refNo);
        log.setRemark(remark == null ? "" : remark);
        log.setCreateBy(currentUsername());
        log.setCreateTime(LocalDateTime.now());
        baseMapper.insert(log);

        autoUpgrade(member, after);
        return after;
    }

    /** 达到门槛自动升级（只升不降；写等级变更记录） */
    private void autoUpgrade(Member member, int growthAfter)
    {
        List<MemberLevel> candidates = memberLevelMapper.selectList(
                new LambdaQueryWrapper<MemberLevel>()
                        .eq(MemberLevel::getStatus, "0")
                        .le(MemberLevel::getGrowthThreshold, growthAfter)
                        .orderByDesc(MemberLevel::getLevelRank));
        if (candidates.isEmpty())
        {
            return;
        }
        MemberLevel target = candidates.get(0);
        MemberLevel current = (member.getLevelId() == null)
                ? null : memberLevelMapper.selectById(member.getLevelId());
        int currentRank = (current == null || current.getLevelRank() == null)
                ? 0 : current.getLevelRank();
        if (target.getLevelRank() == null || target.getLevelRank() <= currentRank)
        {
            return;
        }

        Member patch = new Member();
        patch.setMemberId(member.getMemberId());
        patch.setLevelId(target.getLevelId());
        patch.setLevelName(target.getLevelName());
        memberMapper.updateById(patch);

        MemberLevelLog log = new MemberLevelLog();
        log.setMemberId(member.getMemberId());
        log.setBeforeLevelId(current == null ? 0L : current.getLevelId());
        log.setAfterLevelId(target.getLevelId());
        log.setChangeType(1);
        log.setGrowthValue(growthAfter);
        log.setChangeReason("成长值达标自动升级（" + growthAfter + " ≥ " + target.getGrowthThreshold() + "）");
        log.setCreateBy(currentUsername());
        log.setCreateTime(LocalDateTime.now());
        memberLevelLogMapper.insert(log);
    }

    private String currentUsername()
    {
        try
        {
            String name = SecurityUtils.getUsername();
            return name == null ? "" : name;
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
