package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberStudent;

/**
 * 会员教育信息与学号认证 Mapper 接口（1:1，主键即 member_id，仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface MemberStudentMapper extends BaseMapper<MemberStudent>
{
    /**
     * 查询学号认证列表。
     *
     * @param student 查询条件
     * @return 记录集合
     */
    List<MemberStudent> selectMemberStudentList(MemberStudent student);
}
