package com.tbgbook.bookstore.stock.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.CostChangeLog;
import com.tbgbook.bookstore.stock.mapper.CostChangeLogMapper;
import com.tbgbook.bookstore.stock.service.ICostChangeLogService;

import lombok.RequiredArgsConstructor;

/**
 * 成本变更流水 服务层实现（只读）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class CostChangeLogServiceImpl extends ServiceImpl<CostChangeLogMapper, CostChangeLog>
        implements ICostChangeLogService
{
    private final ReferenceGuard referenceGuard;

    @Override
    public List<CostChangeLog> selectCostChangeLogList(CostChangeLog log)
    {
        return baseMapper.selectCostChangeLogList(log);
    }

    @Override
    public CostChangeLog selectCostChangeLogById(Long logId)
    {
        return referenceGuard.requireExists(baseMapper, logId, "成本变更流水");
    }
}
