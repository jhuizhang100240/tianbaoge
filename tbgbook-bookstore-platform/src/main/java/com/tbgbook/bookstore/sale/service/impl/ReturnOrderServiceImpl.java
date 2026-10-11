package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.Order;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.bookstore.sale.domain.Payment;
import com.tbgbook.bookstore.sale.domain.ReturnNoOrderRequest;
import com.tbgbook.bookstore.sale.domain.ReturnNoOrderResult;
import com.tbgbook.bookstore.sale.domain.ReturnOrder;
import com.tbgbook.bookstore.sale.domain.ReturnOrderItem;
import com.tbgbook.bookstore.sale.mapper.CouponMapper;
import com.tbgbook.bookstore.sale.mapper.MemberReadMapper;
import com.tbgbook.bookstore.sale.mapper.MemberWriteMapper;
import com.tbgbook.bookstore.sale.mapper.OrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.OrderMapper;
import com.tbgbook.bookstore.sale.mapper.PaymentMapper;
import com.tbgbook.bookstore.sale.mapper.ReturnOrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.ReturnOrderMapper;
import com.tbgbook.bookstore.sale.service.IReturnOrderService;
import com.tbgbook.bookstore.sale.support.StockDeductSupport;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 退货单 Service 实现（阶段四步骤⑤）。
 *
 * <p><b>本类最要紧的是「退款资金按原支付方式分流」</b>（§3.2 价格规则 6，2026-09-21 已确认）：
 * <ol>
 *   <li><b>储值余额</b>支付的 → 退回**原储值账户**，本金与赠送金**按原比例分别回退**
 *       （依据 §4.4 规则 5「储值余额不可提现」）</li>
 *   <li><b>微信 / 支付宝</b>支付的 → **原路退回**（单机无商户号，此处只落退款金额与状态，真实退回留阶段九）</li>
 *   <li><b>现金 / 校园卡</b>支付的 → 收银台**手工登记**，计入当班日结（只记退款金额，不动余额）</li>
 *   <li><b>混合支付</b> → 按**各支付方式实付金额逐笔原路回退**，不合并、不冲抵</li>
 * </ol>
 * 退款额按各笔支付的实付金额**比例分摊**，最后一行用「总额减前面各行」兜底，避免差一分钱。
 *
 * <p><b>退款金额服务端计算</b>（NFR-S9）：按原明细的**分摊后实付金额**比例算。
 * 反例：让前端传退款额 —— 那是把「算错钱」的权力交给前端。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class ReturnOrderServiceImpl extends ServiceImpl<ReturnOrderMapper, ReturnOrder>
        implements IReturnOrderService
{
    /** 退货单号前缀（需求未定义，本次自定）：TH + yyyyMMdd + 6 位流水 */
    private static final String RETURN_NO_PREFIX = "TH";

    /** 退货时限的 `sys_config` 键（§3.2 价格规则 4，默认 7 个自然日） */
    private static final String CFG_RETURN_DAYS = "biz.return.days";

    private static final int SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final PaymentMapper paymentMapper;

    private final BookMapper bookMapper;

    private final CouponMapper couponMapper;

    private final MemberWriteMapper memberWriteMapper;

    private final MemberReadMapper memberReadMapper;

    private final IMemberPointService memberPointService;

    private final ReturnOrderItemMapper itemMapper;

    private final StockDeductSupport stockDeduct;

    private final ISysConfigService configService;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<ReturnOrder> selectReturnOrderList(ReturnOrder order)
    {
        return baseMapper.selectReturnOrderList(order);
    }

    @Override
    public ReturnOrder selectReturnOrderById(Long returnId)
    {
        ReturnOrder order = referenceGuard.requireExists(baseMapper, returnId, "退货单");
        ReturnOrderItem query = new ReturnOrderItem();
        query.setReturnId(returnId);
        order.setItems(itemMapper.selectReturnOrderItemList(query));
        return order;
    }

    // ==================================================================
    // 有原单退货（直接完成）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addReturnOrder(ReturnOrder order)
    {
        requireItems(order.getItems());
        if (order.getOriginOrderId() == null || order.getOriginOrderId() <= 0)
        {
            throw BizErrors.badRequest("有原单退货必须指定原订单；无单退货请走 /sales/return/no-order");
        }
        Order origin = referenceGuard.requireExists(orderMapper, order.getOriginOrderId(), "原订单");

        // 时限：结算后 biz.return.days（默认 7）个自然日内（§3.2 价格规则 4）
        int days = cfgInt(CFG_RETURN_DAYS, 7);
        LocalDateTime base = (origin.getCreateTime() == null) ? LocalDateTime.now() : origin.getCreateTime();
        if (base.plusDays(days).isBefore(LocalDateTime.now()))
        {
            throw BizErrors.ruleViolated("订单「" + origin.getOrderNo() + "」已超过退货时限（"
                    + days + " 个自然日），不能退货");
        }

        // 服务端接管
        order.setReturnId(null);
        order.setReturnNo(null);
        order.setOriginOrderNo(origin.getOrderNo());
        order.setMemberId(origin.getMemberId());
        order.setIsNoOrder(0);
        order.setStatus(ReturnOrder.STATUS_DONE);
        order.setReturnBy(SecurityUtils.getUsername());
        order.setReturnTime(LocalDateTime.now());
        order.setAuditBy(null);
        order.setAuditTime(null);
        order.setAuditRemark(null);

        prepareItems(order, origin, true);

        Long predictedId = baseMapper.selectNextReturnId();
        order.setReturnNo(buildReturnNo(predictedId == null ? 1L : predictedId));
        int rows = baseMapper.insert(order);
        correctNo(order);
        persistItems(order);

        // 库存去向 + 退款分流 + 积分/券返还
        applyStockAndRefund(order, origin, true);
        return rows;
    }

    // ==================================================================
    // 无单退货（待审核）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnNoOrderResult addNoOrderReturn(ReturnNoOrderRequest request)
    {
        if (request.getItems() == null || request.getItems().isEmpty())
        {
            throw BizErrors.ruleViolated("退货明细不能为空");
        }
        ReturnOrder order = new ReturnOrder();
        order.setMemberId(request.getMemberId() == null ? 0L : request.getMemberId());
        order.setReason(request.getReason());
        order.setRefundAmount(nz(request.getRefundAmount()));
        order.setOriginOrderId(0L);
        order.setOriginOrderNo("");
        order.setIsNoOrder(1);
        order.setStatus(ReturnOrder.STATUS_PENDING);
        order.setReturnType(ReturnOrder.TYPE_SINGLE);
        order.setBookCondition(ReturnOrderItem.CONDITION_GOOD);

        List<ReturnOrderItem> items = new ArrayList<>();
        int totalQty = 0;
        for (ReturnNoOrderRequest.Item req : request.getItems())
        {
            ReturnOrderItem it = new ReturnOrderItem();
            it.setBookId(req.getBookId());
            it.setUsedBookId(req.getUsedBookId() == null ? 0L : req.getUsedBookId());
            it.setReturnQty(req.getReturnQty());
            it.setBookCondition(req.getBookCondition());
            it.setStockTo(req.getStockTo());
            it.setOrderItemId(0L);
            requireConditionMatchesStockTo(it);
            Book book = referenceGuard.requireExists(bookMapper, req.getBookId(), "图书");
            it.setIsbn(book.getIsbn());
            it.setTitle(book.getTitle());
            it.setUnitPrice(nz(book.getPrice()));
            it.setBuyQty(0);
            // 无单退货无原单可依据 → 退款额按各行**定价**比例分摊收银员填的总额
            it.setRefundAmount(BigDecimal.ZERO);
            items.add(it);
            totalQty += nzi(req.getReturnQty());
        }
        order.setItems(items);
        order.setTotalQty(totalQty);

        // 退款额按定价比例分摊到行（总由收银员填、店长审批）
        allocateByPrice(items, order.getRefundAmount());

        Long predictedId = baseMapper.selectNextReturnId();
        order.setReturnNo(buildReturnNo(predictedId == null ? 1L : predictedId));
        baseMapper.insert(order);
        correctNo(order);
        persistItems(order);

        ReturnNoOrderResult result = new ReturnNoOrderResult();
        result.setReturnId(order.getReturnId());
        result.setReturnNo(order.getReturnNo());
        result.setStatus(ReturnOrder.STATUS_PENDING);
        return result;
    }

    // ==================================================================
    // 审批
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(Long returnId, String auditRemark)
    {
        ReturnOrder order = referenceGuard.requireExists(baseMapper, returnId, "退货单");
        if (!Objects.equals(order.getIsNoOrder(), 1))
        {
            throw BizErrors.ruleViolated("该退货单有原订单、已在提交时直接完成，不需要审批");
        }
        if (!Objects.equals(order.getStatus(), ReturnOrder.STATUS_PENDING))
        {
            throw BizErrors.conflict("退货单「" + order.getReturnNo() + "」已被处理");
        }

        ReturnOrderItem query = new ReturnOrderItem();
        query.setReturnId(returnId);
        List<ReturnOrderItem> items = itemMapper.selectReturnOrderItemList(query);

        LocalDateTime now = LocalDateTime.now();
        // 通过后才回退库存（§4.3 规则 4：无单退货需店长审批）
        for (ReturnOrderItem it : items)
        {
            if (Objects.equals(it.getStockTo(), ReturnOrderItem.STOCK_TO_AVAILABLE)
                    && it.getBookId() != null && it.getBookId() > 0 && nzi(it.getReturnQty()) > 0)
            {
                stockDeduct.giveBack(it.getBookId(), it.getReturnQty(), order.getReturnNo(), "无单退货入库");
            }
        }
        // 无单退货没有原订单可分流 → 退款只能记在单据上（收银台据此手工登记）
        ReturnOrder patch = new ReturnOrder();
        patch.setReturnId(returnId);
        patch.setStatus(ReturnOrder.STATUS_DONE);
        patch.setAuditBy(SecurityUtils.getUsername());
        patch.setAuditTime(now);
        patch.setAuditRemark(auditRemark);
        patch.setReturnBy(SecurityUtils.getUsername());
        patch.setReturnTime(now);
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "退货单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(Long returnId, String auditRemark)
    {
        ReturnOrder order = referenceGuard.requireExists(baseMapper, returnId, "退货单");
        if (!Objects.equals(order.getStatus(), ReturnOrder.STATUS_PENDING))
        {
            throw BizErrors.conflict("退货单「" + order.getReturnNo() + "」已被处理");
        }
        if (auditRemark == null || auditRemark.isBlank())
        {
            throw BizErrors.badRequest("驳回原因不能为空");
        }
        ReturnOrder patch = new ReturnOrder();
        patch.setReturnId(returnId);
        patch.setStatus(ReturnOrder.STATUS_REJECTED);
        patch.setAuditBy(SecurityUtils.getUsername());
        patch.setAuditTime(LocalDateTime.now());
        patch.setAuditRemark(auditRemark);
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "退货单");
        return rows;
    }

    // ==================================================================
    // 改 / 删
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateReturnOrder(ReturnOrder order)
    {
        if (order.getReturnId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的退货单");
        }
        ReturnOrder current = referenceGuard.requireExists(baseMapper, order.getReturnId(), "退货单");
        requirePending(current, "修改");
        // 金额与状态都不接受写入（退款额服务端算，NFR-S9）
        order.setReturnNo(null);
        order.setOriginOrderId(null);
        order.setOriginOrderNo(null);
        order.setMemberId(null);
        order.setRefundAmount(null);
        order.setRefundPoint(null);
        order.setRefundCoupon(null);
        order.setStatus(null);
        order.setTotalQty(null);
        order.setItems(null);
        int rows = baseMapper.updateById(order);
        referenceGuard.requireAffected(rows, "退货单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeReturnOrders(Long[] returnIds)
    {
        List<Long> idList = (returnIds == null) ? Collections.emptyList() : Arrays.asList(returnIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "退货单");
        for (Serializable id : ids)
        {
            ReturnOrder one = referenceGuard.requireExists(baseMapper, id, "退货单");
            requirePending(one, "删除");
        }
        itemMapper.deleteByReturnIds(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 明细服务的挂点（退货单明细的增删改后回算父单）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public ReturnOrder requireEditable(Long returnId, String action)
    {
        ReturnOrder order = referenceGuard.requireExists(baseMapper, returnId, "退货单");
        requirePending(order, action);
        return order;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalcTotals(Long returnId)
    {
        ReturnOrder order = referenceGuard.requireExists(baseMapper, returnId, "退货单");
        ReturnOrderItem query = new ReturnOrderItem();
        query.setReturnId(returnId);
        List<ReturnOrderItem> items = itemMapper.selectReturnOrderItemList(query);

        int totalQty = 0;
        for (ReturnOrderItem it : items)
        {
            totalQty += nzi(it.getReturnQty());
        }

        // 退款额在明细间重分摊。只对无单退货做：有原单退货（status=2）的每行退款额
        // 是按原明细的**分摊后实付金额**算出来的，不能拿定价重算 —— 那会改变退款口径。
        if (Objects.equals(order.getIsNoOrder(), 1) && !items.isEmpty())
        {
            allocateByPrice(items, nz(order.getRefundAmount()));
            for (ReturnOrderItem it : items)
            {
                ReturnOrderItem patch = new ReturnOrderItem();
                patch.setItemId(it.getItemId());
                patch.setRefundAmount(it.getRefundAmount());
                itemMapper.updateById(patch);
            }
        }

        ReturnOrder patch = new ReturnOrder();
        patch.setReturnId(returnId);
        patch.setTotalQty(totalQty);
        baseMapper.updateById(patch);
    }

    // ==================================================================
    // 内部：明细准备与校验
    // ==================================================================

    private void requireItems(List<ReturnOrderItem> items)
    {
        if (items == null || items.isEmpty())
        {
            throw BizErrors.ruleViolated("退货单至少要有一行明细");
        }
    }

    /**
     * 有原单退货：逐行校验可退数量、计算退款额、判定库存去向。
     *
     * <p><b>退款额按原明细的分摊后实付金额比例算</b>（{@code share_amount}）——
     * 与毛利口径一致：顾客实付了多少、退多少，而不是按定价退。
     */
    private void prepareItems(ReturnOrder order, Order origin, boolean checkQty)
    {
        int totalQty = 0;
        for (ReturnOrderItem it : order.getItems())
        {
            if (it.getBookId() == null)
            {
                throw BizErrors.badRequest("退货明细的图书不能为空");
            }
            if (it.getReturnQty() == null || it.getReturnQty() <= 0)
            {
                throw BizErrors.ruleViolated("退货数量必须大于 0");
            }
            requireConditionMatchesStockTo(it);

            // 原订单里必须有这本书
            OrderItem originItem = findOriginItem(origin.getOrderId(), it.getBookId());
            if (originItem == null)
            {
                throw BizErrors.ruleViolated("图书「" + it.getTitle() + "」不在原订单里，不能退");
            }
            it.setOrderItemId(originItem.getItemId());
            it.setIsbn(originItem.getIsbn());
            it.setTitle(originItem.getTitle());
            it.setUnitPrice(originItem.getUnitPrice());
            it.setBuyQty(originItem.getQty());

            if (checkQty)
            {
                int already = itemMapper.sumReturnedQty(originItem.getItemId());
                int canReturn = nzi(originItem.getQty()) - already;
                if (it.getReturnQty() > canReturn)
                {
                    throw BizErrors.ruleViolated("「" + originItem.getTitle() + "」原购 "
                            + nzi(originItem.getQty()) + " 册、已退 " + already + " 册，本次最多还能退 "
                            + Math.max(canReturn, 0) + " 册");
                }
            }

            // 退款额 = 原明细分摊后实付金额 ÷ 购买数量 × 退货数量（四舍五入到分）
            BigDecimal share = nz(originItem.getShareAmount());
            BigDecimal perUnit = (nzi(originItem.getQty()) <= 0) ? share
                    : share.divide(BigDecimal.valueOf(nzi(originItem.getQty())), 6, RoundingMode.HALF_UP);
            it.setRefundAmount(perUnit.multiply(BigDecimal.valueOf(it.getReturnQty()))
                    .setScale(SCALE, RoundingMode.HALF_UP));
            totalQty += it.getReturnQty();
        }
        order.setTotalQty(totalQty);
        order.setReturnType(Objects.equals(totalQty, sumOriginQty(origin.getOrderId()))
                ? ReturnOrder.TYPE_WHOLE : ReturnOrder.TYPE_SINGLE);

        BigDecimal sum = BigDecimal.ZERO;
        for (ReturnOrderItem it : order.getItems())
        {
            sum = sum.add(nz(it.getRefundAmount()));
        }
        order.setRefundAmount(sum.setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * 有原单退货的落地动作：**回退库存 → 按支付方式分流退款 → 返还积分与券**。
     *
     * @param isNoOrderApproved 无单退货审批通过时也走这里（但退款无原单可分流，只落单据）
     */
    private void applyStockAndRefund(ReturnOrder order, Order origin, boolean routeRefund)
    {
        ReturnOrderItem query = new ReturnOrderItem();
        query.setReturnId(order.getReturnId());
        List<ReturnOrderItem> items = itemMapper.selectReturnOrderItemList(query);

        for (ReturnOrderItem it : items)
        {
            // 只有「无损 + 回退可售」才回可售；转报损的不回（残次品不能重新上架）
            if (Objects.equals(it.getStockTo(), ReturnOrderItem.STOCK_TO_AVAILABLE)
                    && Objects.equals(it.getBookCondition(), ReturnOrderItem.CONDITION_GOOD)
                    && it.getBookId() != null && it.getBookId() > 0)
            {
                stockDeduct.giveBack(it.getBookId(), nzi(it.getReturnQty()), order.getReturnNo(), "销售退货入库");
            }
        }

        if (!routeRefund || origin == null)
        {
            return;
        }
        routeRefundByPayment(origin, order);
        refundPointAndCoupon(origin, order);
    }

    /**
     * **退款资金按原支付方式分流**（§3.2 价格规则 6）。
     *
     * <p>按各笔支付的实付金额**比例分摊**本次退款额（混合支付逐笔回退，不合并、不冲抵）：
     * 储值 → 退回原储值账户（本金/赠送金按原比例分别回退 + 两条流水）；
     * 微信/支付宝 → 记退款金额并置「已退款」（单机无商户号，真实退回留阶段九）；
     * 现金/校园卡 → 只记退款金额（收银台手工登记，计入当班日结）。
     */
    private void routeRefundByPayment(Order origin, ReturnOrder order)
    {
        List<Payment> payments = paymentMapper.selectPaymentList(paymentQuery(origin.getOrderId()));
        if (payments.isEmpty())
        {
            return;
        }
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (Payment p : payments)
        {
            totalPaid = totalPaid.add(nz(p.getPayAmount()));
        }
        BigDecimal left = nz(order.getRefundAmount());

        for (int i = 0; i < payments.size(); i++)
        {
            Payment p = payments.get(i);
            boolean last = (i == payments.size() - 1);
            BigDecimal part = last ? left
                    : (totalPaid.signum() == 0 ? BigDecimal.ZERO
                            : order.getRefundAmount().multiply(nz(p.getPayAmount()))
                                    .divide(totalPaid, SCALE, RoundingMode.HALF_UP));
            left = left.subtract(part);
            if (part.signum() <= 0)
            {
                continue;
            }

            if (Objects.equals(p.getPayMethod(), Payment.METHOD_BALANCE) && origin.getMemberId() != null
                    && origin.getMemberId() > 0)
            {
                // 储值：退回原储值账户，本金/赠送金按原比例分别回退
                BigDecimal[] ratio = balanceRatio(origin.getMemberId());
                BigDecimal principalPart = part.multiply(ratio[0]).setScale(SCALE, RoundingMode.DOWN);
                BigDecimal giftPart = part.subtract(principalPart);
                memberWriteMapper.refundBalance(origin.getMemberId(), principalPart, giftPart);
                memberWriteMapper.syncBalanceSummary(origin.getMemberId());
                if (principalPart.signum() > 0)
                {
                    memberWriteMapper.insertStoredValueLog(origin.getMemberId(), 5, principalPart,
                            order.getReturnNo(), "销售退货返本金");
                }
                if (giftPart.signum() > 0)
                {
                    memberWriteMapper.insertStoredValueLog(origin.getMemberId(), 6, giftPart,
                            order.getReturnNo(), "销售退货返赠送金");
                }
            }
            else if (Objects.equals(p.getPayMethod(), Payment.METHOD_WECHAT)
                    || Objects.equals(p.getPayMethod(), Payment.METHOD_ALIPAY))
            {
                // 原路退回：记退款金额并置「已退款」（真实退回留阶段九联调）
                Payment patch = new Payment();
                patch.setPaymentId(p.getPaymentId());
                patch.setRefundAmount(nz(p.getRefundAmount()).add(part).setScale(SCALE, RoundingMode.HALF_UP));
                patch.setRefundTime(LocalDateTime.now());
                patch.setPayStatus(Payment.STATUS_REFUNDED);
                paymentMapper.updateById(patch);
            }
            else
            {
                // 现金 / 校园卡：只记退款金额，收银台手工登记并计入当班日结
                Payment patch = new Payment();
                patch.setPaymentId(p.getPaymentId());
                patch.setRefundAmount(nz(p.getRefundAmount()).add(part).setScale(SCALE, RoundingMode.HALF_UP));
                patch.setRefundTime(LocalDateTime.now());
                paymentMapper.updateById(patch);
            }
        }
    }

    /**
     * 返还积分与券中**可恢复部分**（§4.3 规则 3）。
     *
     * <p>⚠️ **已过期券不返还**（{@code refundCoupon=0}）、**已清零积分不返还**（{@code refundPoint=0}）：
     * <ul>
     *   <li>券：核销时置为「已使用」，此处若券**未过失效时间**则置回「未使用」并清核销信息；
     *       已过期则不动</li>
     *   <li>积分：按退款额占实付额的比例返还 {@code pointUsed}；若会员当前可用积分为 0
     *       （契约口径的「已清零」）则不返还</li>
     * </ul>
     */
    private void refundPointAndCoupon(Order origin, ReturnOrder order)
    {
        int refundPoint = 0;
        int refundCoupon = 0;

        if (origin.getMemberId() != null && origin.getMemberId() > 0)
        {
            BigDecimal payAmount = nz(origin.getPayAmount());
            if (nzi(origin.getPointUsed()) > 0 && payAmount.signum() > 0)
            {
                int memberPoints = memberPoints(origin.getMemberId());
                if (memberPoints > 0)
                {
                    refundPoint = nz(order.getRefundAmount())
                            .multiply(BigDecimal.valueOf(nzi(origin.getPointUsed())))
                            .divide(payAmount, 0, RoundingMode.DOWN).intValue();
                    refundPoint = Math.min(refundPoint, nzi(origin.getPointUsed()));
                }
                // memberPoints == 0 → 契约口径的「已清零积分不返还」
            }
            if (refundPoint > 0)
            {
                // 收编（阶段五步骤②）：返还积分走会员域 Service（写批次与流水）
                memberPointService.returnPoints(origin.getMemberId(), refundPoint,
                        order.getReturnNo(), "销售退货返还");
            }

            if (origin.getCouponId() != null && origin.getCouponId() > 0)
            {
                Coupon coupon = couponMapper.selectById(origin.getCouponId());
                boolean expired = coupon != null && coupon.getValidEnd() != null
                        && coupon.getValidEnd().isBefore(LocalDateTime.now());
                if (coupon != null && !expired)
                {
                    Coupon patch = new Coupon();
                    patch.setCouponId(coupon.getCouponId());
                    patch.setStatus(Coupon.STATUS_UNUSED);
                    patch.setUseOrderId(0L);
                    couponMapper.updateById(patch);
                    refundCoupon = 1;
                }
                // 已过期 → 不返还（refundCoupon 保持 0）
            }
        }

        ReturnOrder patch = new ReturnOrder();
        patch.setReturnId(order.getReturnId());
        patch.setRefundPoint(refundPoint);
        patch.setRefundCoupon(refundCoupon);
        baseMapper.updateById(patch);
        order.setRefundPoint(refundPoint);
        order.setRefundCoupon(refundCoupon);
    }

    // ==================================================================
    // 内部：小工具
    // ==================================================================

    /** 有损的书**不能**回退可售 —— 残次品重新上架会被卖出去（契约错误码原文） */
    private void requireConditionMatchesStockTo(ReturnOrderItem it)
    {
        if (Objects.equals(it.getBookCondition(), ReturnOrderItem.CONDITION_DAMAGED)
                && Objects.equals(it.getStockTo(), ReturnOrderItem.STOCK_TO_AVAILABLE))
        {
            throw BizErrors.ruleViolated("图书状况为「有损」时，库存去向不能是「回退可售」——"
                    + "残次品重新上架会被卖出去；请改为「转报损」");
        }
    }

    private OrderItem findOriginItem(Long orderId, Long bookId)
    {
        OrderItem query = new OrderItem();
        query.setOrderId(orderId);
        query.setBookId(bookId);
        List<OrderItem> list = orderItemMapper.selectOrderItemList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    private int sumOriginQty(Long orderId)
    {
        OrderItem query = new OrderItem();
        query.setOrderId(orderId);
        int sum = 0;
        for (OrderItem it : orderItemMapper.selectOrderItemList(query))
        {
            sum += nzi(it.getQty());
        }
        return sum;
    }

    /** 无单退货：退款额按各行定价比例分摊（无原单可依据，故按定价近似） */
    private void allocateByPrice(List<ReturnOrderItem> items, BigDecimal total)
    {
        BigDecimal base = BigDecimal.ZERO;
        for (ReturnOrderItem it : items)
        {
            base = base.add(nz(it.getUnitPrice()).multiply(BigDecimal.valueOf(nzi(it.getReturnQty()))));
        }
        BigDecimal left = nz(total);
        for (int i = 0; i < items.size(); i++)
        {
            ReturnOrderItem it = items.get(i);
            boolean last = (i == items.size() - 1);
            BigDecimal line = nz(it.getUnitPrice()).multiply(BigDecimal.valueOf(nzi(it.getReturnQty())));
            BigDecimal part = last ? left
                    : (base.signum() == 0 ? BigDecimal.ZERO
                            : nz(total).multiply(line).divide(base, SCALE, RoundingMode.HALF_UP));
            left = left.subtract(part);
            it.setRefundAmount(part);
        }
    }

    private Payment paymentQuery(Long orderId)
    {
        Payment q = new Payment();
        q.setOrderId(orderId);
        return q;
    }

    private BigDecimal[] balanceRatio(Long memberId)
    {
        BigDecimal principal;
        try
        {
            principal = principalOf(memberId);
        }
        catch (Exception ignored)
        {
            principal = BigDecimal.ZERO;
        }
        BigDecimal total = totalBalanceOf(memberId);
        if (total.signum() <= 0)
        {
            return new BigDecimal[] { BigDecimal.ONE, BigDecimal.ZERO };
        }
        return new BigDecimal[] { principal.divide(total, 6, RoundingMode.HALF_UP), BigDecimal.ZERO };
    }

    private BigDecimal principalOf(Long memberId)
    {
        return memberReadMapper.selectPrincipalBalance(memberId);
    }

    private BigDecimal totalBalanceOf(Long memberId)
    {
        com.tbgbook.bookstore.sale.domain.MemberBrief m =
                memberReadMapper.selectMemberByKeywordById(memberId);
        return (m == null || m.getBalanceTotal() == null) ? BigDecimal.ZERO : m.getBalanceTotal();
    }

    private int memberPoints(Long memberId)
    {
        com.tbgbook.bookstore.sale.domain.MemberBrief m =
                memberReadMapper.selectMemberByKeywordById(memberId);
        return (m == null || m.getPoints() == null) ? 0 : m.getPoints();
    }

    private void requirePending(ReturnOrder order, String action)
    {
        if (!Objects.equals(order.getStatus(), ReturnOrder.STATUS_PENDING))
        {
            throw BizErrors.ruleViolated("退货单「" + order.getReturnNo() + "」当前为「"
                    + statusName(order.getStatus()) + "」，只有「1待审核」的可以" + action);
        }
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case ReturnOrder.STATUS_PENDING -> "待审核";
            case ReturnOrder.STATUS_DONE -> "已完成";
            case ReturnOrder.STATUS_REJECTED -> "已驳回";
            default -> "未知";
        };
    }

    private void persistItems(ReturnOrder order)
    {
        for (ReturnOrderItem it : order.getItems())
        {
            it.setReturnId(order.getReturnId());
            it.setItemId(null);
            itemMapper.insert(it);
        }
    }

    private void correctNo(ReturnOrder order)
    {
        String actual = buildReturnNo(order.getReturnId());
        if (!actual.equals(order.getReturnNo()))
        {
            ReturnOrder patch = new ReturnOrder();
            patch.setReturnId(order.getReturnId());
            patch.setReturnNo(actual);
            baseMapper.updateById(patch);
            order.setReturnNo(actual);
        }
    }

    private String buildReturnNo(Long returnId)
    {
        return RETURN_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", returnId);
    }

    private int cfgInt(String key, int fallback)
    {
        String raw = configService.selectConfigByKey(key);
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
