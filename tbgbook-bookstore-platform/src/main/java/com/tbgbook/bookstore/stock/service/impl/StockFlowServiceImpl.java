package com.tbgbook.bookstore.stock.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.StockFlow;
import com.tbgbook.bookstore.stock.mapper.StockFlowMapper;
import com.tbgbook.bookstore.stock.service.IStockFlowService;

import lombok.RequiredArgsConstructor;

/**
 * 库存流水 服务层实现（只读）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockFlowServiceImpl extends ServiceImpl<StockFlowMapper, StockFlow> implements IStockFlowService
{
    private final ReferenceGuard referenceGuard;

    @Override
    public List<StockFlow> selectStockFlowList(StockFlow flow)
    {
        return baseMapper.selectStockFlowList(flow);
    }

    @Override
    public StockFlow selectStockFlowById(Long flowId)
    {
        return referenceGuard.requireExists(baseMapper, flowId, "库存流水");
    }
}
