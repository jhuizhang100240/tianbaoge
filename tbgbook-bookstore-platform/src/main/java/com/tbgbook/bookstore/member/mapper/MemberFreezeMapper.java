package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberFreeze;

/**
 * 会员冻结与黑名单 Mapper 接口（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface MemberFreezeMapper extends BaseMapper<MemberFreeze>
{
    /**
     * 查询冻结记录列表。
     *
     * @param freeze 查询条件
     * @return 记录集合
     */
    List<MemberFreeze> selectMemberFreezeList(MemberFreeze freeze);
}
