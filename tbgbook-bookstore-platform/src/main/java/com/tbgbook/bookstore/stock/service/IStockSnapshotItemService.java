package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockSnapshotItem;

/**
 * 库存快照明细 Service 接口（页面 A-27 快照详情）。
 *
 * <p><b>只读</b>：明细由「库存快照生成」任务（阶段十）与主表同事务写入，
 * 人只看 —— 它是历史回溯的原始凭证，开放手工增删改就等于允许事后改写某日的库存账面。
 *
 * @author tbgbook
 */
public interface IStockSnapshotItemService extends IService<StockSnapshotItem>
{
    /**
     * 查询库存快照明细列表（逐行 = 某图书在某库位）。
     *
     * <p>⚠️ 成本类字段 {@code costPrice} / {@code amount} 无 {@code inventory:cost:view} 时
     * **置 null** 而不是报错（FR-4.6 规则 5）；四类数量不受权限影响。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<StockSnapshotItem> selectStockSnapshotItemList(StockSnapshotItem item);

    /**
     * 查询库存快照明细详情（同上，成本字段按权限裁剪）。
     *
     * @param itemId 明细ID
     * @return 明细；不存在时抛 404
     */
    StockSnapshotItem selectStockSnapshotItemById(Long itemId);
}
