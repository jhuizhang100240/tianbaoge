package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockInItem;

/**
 * 采购入库单明细 Service 接口。
 *
 * <p><b>为什么明细要单独一套端点</b>（契约 `/inventory/stockin/item` 有 5 个）：
 * 入库单在前端是「头 + 可编辑明细表格」，明细行的增删改要能独立提交，
 * 不必每次回传整张单。与教材清单主子表同一形态。
 *
 * <p>⚠️ <b>只有所属单据是草稿时可写</b> —— 已入库的明细改动会让库存与单据对不上。
 *
 * @author tbgbook
 */
public interface IStockInItemService extends IService<StockInItem>
{
    /**
     * 查询明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<StockInItem> selectStockInItemList(StockInItem item);

    /**
     * 查询明细详情。
     *
     * @param itemId 明细ID
     * @return 明细；不存在时抛 404
     */
    StockInItem selectStockInItemById(Long itemId);

    /**
     * 新增明细（服务端算 {@code diffQty} 与 {@code amount}，并回填 isbn/title）。
     *
     * @param item 明细
     * @return 影响行数
     */
    int addStockInItem(StockInItem item);

    /**
     * 修改明细（服务端重算 {@code diffQty} 与 {@code amount}）。
     *
     * @param item 明细
     * @return 影响行数
     */
    int updateStockInItem(StockInItem item);

    /**
     * 批量删除明细（本表无 {@code del_flag}，物理删除）。
     *
     * @param itemIds 明细主键数组
     * @return 实际删除行数
     */
    int removeStockInItems(Long[] itemIds);
}
