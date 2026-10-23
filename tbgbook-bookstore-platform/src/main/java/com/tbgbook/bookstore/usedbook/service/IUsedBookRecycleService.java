package com.tbgbook.bookstore.usedbook.service;

import java.util.List;

import com.tbgbook.bookstore.usedbook.domain.AcceptRequest;
import com.tbgbook.bookstore.usedbook.domain.AcceptResult;
import com.tbgbook.bookstore.usedbook.domain.Recycle;
import com.tbgbook.bookstore.usedbook.domain.RejectRequest;
import com.tbgbook.bookstore.usedbook.domain.SettleRequest;
import com.tbgbook.bookstore.usedbook.domain.SettleResult;

/**
 * 二手书回收单 Service 接口（阶段六步骤②，页面 A-41，流程二）。
 *
 * @author tbgbook
 */
public interface IUsedBookRecycleService
{
    /**
     * 查询回收单列表。
     *
     * @param recycle 查询条件
     * @return 回收单集合
     */
    List<Recycle> selectRecycleList(Recycle recycle);

    /**
     * 查询回收单详情（含子表明细）。
     *
     * @param recycleId 回收单ID
     * @return 回收单；不存在时抛 404
     */
    Recycle selectRecycleById(Long recycleId);

    /**
     * 新增回收单（单据头 + 子表同一事务；状态=1待验收）。
     *
     * @param recycle 回收单（items 可空）
     * @return 影响行数
     */
    int addRecycle(Recycle recycle);

    /**
     * 修改回收单（仅 1待验收 可改；传 items 时整体替换明细并重算合计）。
     *
     * @param recycle 回收单
     * @return 影响行数
     */
    int updateRecycle(Recycle recycle);

    /**
     * 删除回收单（仅 1待验收 / 4已拒绝 / 5已取消 且未生成二手书副本的可删；明细级联删除）。
     *
     * @param recycleIds 回收单主键数组
     * @return 影响行数
     */
    int removeRecycles(Long[] recycleIds);

    /**
     * 验收回收单（逐行判定成色；不一致必填说明；合格行生成二手书副本）。
     *
     * @param recycleId 回收单ID
     * @param request   验收请求
     * @return 验收结果
     */
    AcceptResult accept(Long recycleId, AcceptRequest request);

    /**
     * 整单拒收（不生成副本、不结算）。
     *
     * @param recycleId 回收单ID
     * @param request   拒收请求
     * @return 影响行数
     */
    int reject(Long recycleId, RejectRequest request);

    /**
     * 回收结算（按结算方式联动储值账户 / 积分 / 成长值流水）。
     *
     * @param recycleId 回收单ID
     * @param request   结算请求
     * @return 结算结果
     */
    SettleResult settle(Long recycleId, SettleRequest request);

    /**
     * 重算单据头汇总（品种数 / 总册数 / 预估金额 / 最终金额 / 成色不一致标记）。
     *
     * @param recycleId 回收单ID
     */
    void recalcAggregates(Long recycleId);
}
