package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.member.domain.MemberLevel;

/**
 * 会员等级 Service 接口。
 *
 * @author tbgbook
 */
public interface IMemberLevelService extends IService<MemberLevel>
{
    /**
     * 查询会员等级列表。
     *
     * @param level 查询条件
     * @return 等级集合
     */
    List<MemberLevel> selectMemberLevelList(MemberLevel level);

    /**
     * 查询会员等级详情。
     *
     * @param levelId 等级ID
     * @return 等级；不存在时抛 404
     */
    MemberLevel selectMemberLevelById(Long levelId);

    /**
     * 新增会员等级（默认等级标记全表唯一；新增即默认时同事务清除其他默认）。
     *
     * @param level 等级
     * @return 影响行数
     */
    int addMemberLevel(MemberLevel level);

    /**
     * 修改会员等级（默认等级标记全表唯一；不允许取消唯一的默认等级）。
     *
     * @param level 等级
     * @return 影响行数
     */
    int updateMemberLevel(MemberLevel level);

    /**
     * 批量删除会员等级（默认等级与被会员引用的等级禁止删除）。
     *
     * @param levelIds 等级主键数组
     * @return 影响行数
     */
    int removeMemberLevels(Long[] levelIds);
}
