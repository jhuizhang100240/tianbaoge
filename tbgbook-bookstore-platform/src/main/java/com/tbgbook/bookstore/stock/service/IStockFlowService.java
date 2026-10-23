package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockFlow;

/**
 * 库存流水 服务层。
 *
 * <p><b>只读</b>：流水表只增不改不删（根 CLAUDE.md §6.2），本服务**不提供**写入方法。
 * 写入由各单据的服务端逻辑在同事务内完成（阶段三步骤②起）。
 *
 * @author tbgbook
 */
public interface IStockFlowService extends IService<StockFlow>
{
    /**
     * 查询库存流水列表。
     *
     * @param flow 查询条件
     * @return 流水集合
     */
    List<StockFlow> selectStockFlowList(StockFlow flow);

    /**
     * 查询库存流水详情。
     *
     * @param flowId 流水ID
     * @return 流水；不存在时抛 404
     */
    StockFlow selectStockFlowById(Long flowId);
}
