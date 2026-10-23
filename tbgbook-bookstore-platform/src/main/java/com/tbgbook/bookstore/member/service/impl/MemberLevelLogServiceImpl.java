package com.tbgbook.bookstore.member.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.MemberLevelLog;
import com.tbgbook.bookstore.member.mapper.MemberLevelLogMapper;
import com.tbgbook.bookstore.member.service.IMemberLevelLogService;

import lombok.RequiredArgsConstructor;

/**
 * 会员等级变更记录 Service 实现（只读）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberLevelLogServiceImpl extends ServiceImpl<MemberLevelLogMapper, MemberLevelLog>
        implements IMemberLevelLogService
{
    private final ReferenceGuard referenceGuard;

    @Override
    public List<MemberLevelLog> selectMemberLevelLogList(MemberLevelLog log)
    {
        return baseMapper.selectMemberLevelLogList(log);
    }

    @Override
    public MemberLevelLog selectMemberLevelLogById(Long logId)
    {
        return referenceGuard.requireExists(baseMapper, logId, "会员等级变更记录");
    }
}
