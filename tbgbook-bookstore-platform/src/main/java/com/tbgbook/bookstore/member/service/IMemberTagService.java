package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.tbgbook.bookstore.member.domain.MemberTag;
import com.tbgbook.bookstore.member.domain.MemberTagRel;
import com.tbgbook.bookstore.member.domain.TagMarkResult;
import com.tbgbook.bookstore.member.domain.TagRecalcResult;
import com.tbgbook.bookstore.member.domain.TagRelRequest;
import com.tbgbook.bookstore.member.domain.TagRemoveResult;

/**
 * 会员标签 Service 接口（阶段五步骤③b，页面 A-50，FR-4.4.7）。
 *
 * <p>手工标签（isRule=0）的关联走 mark / remove 与关系 CRUD；
 * 规则标签（isRule=1）的关联**只能由重算维护**，任何手工增删 422。
 *
 * @author tbgbook
 */
public interface IMemberTagService
{
    /**
     * 查询会员标签列表。
     *
     * @param tag 查询条件
     * @return 标签集合
     */
    List<MemberTag> selectMemberTagList(MemberTag tag);

    /**
     * 查询会员标签详情。
     *
     * @param tagId 标签ID
     * @return 标签；不存在时抛 404
     */
    MemberTag selectMemberTagById(Long tagId);

    /**
     * 新增会员标签（规则标签即时校验表达式语法；同店同名唯一）。
     *
     * @param tag 标签
     * @return 影响行数
     */
    int addMemberTag(MemberTag tag);

    /**
     * 修改会员标签（**isRule 不允许修改**；规则标签重新校验表达式）。
     *
     * @param tag 标签
     * @return 影响行数
     */
    int updateMemberTag(MemberTag tag);

    /**
     * 删除会员标签（被关系引用的标签禁止删除，建议改为停用）。
     *
     * @param tagIds 标签主键数组
     * @return 影响行数
     */
    int removeMemberTags(Long[] tagIds);

    /**
     * 查询标签关系列表。
     *
     * @param rel 查询条件
     * @return 关系集合
     */
    List<MemberTagRel> selectMemberTagRelList(MemberTagRel rel);

    /**
     * 查询标签关系详情。
     *
     * @param relId 关系ID
     * @return 关系；不存在时抛 404
     */
    MemberTagRel selectMemberTagRelById(Long relId);

    /**
     * 新增标签关系（等价单条手工打标；规则标签 422，重复 409）。
     *
     * @param rel 关系
     * @return 影响行数
     */
    int addMemberTagRel(MemberTagRel rel);

    /**
     * 修改标签关系 —— **恒 422**：关系不支持修改，先解除再重新打标。
     *
     * @param rel 关系
     * @return 不会返回（抛 422）
     */
    int updateMemberTagRel(MemberTagRel rel);

    /**
     * 批量删除标签关系（规则标签的关联不允许手工删除）。
     *
     * @param relIds 关系主键数组
     * @return 影响行数
     */
    int removeMemberTagRels(Long[] relIds);

    /**
     * 手工打标：新增缺失关系、已存在的跳过；规则标签 422、标签不存在 404。
     *
     * @param request 打标请求
     * @return 新增 / 跳过数
     */
    TagMarkResult mark(TagRelRequest request);

    /**
     * 取消手工标签。
     *
     * @param request 解标请求
     * @return 实际解除数
     */
    TagRemoveResult remove(TagRelRequest request);

    /**
     * 立即重算规则标签：整体替换该标签的关系并更新覆盖数。
     *
     * @param tagId 标签ID
     * @return 覆盖数 / 新增 / 移除
     */
    TagRecalcResult recalc(Long tagId);
}
