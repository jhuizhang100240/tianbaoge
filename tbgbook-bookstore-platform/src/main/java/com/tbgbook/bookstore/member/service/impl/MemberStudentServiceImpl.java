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
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberStudent;
import com.tbgbook.bookstore.member.domain.StudentAuditRequest;
import com.tbgbook.bookstore.member.domain.StudentSubmitRequest;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.MemberStudentMapper;
import com.tbgbook.bookstore.member.service.IMemberStudentService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 会员教育信息与学号认证 Service 实现（阶段五步骤③a）。
 *
 * <p><b>生命周期与可改性（自定，登记 A37）</b>：
 * 未认证(0)/已拒绝(3) 可改可删；**审核中(1) 不可改不可删**（防审批期间偷换材料）；
 * **已认证(2) 不可改不可删**（认证信息要留痕，重新认证需人工处理）。
 *
 * <p><b>学号唯一性</b>：仅对「审核中 / 已认证」的记录查重（未提交/被拒的草稿不占用学号），
 * 与提交动作的错误文案「学号已被其他会员认证」一致。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberStudentServiceImpl extends ServiceImpl<MemberStudentMapper, MemberStudent>
        implements IMemberStudentService
{
    /** 认证状态：1审核中 */
    private static final int CERT_REVIEWING = 1;

    /** 认证状态：2已认证 */
    private static final int CERT_PASSED = 2;

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    @Override
    public List<MemberStudent> selectMemberStudentList(MemberStudent student)
    {
        return baseMapper.selectMemberStudentList(student);
    }

    @Override
    public MemberStudent selectMemberStudentById(Long memberId)
    {
        return referenceGuard.requireExists(baseMapper, memberId, "学号认证记录");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMemberStudent(MemberStudent student)
    {
        if (student.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        referenceGuard.requireExists(memberMapper, student.getMemberId(), "会员");
        if (baseMapper.selectById(student.getMemberId()) != null)
        {
            throw BizErrors.duplicate("学号认证", "会员ID", student.getMemberId());
        }
        if (student.getCertType() == null)
        {
            student.setCertType(2);
        }
        // 服务端接管字段（契约 skip）
        student.setCertStatus(0);
        student.setApplyTime(null);
        student.setAuditBy("");
        student.setAuditTime(null);
        student.setRejectReason("");
        LocalDateTime now = LocalDateTime.now();
        student.setCreateTime(now);
        student.setUpdateTime(now);
        return baseMapper.insert(student);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMemberStudent(MemberStudent student)
    {
        if (student.getMemberId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的会员");
        }
        MemberStudent existing = referenceGuard.requireExists(baseMapper, student.getMemberId(), "学号认证记录");
        requireEditable(existing);
        if (student.getStudentNo() != null && !student.getStudentNo().isBlank())
        {
            requireStudentNoUnique(student.getStudentNo().trim(), student.getMemberId());
        }
        // 服务端接管字段（契约 skip）
        student.setCertStatus(existing.getCertStatus());
        student.setApplyTime(existing.getApplyTime());
        student.setAuditBy(existing.getAuditBy());
        student.setAuditTime(existing.getAuditTime());
        student.setRejectReason(existing.getRejectReason());
        student.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(student);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMemberStudents(Long[] memberIds)
    {
        List<Long> idList = (memberIds == null) ? Collections.emptyList() : Arrays.asList(memberIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "学号认证记录");
        for (Serializable id : ids)
        {
            MemberStudent row = referenceGuard.requireExists(baseMapper, id, "学号认证记录");
            if (Integer.valueOf(CERT_REVIEWING).equals(row.getCertStatus())
                    || Integer.valueOf(CERT_PASSED).equals(row.getCertStatus()))
            {
                throw BizErrors.ruleViolated("审核中或已认证的认证信息不能删除");
            }
        }
        // 物理删除（本表无 @TableLogic）
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberStudent submit(StudentSubmitRequest request)
    {
        if (request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        requireText(request.getSchool(), "学校不能为空");
        requireText(request.getStudentNo(), "学号不能为空");
        requireText(request.getRealName(), "姓名不能为空");
        referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");

        MemberStudent existing = baseMapper.selectById(request.getMemberId());
        if (existing != null
                && (Integer.valueOf(CERT_REVIEWING).equals(existing.getCertStatus())
                        || Integer.valueOf(CERT_PASSED).equals(existing.getCertStatus())))
        {
            throw BizErrors.conflict("该会员已在审核中或已认证");
        }
        requireStudentNoUnique(request.getStudentNo().trim(), request.getMemberId());

        LocalDateTime now = LocalDateTime.now();
        if (existing == null)
        {
            MemberStudent row = new MemberStudent();
            row.setMemberId(request.getMemberId());
            fill(row, request);
            row.setCertStatus(CERT_REVIEWING);
            row.setApplyTime(now);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            baseMapper.insert(row);
        }
        else
        {
            fill(existing, request);
            existing.setCertStatus(CERT_REVIEWING);
            existing.setApplyTime(now);
            existing.setAuditBy("");
            existing.setAuditTime(null);
            existing.setRejectReason("");
            existing.setUpdateTime(now);
            baseMapper.updateById(existing);
        }

        // 冗余同步会员主档认证状态
        Member patch = new Member();
        patch.setMemberId(request.getMemberId());
        patch.setStudentStatus(CERT_REVIEWING);
        memberMapper.updateById(patch);
        return baseMapper.selectById(request.getMemberId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberStudent audit(Long memberId, StudentAuditRequest request)
    {
        if (memberId == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        Integer result = request.getCertStatus();
        if (result == null || (result != CERT_PASSED && result != 3))
        {
            throw BizErrors.ruleViolated("审核结果取值必须是 2通过 / 3拒绝");
        }
        MemberStudent row = baseMapper.selectById(memberId);
        if (row == null)
        {
            throw BizErrors.notFound("学号认证记录", memberId);
        }
        if (!Integer.valueOf(CERT_REVIEWING).equals(row.getCertStatus()))
        {
            throw BizErrors.conflict("该认证不在审核中状态");
        }
        String reason = request.getRejectReason() == null ? "" : request.getRejectReason().trim();
        if (result == 3 && reason.isEmpty())
        {
            throw BizErrors.ruleViolated("拒绝原因未填");
        }

        row.setCertStatus(result);
        row.setAuditBy(currentUsername());
        row.setAuditTime(LocalDateTime.now());
        row.setRejectReason(result == 3 ? reason : "");
        row.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(row);

        Member patch = new Member();
        patch.setMemberId(memberId);
        if (result == CERT_PASSED)
        {
            // 通过后回填会员真实姓名
            patch.setStudentStatus(CERT_PASSED);
            patch.setRealName(row.getRealName());
        }
        else
        {
            patch.setStudentStatus(3);
        }
        memberMapper.updateById(patch);
        return baseMapper.selectById(memberId);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void requireEditable(MemberStudent row)
    {
        if (Integer.valueOf(CERT_REVIEWING).equals(row.getCertStatus()))
        {
            throw BizErrors.ruleViolated("认证审核中，不能修改");
        }
        if (Integer.valueOf(CERT_PASSED).equals(row.getCertStatus()))
        {
            throw BizErrors.ruleViolated("已认证，不能修改");
        }
    }

    /** 学号查重：仅对「审核中 / 已认证」的记录判重（草稿不占用学号） */
    private void requireStudentNoUnique(String studentNo, Long selfMemberId)
    {
        long count = count(new LambdaQueryWrapper<MemberStudent>()
                .eq(MemberStudent::getStudentNo, studentNo)
                .in(MemberStudent::getCertStatus, CERT_REVIEWING, CERT_PASSED)
                .ne(MemberStudent::getMemberId, selfMemberId));
        if (count > 0)
        {
            throw BizErrors.ruleViolated("学号已被其他会员认证");
        }
    }

    private void fill(MemberStudent row, StudentSubmitRequest request)
    {
        row.setSchool(request.getSchool().trim());
        row.setStudentNo(request.getStudentNo().trim());
        row.setRealName(request.getRealName().trim());
        row.setMajor(request.getMajor() == null ? "" : request.getMajor().trim());
        row.setGrade(request.getGrade() == null ? "" : request.getGrade().trim());
        row.setCertImageUrl(request.getCertImageUrl() == null ? "" : request.getCertImageUrl().trim());
        row.setCertType(request.getCertType() == null ? 2 : request.getCertType());
    }

    private void requireText(String value, String message)
    {
        if (value == null || value.isBlank())
        {
            throw BizErrors.ruleViolated(message);
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
