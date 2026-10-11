package com.tbgbook.bookstore.member.service.impl;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.FreezeRequest;
import com.tbgbook.bookstore.member.domain.FreezeResult;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberFreeze;
import com.tbgbook.bookstore.member.domain.UnfreezeRequest;
import com.tbgbook.bookstore.member.domain.UnfreezeResult;
import com.tbgbook.bookstore.member.mapper.MemberFreezeMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.service.IMemberFreezeService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 会员冻结与黑名单 Service 实现（阶段五步骤③c）。
 *
 * <p><b>自定口径（登记 A39）</b>：① 冻结类型 1冻结/2黑名单 → 会员状态 2/3；
 * ② 同一会员同时只允许一条生效记录（409）；③ 仅生效记录可改，改类型同步会员状态；
 * ④ **删除恒 422**（历史留痕）；⑤ 已注销会员不能冻结（422）；
 * ⑥ 冻结动作权限取 {@code member:freeze:add}（接口文档此处写 {@code member:freeze:list}，
 * 与权限命名规约不符且会把「冻结」绑到只读权限上，故按规约实现并登记待确认）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberFreezeServiceImpl extends ServiceImpl<MemberFreezeMapper, MemberFreeze>
        implements IMemberFreezeService
{
    /** 记录状态：1生效中 */
    private static final int STATUS_ACTIVE = 1;

    /** 记录状态：2已解除 */
    private static final int STATUS_RELEASED = 2;

    /** 会员状态：1正常 */
    private static final int MEMBER_NORMAL = 1;

    /** 会员状态：2冻结 */
    private static final int MEMBER_FROZEN = 2;

    /** 会员状态：3黑名单 */
    private static final int MEMBER_BLACKLIST = 3;

    /** 会员状态：4已注销 */
    private static final int MEMBER_CANCELLED = 4;

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    @Override
    public List<MemberFreeze> selectMemberFreezeList(MemberFreeze freeze)
    {
        return baseMapper.selectMemberFreezeList(freeze);
    }

    @Override
    public MemberFreeze selectMemberFreezeById(Long freezeId)
    {
        return referenceGuard.requireExists(baseMapper, freezeId, "冻结记录");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMemberFreeze(MemberFreeze freeze)
    {
        if (freeze.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        MemberFreeze inserted = insertFreeze(freeze.getMemberId(), freeze.getFreezeType(),
                freeze.getReason());
        freeze.setFreezeId(inserted.getFreezeId());
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMemberFreeze(MemberFreeze freeze)
    {
        if (freeze.getFreezeId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的冻结记录");
        }
        MemberFreeze existing = referenceGuard.requireExists(baseMapper, freeze.getFreezeId(), "冻结记录");
        if (!Integer.valueOf(STATUS_ACTIVE).equals(existing.getStatus()))
        {
            throw BizErrors.ruleViolated("已解除的冻结记录不允许修改");
        }
        if (freeze.getFreezeType() != null)
        {
            requireType(freeze.getFreezeType());
        }
        if (freeze.getReason() != null)
        {
            requireReason(freeze.getReason());
            freeze.setReason(freeze.getReason().trim());
        }
        // 服务端接管字段
        freeze.setMemberId(existing.getMemberId());
        freeze.setFreezeBy(existing.getFreezeBy());
        freeze.setFreezeTime(existing.getFreezeTime());
        freeze.setUnfreezeBy(existing.getUnfreezeBy());
        freeze.setUnfreezeTime(existing.getUnfreezeTime());
        freeze.setUnfreezeReason(existing.getUnfreezeReason());
        freeze.setStatus(STATUS_ACTIVE);
        freeze.setUpdateTime(LocalDateTime.now());
        int rows = baseMapper.updateById(freeze);
        if (freeze.getFreezeType() != null && !freeze.getFreezeType().equals(existing.getFreezeType()))
        {
            applyFreezeStatus(existing.getMemberId(), freeze.getFreezeType());
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMemberFreezes(Long[] freezeIds)
    {
        List<Long> idList = (freezeIds == null) ? Collections.emptyList() : Arrays.asList(freezeIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "冻结记录");
        for (Serializable id : ids)
        {
            referenceGuard.requireExists(baseMapper, id, "冻结记录");
        }
        throw BizErrors.ruleViolated("冻结记录需要留痕，不能删除；如需解除请走解冻动作");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FreezeResult freeze(Long memberId, FreezeRequest request)
    {
        if (memberId == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        MemberFreeze row = insertFreeze(memberId, request.getFreezeType(), request.getReason());
        FreezeResult result = new FreezeResult();
        result.setFreezeId(row.getFreezeId());
        result.setStatus(row.getStatus());
        result.setFreezeBy(row.getFreezeBy());
        result.setFreezeTime(row.getFreezeTime());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnfreezeResult unfreeze(Long memberId, UnfreezeRequest request)
    {
        if (memberId == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        if (request.getUnfreezeReason() == null || request.getUnfreezeReason().isBlank())
        {
            throw BizErrors.ruleViolated("解除原因未填");
        }
        referenceGuard.requireExists(memberMapper, memberId, "会员");
        MemberFreeze active = findActive(memberId);
        if (active == null)
        {
            throw BizErrors.conflict("该会员当前没有生效中的冻结记录");
        }
        LocalDateTime now = LocalDateTime.now();
        active.setStatus(STATUS_RELEASED);
        active.setUnfreezeBy(currentUsername());
        active.setUnfreezeTime(now);
        active.setUnfreezeReason(request.getUnfreezeReason().trim());
        active.setUpdateTime(now);
        baseMapper.updateById(active);
        restoreMemberStatus(memberId);

        UnfreezeResult result = new UnfreezeResult();
        result.setFreezeId(active.getFreezeId());
        result.setStatus(STATUS_RELEASED);
        result.setUnfreezeBy(active.getUnfreezeBy());
        result.setUnfreezeTime(active.getUnfreezeTime());
        result.setRestoredMemberStatus(MEMBER_NORMAL);
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 共用冻结插入：校验会员与活跃记录 → 落库 → 同步会员状态 */
    private MemberFreeze insertFreeze(Long memberId, Integer freezeType, String reason)
    {
        requireType(freezeType);
        requireReason(reason);
        Member member = referenceGuard.requireExists(memberMapper, memberId, "会员");
        if (Integer.valueOf(MEMBER_CANCELLED).equals(member.getStatus()))
        {
            throw BizErrors.ruleViolated("会员已注销，不能冻结");
        }
        if (findActive(memberId) != null)
        {
            throw BizErrors.conflict("该会员已有生效中的冻结记录");
        }
        LocalDateTime now = LocalDateTime.now();
        MemberFreeze row = new MemberFreeze();
        row.setMemberId(memberId);
        row.setFreezeType(freezeType);
        row.setReason(reason.trim());
        row.setFreezeBy(currentUsername());
        row.setFreezeTime(now);
        row.setUnfreezeBy("");
        row.setUnfreezeReason("");
        row.setStatus(STATUS_ACTIVE);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        baseMapper.insert(row);
        applyFreezeStatus(memberId, freezeType);
        return row;
    }

    /** 冻结/拉黑：同步会员状态（1冻结→2冻结 / 2黑名单→3黑名单） */
    private void applyFreezeStatus(Long memberId, int freezeType)
    {
        Member patch = new Member();
        patch.setMemberId(memberId);
        patch.setStatus(freezeType == 1 ? MEMBER_FROZEN : MEMBER_BLACKLIST);
        memberMapper.updateById(patch);
    }

    /** 解冻：恢复会员状态为 1正常 */
    private void restoreMemberStatus(Long memberId)
    {
        Member patch = new Member();
        patch.setMemberId(memberId);
        patch.setStatus(MEMBER_NORMAL);
        memberMapper.updateById(patch);
    }

    private MemberFreeze findActive(Long memberId)
    {
        List<MemberFreeze> rows = baseMapper.selectList(new LambdaQueryWrapper<MemberFreeze>()
                .eq(MemberFreeze::getMemberId, memberId)
                .eq(MemberFreeze::getStatus, STATUS_ACTIVE));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireType(Integer freezeType)
    {
        if (freezeType == null || (freezeType != 1 && freezeType != 2))
        {
            throw BizErrors.ruleViolated("冻结类型取值必须是 1冻结 / 2黑名单");
        }
    }

    private void requireReason(String reason)
    {
        if (reason == null || reason.isBlank())
        {
            throw BizErrors.ruleViolated("原因未填");
        }
        if (reason.trim().length() > 500)
        {
            throw BizErrors.ruleViolated("原因不能超过 500 个字符");
        }
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
