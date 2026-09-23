package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockTransfer;

/**
 * 库存调拨单 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockTransferMapper extends BaseMapper<StockTransfer>
{
    /**
     * 查询库存调拨单列表（含 JOIN {@code biz_location} 的调出/调入库位展示字段）。
     *
     * @param transfer 查询条件
     * @return 单据集合
     */
    List<StockTransfer> selectStockTransferList(StockTransfer transfer);

    /**
     * 取 {@code biz_stock_transfer} 的下一个自增主键（用于预生成 transferNo）。
     *
     * <p>{@code transfer_no} 是 {@code NOT NULL} 且**无默认值**、还带唯一索引
     * （{@code uk_transfer_no}），因此不能「先插入再回填」—— 插入时就必须给出一个
     * 既非空又唯一的号。与 {@code StockInMapper#selectNextInId} 同一手法：
     * 从 {@code information_schema} 取 AUTO_INCREMENT 作预估；插入后若真实主键与预估不符
     * （并发下别人先插了），再按真实主键校正一次。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextTransferId();
}
