package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.CostChangeLog;

/**
 * 成本变更流水 服务层。
 *
 * <p><b>只读</b>：流水表只增不删。写入由移动加权平均重算时完成（阶段三步骤②）。
 * <p><b>含成本金额</b>，接口只对 {@code inventory:cost:view} 开放（FR-4.6 规则 5）。
 *
 * @author tbgbook
 */
public interface ICostChangeLogService extends IService<CostChangeLog>
{
    /**
     * 查询成本变更流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<CostChangeLog> selectCostChangeLogList(CostChangeLog log);

    /**
     * 查询成本变更流水详情。
     *
     * @param logId 流水ID
     * @return 流水；不存在时抛 404
     */
    CostChangeLog selectCostChangeLogById(Long logId);
}
