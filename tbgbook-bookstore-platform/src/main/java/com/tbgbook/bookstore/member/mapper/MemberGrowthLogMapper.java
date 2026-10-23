package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberGrowthLog;

/**
 * 成长值流水 Mapper 接口（只增不删，仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface MemberGrowthLogMapper extends BaseMapper<MemberGrowthLog>
{
    /**
     * 查询成长值流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<MemberGrowthLog> selectMemberGrowthLogList(MemberGrowthLog log);
}
