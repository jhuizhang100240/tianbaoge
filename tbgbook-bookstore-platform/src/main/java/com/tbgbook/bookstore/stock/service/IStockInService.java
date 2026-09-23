package com.tbgbook.bookstore.stock.service;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockIn;
import com.tbgbook.bookstore.stock.domain.StockInConfirmResult;

/**
 * 采购入库单 Service 接口。
 *
 * <p><b>本单据是库存数量的唯一合法入口</b>（FR-4.2 规则 1）。四类库存都只能由单据改动，
 * 图书建档时写不进数量 —— 这条约束的落地点就是这里。
 *
 * <p><b>状态机</b>：{@code 1草稿 → 2已入库}（confirm）、{@code → 3已作废}（void）。
 * <b>草稿不占用库存</b>：开单只落库单据与明细，{@link #confirm} 才真正加库存。
 *
 * @author tbgbook
 */
public interface IStockInService extends IService<StockIn>
{
    /**
     * 查询采购入库单列表。
     *
     * @param stockIn 查询条件
     * @return 单据集合
     */
    List<StockIn> selectStockInList(StockIn stockIn);

    /**
     * 查询采购入库单详情（**含明细**）。
     *
     * @param inId 单据ID
     * @return 单据（{@code items} 已填充）；不存在时抛 404
     */
    StockIn selectStockInById(Long inId);

    /**
     * 新增采购入库单（草稿）。
     *
     * <p>服务端负责：生成 {@code inNo}、回填明细的 {@code isbn}/{@code title}、
     * 计算 {@code diffQty} 与 {@code amount}、聚合 {@code bookKind}/{@code totalQty}/{@code totalAmount}。
     * 单据头与明细**同一事务**落库。**不动库存**。
     *
     * @param stockIn 单据（含 {@code items}）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 明细为空；404 图书或库位不存在
     */
    int addStockIn(StockIn stockIn);

    /**
     * 修改采购入库单。
     *
     * <p><b>只有草稿可改</b>（已入库的单据改动会让库存与单据对不上）。
     * 明细采用**整体替换**（删旧插新）—— 明细有服务端算的派生列，
     * 逐行 diff 的复杂度换不来收益，而「头与明细同事务」的语义反而更清晰。
     *
     * @param stockIn 单据（含 {@code items}）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 422 非草稿状态
     */
    int updateStockIn(StockIn stockIn);

    /**
     * 删除采购入库单（**主从级联**）。
     *
     * <p><b>只有草稿可删</b>：已入库的单据要用 {@link #voidStockIn} 作废（那样会反向冲减库存），
     * 直接删掉会让流水表里的记录失去主语。
     *
     * @param inIds 单据主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 含非草稿单据
     */
    int removeStockIns(Long[] inIds);

    /**
     * 确认入库 —— <b>库存真正变动的唯一动作</b>。
     *
     * <p>在一个事务内完成四件事：
     * <ol>
     *   <li>逐行把 {@code actualQty} 累加到该「图书 + 库位」的**可售库存**</li>
     *   <li>逐行写 {@code biz_stock_flow}（{@code biz_type=1 采购入库}，含变动前后结存）</li>
     *   <li>逐书按**移动加权平均**重算成本价，写 {@code biz_cost_change_log}</li>
     *   <li>单据置 {@code 2已入库}、写 {@code inTime}、聚合统计列</li>
     * </ol>
     *
     * <p><b>成本要在改库存之前算</b>：移动加权平均的分母是「本次入库前的实物总量」，
     * 先加库存会让分母变大、算出偏低的成本（见实现的三个 pass）。
     *
     * @param inId   单据ID
     * @param inTime 入库时间，null 取当前时间
     * @return 结果（含逐行 {@code stockAfter} 结存）
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；
     *         409 已是已入库状态；422 单据已作废 / 明细为空 / 实收为 0 / 有差异未填原因
     */
    StockInConfirmResult confirm(Long inId, LocalDateTime inTime);

    /**
     * 作废入库单。
     *
     * <p><b>草稿</b>：直接置 {@code 3已作废}，不涉及库存。<br>
     * <b>已入库</b>：**反向冲减库存**并写红字流水（{@code qty_change} 为负）。
     * 若某行冲减后会让可售数变负，说明这批书已经出过货（销售/调拨/报损），
     * 返回 <b>422</b> 而不是把库存做成负数 —— 那属于「先冲销后续流程」，
     * 不是简单作废。
     *
     * <p>⚠️ <b>作废不回滚成本价</b>：契约对 {@code void} 只要求「反向冲减库存并写红字流水」，
     * 未提成本。移动加权平均要按历史反向重算，需要完整流水链且中间可能已有多次调价，
     * 这属后续阶段的事 —— 此处**刻意不动 {@code biz_book.cost_price}**，并在结果里如实体现。
     *
     * @param inId   单据ID
     * @param reason 作废原因（契约必填）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；409 已作废；422 已入库且库存不足冲减
     */
    int voidStockIn(Long inId, String reason);
}
