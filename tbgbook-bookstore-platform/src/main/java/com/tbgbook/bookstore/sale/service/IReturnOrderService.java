package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.ReturnNoOrderRequest;
import com.tbgbook.bookstore.sale.domain.ReturnNoOrderResult;
import com.tbgbook.bookstore.sale.domain.ReturnOrder;

/**
 * 退货单 Service（A-31 退货处理，FR-4.3.5）。
 *
 * <p><b>两条路径</b>：
 * <ul>
 *   <li><b>有原单退货</b>（{@link #addReturnOrder}，{@code originOrderId > 0}）→ **直接完成**：
 *       校验时限（{@code biz.return.days} 默认 7 天）与可退数量，回退库存、按支付方式分流退款</li>
 *   <li><b>无单退货</b>（{@link #addNoOrderReturn}）→ {@code isNoOrder=1}、{@code status=1待审核}，
 *       库存与退款等 {@link #approve} 通过后才执行（§4.3 规则 4）</li>
 * </ul>
 *
 * <p><b>退款金额一律服务端计算</b>（NFR-S9）：按原明细的**分摊后实付金额**
 * （{@code biz_order_item.share_amount}）比例算，与毛利口径一致。
 */
public interface IReturnOrderService extends IService<ReturnOrder>
{
    List<ReturnOrder> selectReturnOrderList(ReturnOrder order);

    ReturnOrder selectReturnOrderById(Long returnId);

    /**
     * 有原单退货（**直接完成**）。
     *
     * @param order 退货单（须带 {@code originOrderId} 与 {@code items}）
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException
     *         404 原订单/图书不存在；422 超过退货时限 / 退货数量超过可退 / 有损却选回退可售 / 明细为空
     */
    int addReturnOrder(ReturnOrder order);

    /**
     * 无单退货（**待审核**，需店长审批）。
     *
     * @param request 无单退货请求
     * @return 退货单ID / 单号 / 状态
     * @throws com.tbgbook.common.exception.ServiceException 422 有损却选回退可售 / 明细为空
     */
    ReturnNoOrderResult addNoOrderReturn(ReturnNoOrderRequest request);

    int updateReturnOrder(ReturnOrder order);

    /** 删除退货单（**仅待审核可删**；已完成的是库存与资金的凭证）。 */
    int removeReturnOrders(Long[] returnIds);

    /**
     * 审批通过无单退货：回退库存、返还积分与券中**可恢复部分**。
     *
     * <p>⚠️ **已过期券不返还、已清零积分不返还**（§4.3 规则 3）。
     *
     * @throws com.tbgbook.common.exception.ServiceException
     *         404 不存在；422 该单不需要审批（有原单退货直接完成）；409 已被处理
     */
    int approve(Long returnId, String auditRemark);

    /** 驳回报损…退货申请（**不退库存、不返款**）。 */
    int reject(Long returnId, String auditRemark);

    /**
     * 取父单并断言其为「1待审核」——**明细的增删改只允许发生在待审核的单据上**。
     *
     * <p>有原单退货在提交时就已完成（{@code status=2}），其明细是库存与资金的凭证；
     * 而「待审核」只可能是无单退货，故本方法实际只放行无单退货。
     *
     * @param action 被拒绝时的动作名（如「新增明细」），用于拼错误文案
     * @throws com.tbgbook.common.exception.ServiceException 404 不存在；422 当前状态不允许该动作
     */
    ReturnOrder requireEditable(Long returnId, String action);

    /**
     * 重算父单的 {@code total_qty} 与退款额在明细间的分摊（明细增删改后调用）。
     *
     * <p>⚠️ **不重算退款总额** —— 那是收银员填、店长审批的数；明细的增删改只改变它在各行的分摊。
     */
    void recalcTotals(Long returnId);
}
