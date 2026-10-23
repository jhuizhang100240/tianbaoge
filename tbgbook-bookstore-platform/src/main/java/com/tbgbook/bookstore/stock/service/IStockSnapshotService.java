package com.tbgbook.bookstore.stock.service;

import java.time.LocalDate;
import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.SnapshotCompare;
import com.tbgbook.bookstore.stock.domain.StockSnapshot;

/**
 * 库存快照 Service 接口（页面 A-27 库存快照）。
 *
 * <p><b>只读</b>：快照由定时任务「库存快照生成」（每日 02:00）产出，**本接口不提供写方法**。
 * 生成逻辑属 `plan/backend-plan.md` **阶段十**的定时任务范围 ——
 * 届时任务在 quartz 模块调用本 Service 写主表与明细（同事务），
 * 本步骤只交付读与 {@link #compare} 对比。
 *
 * @author tbgbook
 */
public interface IStockSnapshotService extends IService<StockSnapshot>
{
    /**
     * 查询库存快照列表（支持按快照日期区间筛选）。
     *
     * <p>⚠️ 成本类字段 {@code totalAmount} 无 {@code inventory:cost:view} 时**置 null**
     * 而不是报错（FR-4.6 规则 5）。
     *
     * @param snapshot 查询条件
     * @return 快照集合
     */
    List<StockSnapshot> selectStockSnapshotList(StockSnapshot snapshot);

    /**
     * 查询库存快照详情（同上，成本字段按权限裁剪）。
     *
     * @param snapshotId 快照ID
     * @return 快照；不存在时抛 404
     */
    StockSnapshot selectStockSnapshotById(Long snapshotId);

    /**
     * 把基准快照与另一份快照逐图书对比（FR-4.2.11 历史回溯 / 原型 A-27）。
     *
     * <p>对比对象二选一：{@code compareSnapshotId} 优先；只给 {@code compareDate} 时
     * 按日期取那一份快照。
     *
     * <p><b>只出现在一侧的图书也会出现在结果里</b>（缺的一侧按 0 参与计算）：
     * 「新增了」与「清空了」正是对比页最该看出来的两种变化。
     *
     * @param snapshotId        基准快照ID
     * @param compareSnapshotId 对比快照ID（与 {@code compareDate} 二选一）
     * @param compareDate       对比日期（与 {@code compareSnapshotId} 二选一）
     * @return 对比结果（成本类增减字段按权限裁剪）
     * @throws com.tbgbook.common.exception.ServiceException 404 基准或对比快照不存在
     *         （含「该日期未生成快照」）；422 两个对比参数都没给
     */
    SnapshotCompare compare(Long snapshotId, Long compareSnapshotId, LocalDate compareDate);
}
