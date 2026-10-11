package com.tbgbook.bookstore.sale.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.CheckoutRequest;
import com.tbgbook.bookstore.sale.domain.CheckoutResult;
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.bookstore.member.service.IMemberGrowthService;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.bookstore.sale.domain.MemberBrief;
import com.tbgbook.bookstore.sale.domain.Order;
import com.tbgbook.bookstore.sale.domain.OrderHold;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.domain.Payment;
import com.tbgbook.bookstore.sale.domain.TrialRequest;
import com.tbgbook.bookstore.sale.domain.TrialResult;
import com.tbgbook.bookstore.sale.mapper.CouponMapper;
import com.tbgbook.bookstore.sale.mapper.CouponTemplateMapper;
import com.tbgbook.bookstore.sale.mapper.MemberReadMapper;
import com.tbgbook.bookstore.sale.mapper.OrderHoldMapper;
import com.tbgbook.bookstore.sale.mapper.OrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.OrderMapper;
import com.tbgbook.bookstore.sale.mapper.PaymentMapper;
import com.tbgbook.bookstore.sale.service.ICashierService;
import com.tbgbook.bookstore.sale.service.ICheckoutService;
import com.tbgbook.bookstore.sale.support.IdempotencySupport;
import com.tbgbook.bookstore.sale.support.StockDeductSupport;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 收银结算 Service 实现（阶段四步骤④）—— 本项目最核心的写接口。
 *
 * <p>六步全在同一事务内，任一步失败整体回滚。四处要紧的取舍：
 * <ol>
 *   <li><b>优惠计算复用 {@link ICashierService#trial}</b>，不另写一套：试算与结算的金额
 *       必须**逐分一致**，否则「试算说 150、结账收 152」是最难查也最伤客诉的 bug</li>
 *   <li><b>门店收银直接扣可售、不经过「锁定」</b>（Q6 已确认口径）：线下当场取书，
 *       锁定再扣减在同一事务里等价；「可售→锁定→出库」的两段式留给线上下单</li>
 *   <li><b>多库位贪心扣减</b>（可售多的库位先扣）：请求体没有库位字段（契约如此），
 *       而一本书可能散在多个库位 —— 只从一行扣会「明明有货却报库存不足」</li>
 *   <li><b>乐观锁冲突必须显式检查</b>：{@code Stock} 带 {@code @Version}，
 *       MP 在影响 0 行时**不抛异常**，不检查就会静默超卖（§4.2 规则 3）</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements ICheckoutService
{
    /** 订单号前缀（需求 §3.2：SO + yyyyMMddHHmmss + 4 位流水） */
    private static final String ORDER_NO_PREFIX = "SO";

    /** 积分与金额换算：100 积分 = 1 元 */
    private static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");

    private static final int SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final ICashierService cashierService;

    private final IdempotencySupport idempotency;

    private final BookMapper bookMapper;

    private final StockDeductSupport stockDeduct;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final PaymentMapper paymentMapper;

    private final OrderHoldMapper orderHoldMapper;

    private final CouponMapper couponMapper;

    private final CouponTemplateMapper couponTemplateMapper;

    private final IMemberBalanceService memberBalanceService;

    private final IMemberPointService memberPointService;

    private final IMemberGrowthService memberGrowthService;

    private final IMemberService memberService;

    private final MemberReadMapper memberReadMapper;

    private final ISysConfigService configService;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckoutResult checkout(CheckoutRequest request)
    {
        // ---------- 幂等：抢不到键就直接返回首次结果（或 409） ----------
        Object cached = idempotency.claim(request.getRequestId());
        if (cached instanceof CheckoutResult)
        {
            CheckoutResult first = (CheckoutResult) cached;
            first.setIdempotentHit(true);
            return first;
        }

        try
        {
            CheckoutResult result = doCheckout(request);
            idempotency.complete(request.getRequestId(), result);
            return result;
        }
        catch (RuntimeException e)
        {
            // 失败必须释放占位，否则客户端重试会被误判成「处理中」而永远拿不到结果
            idempotency.release(request.getRequestId());
            throw e;
        }
    }

    /** 真正的结算（六步） */
    private CheckoutResult doCheckout(CheckoutRequest request)
    {
        Long memberId = (request.getMemberId() == null) ? 0L : request.getMemberId();
        MemberBrief member = (memberId > 0) ? memberReadMapper.selectMemberByKeywordById(memberId) : null;
        if (memberId > 0 && member == null)
        {
            throw BizErrors.notFound("会员不存在（会员ID=" + memberId + "）");
        }

        // ---------- 用试算算出金额（与试算逐分一致） ----------
        TrialRequest trialReq = new TrialRequest();
        trialReq.setMemberId(memberId);
        trialReq.setCouponId(request.getCouponId());
        trialReq.setUsePoints(request.getUsePoints());
        List<TrialRequest.Line> trialLines = new ArrayList<>();
        for (CheckoutRequest.Item it : request.getItems())
        {
            TrialRequest.Line line = new TrialRequest.Line();
            line.setBookId(it.getBookId());
            line.setUsedBookId(it.getUsedBookId());
            line.setQty(it.getQty());
            trialLines.add(line);
        }
        trialReq.setItems(trialLines);
        TrialResult t = cashierService.trial(trialReq);

        // ---------- 折扣审批：触发时必须给审批人 ----------
        if (Objects.equals(t.getNeedApproval(), 1)
                && (request.getApprovalBy() == null || request.getApprovalBy().isBlank()))
        {
            throw BizErrors.ruleViolated("本单触发折扣审批（" + String.join("；", t.getApprovalReasons())
                    + "），必须提供 approvalBy（折扣审批人，角色权限要点 1：仅店长可审批）");
        }

        // ---------- 支付金额校验：多种方式之和必须等于应付 ----------
        BigDecimal paySum = BigDecimal.ZERO;
        for (CheckoutRequest.Pay p : request.getPayments())
        {
            if (p.getPayMethod() == null || p.getPayAmount() == null)
            {
                throw BizErrors.badRequest("支付方式与金额都不能为空");
            }
            paySum = paySum.add(p.getPayAmount());
        }
        if (paySum.setScale(SCALE, RoundingMode.HALF_UP).compareTo(t.getPayAmount()) != 0)
        {
            throw BizErrors.ruleViolated("多种支付方式金额之和（" + paySum + "）不等于应付金额（"
                    + t.getPayAmount() + "）");
        }

        // ---------- ① 先落订单头，拿到真实主键后再定订单号 ----------
        // ⚠️ 顺序不可颠倒：库存流水 / 明细 / 支付都要带订单号。
        // 若先用 selectNextOrderId() 的「预测值」生成单号再写库，实际主键与预测值不一致时，
        // 只能回改订单头，**已写入的 biz_stock_flow.biz_no 会留在错误单号上**（实测踩到：
        // 流水挂 SO...000672，订单实际是 SO...000773，对账直接对不上）。
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        // 先占位：order_no 是 NOT NULL，落库后用真实主键重算并回写
        order.setOrderNo("TMP");
        order.setOrderType(Order.TYPE_STORE);
        order.setMemberId(memberId);
        if (member != null)
        {
            order.setMemberNo(member.getMemberNo());
            order.setMemberName(member.getNickname());
            order.setMemberLevelId(member.getLevelId());
            order.setMemberDiscountRate(member.getDiscountRate());
        }
        order.setBookKind(t.getItems().size());
        int totalQty = 0;
        for (TrialResult.Line line : t.getItems())
        {
            totalQty += (line.getQty() == null ? 0 : line.getQty());
        }
        order.setTotalQty(totalQty);
        order.setTotalAmount(t.getTotalAmount());
        order.setSingleDiscount(t.getSingleDiscount());
        order.setMemberDiscount(t.getMemberDiscount());
        order.setCouponAmount(t.getCouponAmount());
        order.setPointAmount(t.getPointAmount());
        order.setDiscountTotal(t.getDiscountTotal());
        order.setPayAmount(t.getPayAmount());
        order.setPointUsed(t.getPointUsed());
        order.setPointGain(t.getPointGain());
        order.setCouponId(request.getCouponId() == null ? 0L : request.getCouponId());
        order.setHasSpecialPrice(t.getHasSpecialPrice());
        order.setNeedApproval(t.getNeedApproval());
        order.setApprovalBy(request.getApprovalBy());
        order.setApprovalTime(Objects.equals(t.getNeedApproval(), 1) ? now : null);
        order.setPayExpireTime(now.plusMinutes(payTimeoutMinutes()));
        order.setPickupCode(buildPickupCode());
        order.setHoldId(request.getHoldId() == null ? 0L : request.getHoldId());
        order.setCashierBy(SecurityUtils.getUsername());
        order.setIsRed(0);
        order.setIsSettled(0);
        order.setStatus(Order.STATUS_UNPAID);
        order.setPayTime(null);

        orderMapper.insert(order);
        String orderNo = buildOrderNo(order.getOrderId());
        Order noPatch = new Order();
        noPatch.setOrderId(order.getOrderId());
        noPatch.setOrderNo(orderNo);
        referenceGuard.requireAffected(orderMapper.updateById(noPatch), "销售订单");
        order.setOrderNo(orderNo);

        // ---------- ② 扣减库存（多库位贪心 + 乐观锁 + 写流水） ----------
        for (TrialResult.Line line : t.getItems())
        {
            stockDeduct.deduct(line.getBookId(), line.getQty(), orderNo, "收银销售出库");
        }

        // ---------- ③ 写订单明细 ----------
        for (TrialResult.Line line : t.getItems())
        {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getOrderId());
            item.setOrderNo(orderNo);
            item.setBookId(line.getBookId());
            item.setUsedBookId(line.getUsedBookId());
            item.setIsbn(line.getIsbn());
            item.setTitle(line.getTitle());
            item.setBookType(line.getBookType());
            item.setPriceType(line.getPriceType());
            item.setOriginPrice(line.getOriginPrice());
            item.setUnitPrice(line.getUnitPrice());
            item.setQty(line.getQty());
            item.setAmount(line.getAmount());
            item.setSingleDiscount(line.getSingleDiscount());
            item.setMemberDiscount(line.getMemberDiscount());
            item.setCouponShare(line.getCouponShare());
            item.setPointShare(line.getPointShare());
            item.setShareAmount(line.getShareAmount());
            // 成本快照：毛利核算用（下单时点）
            item.setCostPrice(costPriceOf(line.getBookId()));
            orderItemMapper.insert(item);
        }

        // ---------- ③ 写支付流水（组合支付逐笔落库） ----------
        boolean allPaid = true;
        for (CheckoutRequest.Pay p : request.getPayments())
        {
            int payStatus = resolvePayment(p, memberId, orderNo, now);
            allPaid = allPaid && (payStatus == Payment.STATUS_SUCCESS);

            Payment payment = new Payment();
            payment.setOrderId(order.getOrderId());
            payment.setOrderNo(orderNo);
            payment.setPayNo(buildPayNo(order.getOrderId(), p.getPayMethod()));
            payment.setPayMethod(p.getPayMethod());
            payment.setPayAmount(p.getPayAmount());
            payment.setPayStatus(payStatus);
            payment.setTradeNo(p.getTradeNo() == null ? "" : p.getTradeNo());
            payment.setPayTime(payStatus == Payment.STATUS_SUCCESS ? now : null);
            payment.setRefundAmount(BigDecimal.ZERO);
            paymentMapper.insert(payment);
        }

        // ---------- ④ 核销券 / 扣减积分 ----------
        if (request.getCouponId() != null && request.getCouponId() > 0)
        {
            Coupon coupon = referenceGuard.requireExists(couponMapper, request.getCouponId(), "优惠券");
            Coupon patch = new Coupon();
            patch.setCouponId(coupon.getCouponId());
            patch.setStatus(Coupon.STATUS_USED);
            patch.setUseOrderId(order.getOrderId());
            patch.setUseTime(now);
            referenceGuard.requireAffected(couponMapper.updateById(patch), "优惠券");

            CouponTemplate tp = couponTemplateMapper.selectById(coupon.getTemplateId());
            if (tp != null)
            {
                CouponTemplate tpPatch = new CouponTemplate();
                tpPatch.setTemplateId(tp.getTemplateId());
                tpPatch.setUsedQty(nzi(tp.getUsedQty()) + 1);
                couponTemplateMapper.updateById(tpPatch);
            }
        }

        // ---------- ⑤⑥ 会员侧结果：积分 / 成长值 / 消费汇总 ----------
        // 收编（阶段五步骤②）：积分与成长值改写会员域 Service —— 积分按批次先到期先消耗、
        // 成长值在会员域内完成「达标自动升级」，销售域不再直接写 biz_member。
        if (member != null)
        {
            if (t.getPointUsed() > 0)
            {
                memberPointService.deductPoints(memberId, t.getPointUsed(), orderNo, "收银结算抵扣");
            }
            if (t.getPointGain() > 0)
            {
                memberPointService.awardPoints(memberId, t.getPointGain(), orderNo, "收银结算获得");
            }
            // 成长值 = 实付金额（消费 1 元 = 1 成长值），**只增不减**
            int gain = t.getPayAmount().setScale(0, RoundingMode.DOWN).intValue();
            if (gain > 0)
            {
                memberGrowthService.addGrowth(memberId, 1, gain, orderNo, "消费累加");
            }
            memberService.addConsume(memberId, t.getPayAmount());
        }

        // ---------- 挂单转入：标记为已取单结算 ----------
        if (request.getHoldId() != null && request.getHoldId() > 0)
        {
            OrderHold hold = orderHoldMapper.selectById(request.getHoldId());
            if (hold != null && Objects.equals(hold.getStatus(), OrderHold.STATUS_HOLDING))
            {
                OrderHold hp = new OrderHold();
                hp.setHoldId(hold.getHoldId());
                hp.setStatus(OrderHold.STATUS_RESUMED);
                hp.setResumeTime(now);
                orderHoldMapper.updateById(hp);
            }
        }

        // ---------- 单据置终态 ----------
        Order finalPatch = new Order();
        finalPatch.setOrderId(order.getOrderId());
        if (allPaid)
        {
            // 门店收银当场取书 → 直接完成（待取货留给线上下单）
            finalPatch.setStatus(Order.STATUS_FINISHED);
            finalPatch.setPayTime(now);
            finalPatch.setFinishTime(now);
        }
        else
        {
            finalPatch.setStatus(Order.STATUS_UNPAID);
        }
        referenceGuard.requireAffected(orderMapper.updateById(finalPatch), "销售订单");

        CheckoutResult result = new CheckoutResult();
        result.setOrderId(order.getOrderId());
        result.setOrderNo(orderNo);
        result.setTotalAmount(t.getTotalAmount());
        result.setSingleDiscount(t.getSingleDiscount());
        result.setMemberDiscount(t.getMemberDiscount());
        result.setCouponAmount(t.getCouponAmount());
        result.setPointAmount(t.getPointAmount());
        result.setDiscountTotal(t.getDiscountTotal());
        result.setPayAmount(t.getPayAmount());
        result.setPointUsed(t.getPointUsed());
        result.setPointGain(t.getPointGain());
        result.setPickupCode(order.getPickupCode());
        result.setStatus(finalPatch.getStatus());
        result.setNeedApproval(t.getNeedApproval());
        result.setItems(t.getItems());
        result.setCreateTime(now);
        result.setIdempotentHit(false);
        return result;
    }

    // ==================================================================
    // 库存扣减（多库位贪心 + 乐观锁 + 流水）
    // ==================================================================

    // ==================================================================
    // 支付方式处理
    // ==================================================================

    /**
     * 处理一笔支付，返回其支付状态。
     *
     * <p>按已确认口径（Q2）：
     * <ul>
     *   <li>{@code 1现金} / {@code 5校园卡} → 直接置成功（校园卡本期不做特殊逻辑）</li>
     *   <li>{@code 4储值余额} → **条件更新扣减**（余额不足影响 0 行 → 422），
     *       按当前本金:赠送金比例分摊扣减并各写一条流水（保持比例稳定，
     *       以便退款时「按原比例分别回退」精确成立，§3.2 价格规则 6）</li>
     *   <li>{@code 2微信} / {@code 3支付宝} → **传了 tradeNo 视为扫码支付成功**，
     *       否则置「待支付」等回调（单机无商户号，回调接口留待阶段九）</li>
     * </ul>
     */
    private int resolvePayment(CheckoutRequest.Pay p, Long memberId, String orderNo, LocalDateTime now)
    {
        int method = p.getPayMethod();
        if (method == Payment.METHOD_CASH || method == Payment.METHOD_CAMPUS_CARD)
        {
            return Payment.STATUS_SUCCESS;
        }
        if (method == Payment.METHOD_BALANCE)
        {
            if (memberId == null || memberId <= 0)
            {
                throw BizErrors.ruleViolated("散客不能使用储值余额支付");
            }
            BigDecimal amount = p.getPayAmount();
            // 按当前比例分摊到本金/赠送金
            BigDecimal[] ratio = balanceRatio(memberId);
            BigDecimal principalPart = amount.multiply(ratio[0]).setScale(SCALE, RoundingMode.DOWN);
            BigDecimal giftPart = amount.subtract(principalPart);

            // 收编（阶段五步骤②b）：扣减、余额汇总与流水全部走会员域储值服务
            memberBalanceService.deductBalance(memberId, principalPart, giftPart, amount, orderNo,
                    "收银消费扣本金", "收银消费扣赠送金");
            return Payment.STATUS_SUCCESS;
        }
        // 微信 / 支付宝：单机无商户号 → 有交易号即视为已支付，否则待支付
        boolean paid = p.getTradeNo() != null && !p.getTradeNo().isBlank();
        return paid ? Payment.STATUS_SUCCESS : Payment.STATUS_UNPAID;
    }

    /** 当前「本金 : 赠送金」比例；余额为 0 时按全本金处理 */
    private BigDecimal[] balanceRatio(Long memberId)
    {
        MemberBrief m = memberReadMapper.selectMemberByKeywordById(memberId);
        BigDecimal total = (m == null || m.getBalanceTotal() == null) ? BigDecimal.ZERO : m.getBalanceTotal();
        if (total.signum() <= 0)
        {
            return new BigDecimal[] { BigDecimal.ONE, BigDecimal.ZERO };
        }
        BigDecimal principal = BigDecimal.ZERO;
        try
        {
            principal = memberReadMapper.selectPrincipalBalance(memberId);
        }
        catch (Exception ignored)
        {
            principal = total;
        }
        return new BigDecimal[] { principal.divide(total, 6, RoundingMode.HALF_UP), BigDecimal.ZERO };
    }

    // ==================================================================
    // 内部小工具
    // ==================================================================

    /** 订单号：{@code SO} + yyyyMMddHHmmss + 4 位流水（需求 §3.2） */
    private String buildOrderNo(Long orderId)
    {
        return ORDER_NO_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", orderId % 10000);
    }

    /** 支付流水号：{@code PAY} + yyyyMMdd + 6 位（订单ID 后 6 位）+ 支付方式 */
    private String buildPayNo(Long orderId, Integer payMethod)
    {
        return "PAY" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", orderId % 1000000) + payMethod;
    }

    /** 取货码：6 位数字（供退换货核验与线上下单取货） */
    private String buildPickupCode()
    {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1000000));
    }

    /** 成交时点成本价快照（毛利核算用）；图书不存在或未核算过成本时为 0 */
    private BigDecimal costPriceOf(Long bookId)
    {
        Book book = bookMapper.selectById(bookId);
        return (book == null || book.getCostPrice() == null) ? BigDecimal.ZERO : book.getCostPrice();
    }

    private int payTimeoutMinutes()
    {
        String raw = configService.selectConfigByKey("biz.order.pay.timeout.minutes");
        try
        {
            return (raw == null || raw.isBlank()) ? 15 : Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return 15;
        }
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
