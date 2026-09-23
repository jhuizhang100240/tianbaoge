package com.tbgbook.bookstore.stock.service;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockTransfer;
import com.tbgbook.bookstore.stock.domain.StockTransferConfirmResult;

/**
 * 库存调拨单 Service 接口。
 *
 * <p><b>调拨免审批</b>（Q22 已追认）：{@code 1草稿 → 2已调拨}（{@link #confirm}）、
 * {@code → 3已作废}（{@link #voidTransfer}）。没有「待审批」态 ——
 * 与报损单（必经店长）形成对照。
 *
 * <p><b>草稿不占用库存</b>：开单只落库单据与明细，{@link #confirm} 才真正挪库存。
 *
 * <p><b>本单据是全项目唯一「一次改两个库位」的动作</b>：调出减、调入加，
 * 两条流水在同一事务内写，避免出现「已经出去了但还没进来」的中间态（FR-4.2.10）。
 * 调拨前后**实物总量不变** —— 这是确认逻辑必须成立的不变量。
 *
 * @author tbgbook
 */
public interface IStockTransferService extends IService<StockTransfer>
{
    /**
     * 查询库存调拨单列表（含调出/调入库位的编码、名称、区域）。
     *
     * @param transfer 查询条件
     * @return 单据集合
     */
    List<StockTransfer> selectStockTransferList(StockTransfer transfer);

    /**
     * 查询库存调拨单详情（**含明细**）。
     *
     * @param transferId 单据ID
     * @return 单据（{@code items} 已填充）；不存在时抛 404
     */
    StockTransfer selectStockTransferById(Long transferId);

    /**
     * 新增库存调拨单（草稿，**不动库存**）。
     *
     * <p>服务端负责：生成 {@code transferNo}、回填明细的 {@code isbn}/{@code title}、
     * 聚合 {@code bookKind}/{@code totalQty}。单据头与明细**同一事务**落库。
     *
     * @param transfer 单据（含 {@code items}）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 明细为空 / 未指定库位；
     *         404 图书或库位不存在；422 调出与调入库位相同 / 同书出现多行
     */
    int addStockTransfer(StockTransfer transfer);

    /**
     * 修改库存调拨单。
     *
     * <p><b>只有草稿可改</b>（已调拨的单据改动会让库存与单据对不上）。
     * 明细采用**整体替换**（删旧插新），理由同 {@code StockInServiceImpl#updateStockIn}。
     *
     * @param transfer 单据（含 {@code items}，可为 null 表示只改单据头）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 422 非草稿状态
     */
    int updateStockTransfer(StockTransfer transfer);

    /**
     * 删除库存调拨单（**主从级联**）。
     *
     * <p><b>只有草稿可删</b>：已调拨的单据要用 {@link #voidTransfer} 作废
     * （那样会反向挪回库存），直接删掉会让流水表里的记录失去主语。
     *
     * @param transferIds 单据主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 含非草稿单据
     */
    int removeStockTransfers(Long[] transferIds);

    /**
     * 确认调拨 —— <b>调拨真正生效的动作</b>。
     *
     * <p>在一个事务内完成三件事：
     * <ol>
     *   <li>逐行把 {@code qty} 从调出库位的**可售库存**扣减</li>
     *   <li>逐行把它累加到调入库位的**可售库存**</li>
     *   <li>每行写**两条**库存流水：调出（{@code biz_type=10}）与调入（{@code biz_type=9}）</li>
     * </ol>
     *
     * <p><b>先整体校验再动手</b>：任一行的调出库位库存不足即 422，
     * 且**一条流水都不产生**（不是改到一半才发现）。本方法在事务里，
     * 虽然失败会整体回滚，但一次性把能给的错误给全，比让人反复试更好。
     *
     * <p><b>并发</b>：库存行的改动用 {@code @Version} 乐观锁，
     * 未命中时由 {@code ReferenceGuard#requireAffected} 转成 409，不会静默超卖。
     *
     * @param transferId   单据ID
     * @param transferTime 调拨时间，null 取当前时间
     * @return 结果（含逐行的两个库位结存）
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；
     *         409 已是已调拨或已作废状态；422 明细为空 / 调出库位库存不足
     */
    StockTransferConfirmResult confirm(Long transferId, LocalDateTime transferTime);

    /**
     * 作废调拨单。
     *
     * <p><b>草稿</b>：直接置 {@code 3已作废}，不涉及库存。<br>
     * <b>已调拨</b>：**反向挪回库存** —— 调入库位扣减、调出库位回补，
     * 并写两条红字流水（{@code biz_type} 仍按「10 = 离开该库位 / 9 = 进入该库位」的方向语义取）。
     * 若调入库位的可售数不足以扣回，说明这批书已经出过货（销售 / 报损 / 二次调拨），
     * 返回 <b>422</b> 而不是把库存做成负数。
     *
     * <p>⚠️ <b>为什么要有这个方法</b>：{@code inventory:transfer:remove} 只授给店长
     * （{@code 07_role_seed.sql} 的横切规则②：「删除权仅店长；非店长需要撤销单据时走
     * 『作废 / 红冲』业务动作」）。没有 void 端点时，库管员建错草稿后既删不掉也废不掉。
     * 该规则预设了本动作的存在，故本端点是它的补全（2026-09-23 用户裁决）。
     *
     * @param transferId 单据ID
     * @param reason     作废原因（契约必填）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；409 已作废；
     *         422 已调拨且调入库位库存不足，无法回滚
     */
    int voidTransfer(Long transferId, String reason);
}
