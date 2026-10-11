package com.tbgbook.bookstore.member.mapper;

import java.util.List;

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
}
