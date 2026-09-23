package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockTransferItem;

/**
 * 库存调拨明细 Service 接口。
 *
 * <p>明细行的写操作有一条统一前置：<b>所属单据必须处于草稿状态</b>。
 * 已调拨的单据改明细会让库存与单据对不上（库存是按当时的明细挪过的），
 * 而这一点必须**在明细这一侧也拦一道** —— 只靠单据侧的校验挡不住
 * 「直接对明细发 PUT」这条路径（与 {@code IStockInItemService} 同一理由）。
 *
 * @author tbgbook
 */
public interface IStockTransferItemService extends IService<StockTransferItem>
{
    /**
     * 查询库存调拨明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<StockTransferItem> selectStockTransferItemList(StockTransferItem item);

    /**
     * 查询库存调拨明细详情。
     *
     * @param itemId 明细ID
     * @return 明细；不存在时抛 404
     */
    StockTransferItem selectStockTransferItemById(Long itemId);

    /**
     * 新增库存调拨明细。
     *
     * <p>{@code bookId} 与 {@code qty} 在**新增**路径上必填（由 service 按操作判定），
     * 服务端回填 {@code isbn}/{@code title} 并刷新单据头的聚合列。
     *
     * @param item 明细
     * @return 影响行数
     */
    int addStockTransferItem(StockTransferItem item);

    /**
     * 修改库存调拨明细（主键在请求体）。
     *
     * @param item 明细；{@code bookId} / {@code qty} 可省略以沿用原值
     * @return 影响行数
     */
    int updateStockTransferItem(StockTransferItem item);

    /**
     * 删除库存调拨明细（**物理删除**，本表无 {@code del_flag}）。
     *
     * @param itemIds 明细主键数组
     * @return 实际删除行数
     */
    int removeStockTransferItems(Long[] itemIds);
}
