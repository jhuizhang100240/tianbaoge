package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.member.domain.MemberLevelLog;

/**
 * 会员等级变更记录 Service 接口（只读）。
 *
 * @author tbgbook
 */
public interface IMemberLevelLogService extends IService<MemberLevelLog>
{
    /**
     * 查询等级变更记录列表。
     *
     * @param log 查询条件
     * @return 记录集合
     */
    List<MemberLevelLog> selectMemberLevelLogList(MemberLevelLog log);

    /**
     * 查询等级变更记录详情。
     *
     * @param logId 记录ID
     * @return 记录；不存在时抛 404
     */
    MemberLevelLog selectMemberLevelLogById(Long logId);
}
