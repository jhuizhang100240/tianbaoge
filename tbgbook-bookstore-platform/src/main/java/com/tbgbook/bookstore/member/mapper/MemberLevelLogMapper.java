package com.tbgbook.bookstore.member.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberLevelLog;

/**
 * 会员等级变更记录 Mapper 接口（只读）。
 *
 * @author tbgbook
 */
public interface MemberLevelLogMapper extends BaseMapper<MemberLevelLog>
{
    /**
     * 查询等级变更记录列表。
     *
     * @param log 查询条件
     * @return 记录集合
     */
    List<MemberLevelLog> selectMemberLevelLogList(MemberLevelLog log);
}
