package com.tbgbook.bookstore.usedbook.service;

import java.util.List;

import com.tbgbook.bookstore.usedbook.domain.RecycleItem;

/**
 * 二手书回收明细 Service 接口（阶段六步骤②，页面 A-41）。
 *
 * @author tbgbook
 */
public interface IUsedBookRecycleItemService
{
    /**
     * 查询回收明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<RecycleItem> selectRecycleItemList(RecycleItem item);

    /**
     * 查询回收明细详情。
     *
     * @param itemId 明细ID
     * @return 明细；不存在时抛 404
     */
    RecycleItem selectRecycleItemById(Long itemId);

    /**
     * 新增回收明细（仅 1待验收 单据可加；成色档必须存在且启用）。
     *
     * @param item 明细
     * @return 影响行数
     */
    int addRecycleItem(RecycleItem item);

    /**
     * 修改回收明细（仅 1待验收 单据 / 明细可改）。
     *
     * @param item 明细
     * @return 影响行数
     */
    int updateRecycleItem(RecycleItem item);

    /**
     * 删除回收明细（仅 1待验收 明细可删；级联重算单据头汇总）。
     *
     * @param itemIds 明细主键数组
     * @return 影响行数
     */
    int removeRecycleItems(Long[] itemIds);
}
