package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.LossApplyItem;

/**
 * 报损申请明细 Service 接口。
 *
 * <p>明细行的写操作有一条统一前置：<b>所属单据必须处于待审批状态</b>。
 * 已通过的单据已扣过库存；已驳回 / 已撤销是终态记录 —— 三种情况都不允许再改明细
 * （与 {@code IStockInItemService} 同一理由，且这里还要多挡两种终态）。
 *
 * @author tbgbook
 */
public interface ILossApplyItemService extends IService<LossApplyItem>
{
    /**
     * 查询报损申请明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<LossApplyItem> selectLossApplyItemList(LossApplyItem item);

    /**
     * 查询报损申请明细详情。
     *
     * @param itemId 明细ID
     * @return 明细；不存在时抛 404
     */
    LossApplyItem selectLossApplyItemById(Long itemId);

    /**
     * 新增报损申请明细。
     *
     * <p>{@code bookId} 与 {@code qty} 在**新增**路径上必填（由 service 按操作判定）。
     * 服务端回填 {@code isbn}/{@code title}，取成本单价与库存快照，算 {@code amount}，
     * 并刷新单据头的聚合列与 {@code needConfirm}。
     *
     * @param item 明细
     * @return 影响行数
     */
    int addLossApplyItem(LossApplyItem item);

    /**
     * 修改报损申请明细（主键在请求体）。
     *
     * @param item 明细；{@code bookId} / {@code qty} 可省略以沿用原值
     * @return 影响行数
     */
    int updateLossApplyItem(LossApplyItem item);

    /**
     * 删除报损申请明细（**物理删除**，本表无 {@code del_flag}）。
     *
     * @param itemIds 明细主键数组
     * @return 实际删除行数
     */
    int removeLossApplyItems(Long[] itemIds);
}
