package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.CostChangeLog;

/**
 * 成本变更流水 Mapper 接口。
 *
 * <p>本表**只增不删**，这里**只有查询方法**。写入由移动加权平均重算时完成
 * （阶段三步骤②采购入库确认）。
 *
 * @author tbgbook
 */
public interface CostChangeLogMapper extends BaseMapper<CostChangeLog>
{
    /**
     * 查询成本变更流水列表（含 JOIN `biz_book` 的 isbn 筛选与书名展示）。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<CostChangeLog> selectCostChangeLogList(CostChangeLog log);
}
