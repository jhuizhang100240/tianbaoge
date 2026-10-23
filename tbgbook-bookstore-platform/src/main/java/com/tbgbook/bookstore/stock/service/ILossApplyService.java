package com.tbgbook.bookstore.stock.service;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.LossApply;
import com.tbgbook.bookstore.stock.domain.LossApproveResult;

/**
 * 报损申请单 Service 接口。
 *
 * <p><b>状态机</b>：{@code 1待审批 → 2已通过}（{@link #approve}）、
 * {@code → 3已驳回}（{@link #reject}）、{@code → 4已撤销}（{@link #revoke}）。
 * <b>只有 {@code 2已通过} 会动库存</b>，其余三态都不产生任何流水。
 *
 * <p><b>待审批期间不扣库存</b>（§4.2 规则 4）：这是报损与调拨最本质的差别 ——
 * 调拨免审批、确认即生效；报损必须先让店长看一眼。
 * 书还烂在架上，但账上仍算它的，「待审批」这几天的账面与实物是暂时不符的，
 * 这正是需要一个状态而不是直接扣减的原因。
 *
 * <p><b>审批权限与单据权限分离</b>：{@code inventory:loss:approve} 仅店长，
 * 单据的增删改查走 {@code inventory:loss:audit}（库管员 + 店长）。
 * 从权限串看不出来，但这是本单据设计的关键 —— {@code ILossApplyService} 的
 * {@link #approve} / {@link #reject} 与其余方法分属两个权限面。
 *
 * @author tbgbook
 */
public interface ILossApplyService extends IService<LossApply>
{
    /** 金额阈值对应的 `sys_config` 键（§4.2 规则 4，默认 500 元） */
    String CFG_CONFIRM_AMOUNT = "biz.loss.confirm.amount";

    /**
     * 查询报损申请单列表。
     *
     * @param apply 查询条件
     * @return 单据集合
     */
    List<LossApply> selectLossApplyList(LossApply apply);

    /**
     * 查询报损申请单详情（**含明细**）。
     *
     * @param applyId 单据ID
     * @return 单据（{@code items} 已填充）；不存在时抛 404
     */
    LossApply selectLossApplyById(Long applyId);

    /**
     * 新增报损申请单（**待审批，不动库存**）。
     *
     * <p>服务端负责：生成 {@code applyNo}（BS+yyyyMMdd+流水）、回填明细的
     * {@code isbn}/{@code title}、取成本单价与库存快照、算 {@code amount}、
     * 聚合 {@code bookKind}/{@code totalQty}/{@code totalAmount}、
     * 按 {@code biz.loss.confirm.amount} 置 {@code needConfirm}、
     * 记 {@code applyBy}/{@code applyTime}。单据头与明细**同一事务**落库。
     *
     * @param apply 单据（含 {@code items}）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 明细为空 / 未指定报损类型；
     *         404 图书不存在；422 数量非法
     */
    int addLossApply(LossApply apply);

    /**
     * 修改报损申请单。
     *
     * <p><b>只有待审批可改</b>：已通过的单据已扣过库存，改动会让单据与库存对不上；
     * 已驳回 / 已撤销的是终态记录，要重新报损应当新开一单（这样审批链才完整）。
     * 明细采用**整体替换**（删旧插新），并**重新取一次库存快照与成本单价**。
     *
     * @param apply 单据（含 {@code items}，可为 null 表示只改单据头）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 422 非待审批状态
     */
    int updateLossApply(LossApply apply);

    /**
     * 删除报损申请单（**主从级联**）。
     *
     * <p><b>只有 {@code 2已通过} 禁止删除</b>：它是库存的实际凭证（{@code biz_stock_flow}
     * 按 {@code applyNo} 引用它），删掉会让流水失去主语。待审批 / 已驳回 / 已撤销三态
     * 都没动过库存，可以删除（操作留痕在 {@code sys_oper_log}，与业务行是否保留无关）。
     *
     * @param applyIds 单据主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 含已通过的单据
     */
    int removeLossApplies(Long[] applyIds);

    /**
     * 审批通过报损单 —— <b>唯一的库存扣减动作</b>。
     *
     * <p>在一个事务内完成三件事：
     * <ol>
     *   <li>逐行把 {@code qty} 从该「图书 + 库位」的**可售库存**扣减</li>
     *   <li>逐行写 {@code biz_stock_flow}（{@code biz_type=6 报损}，含变动前后结存）</li>
     *   <li>单据置 {@code 2已通过}，记 {@code auditBy} / {@code auditTime} / {@code auditRemark}</li>
     * </ol>
     *
     * <p><b>扣的是可售库存、书直接出库，不转残损库存</b>。契约写的是「扣减库存并写
     * {@code biz_stock_flow}（{@code biz_type=6 报损}）」——只提一条流水；若转残损，
     * 可售减与残损加是两次库存变动，应当有两条流水（如调拨）。原型 A-18 的说法是
     * 「扣减库存至残损库存**或直接出库**」，本实现取**直接出库**一侧，
     * 与阶段三步骤③「盘亏只减可售」的既有口径一致。已登记 `plan/待确认项.md`。
     *
     * <p>⚠️ <b>权限由 Controller 的 {@code @PreAuthorize} 把关</b>
     * （{@code inventory:loss:approve}，仅店长），本方法不重复判角色 ——
     * 权限串与路径一一对应是本项目的接口风格，角色判断不走 service。
     *
     * @param applyId     单据ID
     * @param auditRemark 审批意见（选填）
     * @return 结果（含逐行扣减后的结存）
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；
     *         409 单据已被处理；422 某明细行报损数量超过当前可售库存
     */
    LossApproveResult approve(Long applyId, String auditRemark);

    /**
     * 驳回报损单（**不扣库存**）。
     *
     * @param applyId     单据ID
     * @param auditRemark 驳回原因（**必填**，由本方法判定 —— 一个 DTO 服务两个端点，
     *                    字段注解分不清这次是哪个端点）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 未填驳回原因；
     *         404 不存在；409 单据已被处理
     */
    int reject(Long applyId, String auditRemark);

    /**
     * 撤销报损申请（**申请人撤回自己的待审批单据**）。
     *
     * @param applyId 单据ID
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 403 非本人提交；
     *         404 不存在；409 单据已被审批
     */
    int revoke(Long applyId);

    /**
     * 读金额阈值（{@code sys_config.biz.loss.confirm.amount}，默认 500）。
     *
     * <p>NFR-M4 / R7：阈值必须运营可配，**不得写死在 Java 里**。
     * 走 {@code ISysConfigService}（带 Redis 缓存）。
     *
     * @return 阈值金额
     */
    BigDecimal confirmAmountThreshold();
}
