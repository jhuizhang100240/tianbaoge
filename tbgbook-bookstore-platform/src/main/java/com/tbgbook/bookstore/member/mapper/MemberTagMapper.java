package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberTag;

/**
 * 会员标签定义 Mapper 接口（仅列表查询与覆盖数原子增减走 XML）。
 *
 * @author tbgbook
 */
public interface MemberTagMapper extends BaseMapper<MemberTag>
{
    /**
     * 查询会员标签列表。
     *
     * @param tag 查询条件
     * @return 标签集合
     */
    List<MemberTag> selectMemberTagList(MemberTag tag);

    /** 覆盖会员数原子增减（不小于 0） */
    int addMemberCount(@Param("tagId") Long tagId, @Param("delta") int delta);

    /** 覆盖会员数直接置值（重算用） */
    int setMemberCount(@Param("tagId") Long tagId, @Param("count") int count);
}
