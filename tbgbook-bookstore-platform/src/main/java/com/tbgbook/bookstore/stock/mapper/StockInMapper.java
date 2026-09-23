package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockIn;

/**
 * 采购入库单 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockInMapper extends BaseMapper<StockIn>
{
    /**
     * 查询采购入库单列表。
     *
     * @param stockIn 查询条件
     * @return 单据集合
     */
    List<StockIn> selectStockInList(StockIn stockIn);

    /**
     * 取 {@code biz_stock_in} 的下一个自增主键（用于预生成 inNo）。
     *
     * <p>{@code in_no} 是 {@code NOT NULL} 且**无默认值**、还带唯一索引，
     * 因此不能「先插入再回填」—— 插入时就必须给出一个既非空又唯一的号。
     * 与 {@code BookMapper#selectNextBookId} 同一手法（那里也是先预取再校正）：
     * 从 {@code information_schema} 取 AUTO_INCREMENT 作预估；插入后若真实主键与预估不符
     * （并发下别人先插了），再按真实主键校正一次。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextInId();
}
