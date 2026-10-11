package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.tbgbook.bookstore.member.domain.FreezeRequest;
import com.tbgbook.bookstore.member.domain.FreezeResult;
import com.tbgbook.bookstore.member.domain.MemberFreeze;
import com.tbgbook.bookstore.member.domain.UnfreezeRequest;
import com.tbgbook.bookstore.member.domain.UnfreezeResult;

/**
 * 会员冻结与黑名单 Service 接口（阶段五步骤③c，页面 A-52，FR-4.4.11）。
 *
 * <p>冻结/解冻会同步 {@code biz_member.status}（2冻结 / 3黑名单 / 恢复 1正常）；
 * 记录留痕（解冻置 {@code status=2}），**删除端点恒 422**（见 A39）。
 *
 * @author tbgbook
 */
public interface IMemberFreezeService
{
    /**
     * 查询冻结记录列表。
     *
     * @param freeze 查询条件
     * @return 记录集合
     */
    List<MemberFreeze> selectMemberFreezeList(MemberFreeze freeze);

    /**
     * 查询冻结记录详情。
     *
     * @param freezeId 记录ID
     * @return 记录；不存在时抛 404
     */
    MemberFreeze selectMemberFreezeById(Long freezeId);

    /**
     * 新增冻结记录（等价冻结动作：同步会员状态；已有生效记录 409）。
     *
     * @param freeze 记录
     * @return 影响行数
     */
    int addMemberFreeze(MemberFreeze freeze);

    /**
     * 修改冻结记录（仅**生效中**可改；改类型时同步会员状态）。
     *
     * @param freeze 记录
     * @return 影响行数
     */
    int updateMemberFreeze(MemberFreeze freeze);

    /**
     * 删除冻结记录 —— **恒 422**（历史留痕不可删除；解除请走 unfreeze）。
     *
     * @param freezeIds 记录主键数组
     * @return 不会返回（抛 422）
     */
    int removeMemberFreezes(Long[] freezeIds);

    /**
     * 冻结会员 / 拉黑（同步会员状态为 2 / 3）。
     *
     * @param memberId 会员ID
     * @param request  冻结请求
     * @return 冻结结果
     */
    FreezeResult freeze(Long memberId, FreezeRequest request);

    /**
     * 解冻会员（恢复会员状态为 1正常；历史记录置 2已解除）。
     *
     * @param memberId 会员ID
     * @param request  解冻请求
     * @return 解冻结果
     */
    UnfreezeResult unfreeze(Long memberId, UnfreezeRequest request);
}
