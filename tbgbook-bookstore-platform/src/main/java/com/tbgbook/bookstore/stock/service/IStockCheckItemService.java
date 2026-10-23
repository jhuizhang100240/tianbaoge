package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockCheckItem;

/**
 * 库存盘点明细 Service 接口。
 *
 * <p>与教材清单、采购入库单的明细同一形态：明细要能独立增删改
 * （前端是「头 + 可编辑明细表格」，每加一行回传整张单太重）。
 *
 * <p>⚠️ **只有所属单据处于「进行中」时可写** —— 差异一旦应用到库存，
 * 再改明细会让单据与库存对不上。
 *
 * @author tbgbook
 */
public interface IStockCheckItemService extends IService<StockCheckItem>
{
    /**
     * 查询盘点明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<StockCheckItem> selectStockCheckItemList(StockCheckItem item);

    /**
     * 查询盘点明细详情。
     *
     * @param itemId 明细ID
     * @return 明细；不存在时抛 404
     */
    StockCheckItem selectStockCheckItemById(Long itemId);

    /**
     * 新增盘点明细（账面数由服务端按当前库存快照，派生列服务端算）。
     *
     * @param item 明细
     * @return 影响行数
     */
    int addStockCheckItem(StockCheckItem item);

    /**
     * 修改盘点明细。
     *
     * @param item 明细
     * @return 影响行数
     */
    int updateStockCheckItem(StockCheckItem item);

    /**
     * 批量删除盘点明细（本表无 {@code del_flag}，物理删除）。
     *
     * @param itemIds 明细主键数组
     * @return 实际删除行数
     */
    int removeStockCheckItems(Long[] itemIds);
}
