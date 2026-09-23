package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockCheck;

/**
 * 库存盘点单 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockCheckMapper extends BaseMapper<StockCheck>
{
    /**
     * 查询库存盘点单列表。
     *
     * @param stockCheck 查询条件
     * @return 单据集合
     */
    List<StockCheck> selectStockCheckList(StockCheck stockCheck);

    /**
     * 取 {@code biz_stock_check} 的下一个自增主键（用于预生成 checkNo）。
     *
     * <p>{@code check_no} 是 {@code NOT NULL} 且带唯一索引，不能「先插入再回填」。
     * 与 `BookMapper#selectNextBookId` / `StockInMapper#selectNextInId` 同一手法。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextCheckId();
}
