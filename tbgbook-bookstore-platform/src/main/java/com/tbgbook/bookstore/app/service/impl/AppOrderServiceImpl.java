package com.tbgbook.bookstore.app.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.tbgbook.bookstore.app.domain.request.CancelOrderRequest;
import com.tbgbook.bookstore.app.domain.request.CartItemRequest;
import com.tbgbook.bookstore.app.domain.request.CartTrialRequest;
import com.tbgbook.bookstore.app.domain.request.OrderSubmitRequest;
import com.tbgbook.bookstore.app.domain.request.PayRequest;
import com.tbgbook.bookstore.app.domain.request.RefundApplyRequest;
import com.tbgbook.bookstore.app.domain.request.RefundItemRequest;
import com.tbgbook.bookstore.app.mapper.AppTradeMapper;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.service.IAppOrderService;
import com.tbgbook.bookstore.app.support.AppQrCode;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.bookstore.member.service.IMemberLevelService;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.domain.Order;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.domain.Payment;
import com.tbgbook.bookstore.sale.domain.ReturnOrder;
import com.tbgbook.bookstore.sale.domain.ReturnOrderItem;
import com.tbgbook.bookstore.sale.mapper.CouponMapper;
import com.tbgbook.bookstore.sale.mapper.CouponTemplateMapper;
import com.tbgbook.bookstore.sale.mapper.OrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.OrderMapper;
import com.tbgbook.bookstore.sale.mapper.PaymentMapper;
import com.tbgbook.bookstore.sale.mapper.ReturnOrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.ReturnOrderMapper;
import com.tbgbook.bookstore.usedbook.domain.UsedBook;
import com.tbgbook.bookstore.usedbook.mapper.UsedBookMapper;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * 会员端交易 Service 实现。
 *
 * <p>下单：Redis requestId 幂等 → 试算 → 锁库存 → 建单；支付：储值真实扣款，
 * 微信/支付宝返回 mock 支付参数（无支付回调端点，见计划 Ruling）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AppOrderServiceImpl implements IAppOrderService
{
    private static final long STORE_ID = 1L;

    private static final int PAY_TIMEOUT_MINUTES = 15;

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AppTradeMapper tradeMapper;

    private final IMemberService memberService;

    private final IMemberLevelService memberLevelService;

    private final IMemberBalanceService memberBalanceService;

    private final IMemberPointService memberPointService;

    private final BookMapper bookMapper;

    private final UsedBookMapper usedBookMapper;

    private final CouponMapper couponMapper;

    private final CouponTemplateMapper couponTemplateMapper;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final PaymentMapper paymentMapper;

    private final ReturnOrderMapper returnOrderMapper;

    private final ReturnOrderItemMapper returnOrderItemMapper;

    private final RedissonClient redissonClient;

    private final ISysConfigService configService;

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> trial(CartTrialRequest request)
    {
        Pricing pricing = price(request.getItems(), request.getCouponId(), request.getUsePoints());
        return pricingData(pricing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(OrderSubmitRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        if (!StringUtils.hasText(request.getRequestId()))
        {
            throw BizErrors.badRequest("requestId 不能为空");
        }
        String key = "app:order:req:" + memberId + ":" + request.getRequestId().trim();
        RBucket<String> bucket = redissonClient.getBucket(key);
        String cached = bucket.get();
        if (StringUtils.hasText(cached))
        {
            if ("PROCESSING".equals(cached))
            {
                throw BizErrors.conflict("requestId 重复且首次请求仍在处理中");
            }
            try
            {
                return objectMapper.readValue(cached,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            }
            catch (Exception e)
            {
                bucket.delete();
            }
        }
        if (!bucket.trySet("PROCESSING", 10, java.util.concurrent.TimeUnit.MINUTES))
        {
            throw BizErrors.conflict("requestId 重复且首次请求仍在处理中");
        }

        try
        {
            Pricing pricing = price(request.getItems(), request.getCouponId(), request.getUsePoints());
            Member member = memberService.selectMemberById(memberId);
            String orderNo = "SO" + LocalDateTime.now().format(NO_FMT)
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            String pickupCode = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
            LocalDateTime now = LocalDateTime.now();
            Order order = new Order();
            order.setStoreId(STORE_ID);
            order.setOrderNo(orderNo);
            order.setOrderType(2);
            order.setMemberId(memberId);
            order.setMemberNo(member.getMemberNo());
            order.setMemberName(member.getNickname());
            order.setMemberLevelId(member.getLevelId());
            order.setMemberDiscountRate(pricing.level.getDiscountRate());
            order.setBookKind(pricing.lines.size());
            order.setTotalQty(pricing.lines.stream().mapToInt(line -> line.qty).sum());
            order.setTotalAmount(pricing.totalAmount);
            order.setSingleDiscount(BigDecimal.ZERO);
            order.setMemberDiscount(pricing.memberDiscount);
            order.setCouponAmount(pricing.couponAmount);
            order.setPointAmount(pricing.pointAmount);
            order.setDiscountTotal(pricing.memberDiscount.add(pricing.couponAmount)
                    .add(pricing.pointAmount));
            order.setPayAmount(pricing.payAmount);
            order.setPointUsed(pricing.usePoints);
            order.setPointGain(pricing.pointGain);
            order.setCouponId(pricing.couponId);
            order.setHasSpecialPrice(0);
            order.setNeedApproval(0);
            order.setStatus(Order.STATUS_UNPAID);
            order.setPayExpireTime(now.plusMinutes(PAY_TIMEOUT_MINUTES));
            order.setPickupCode(pickupCode);
            order.setIsRed(0);
            order.setIsSettled(0);
            order.setRemark(request.getRemark() == null ? "" : request.getRemark());
            order.setCreateBy("app");
            order.setCreateTime(now);
            orderMapper.insert(order);

            BigDecimal eligible = pricing.totalAmount.subtract(pricing.memberDiscount);
            for (Line line : pricing.lines)
            {
                BigDecimal lineEligible = line.amount.subtract(line.memberDiscount);
                BigDecimal couponShare = share(pricing.couponAmount, lineEligible, eligible);
                BigDecimal pointShare = share(pricing.pointAmount, lineEligible, eligible);
                BigDecimal shareAmount = lineEligible.subtract(couponShare).subtract(pointShare)
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                OrderItem item = new OrderItem();
                item.setOrderId(order.getOrderId());
                item.setOrderNo(orderNo);
                item.setBookId(line.book == null ? 0L : line.book.getBookId());
                item.setUsedBookId(line.used == null ? 0L : line.used.getUsedBookId());
                item.setIsbn(line.isbn);
                item.setTitle(line.title);
                item.setBookType(line.used == null ? 1 : 2);
                item.setConditionGrade(line.used == null ? "" : line.used.getConditionGradeName());
                item.setPriceType(1);
                item.setOriginPrice(line.originPrice);
                item.setUnitPrice(line.unitPrice);
                item.setQty(line.qty);
                item.setAmount(line.amount);
                item.setSingleDiscount(BigDecimal.ZERO);
                item.setMemberDiscount(line.memberDiscount);
                item.setCouponShare(couponShare);
                item.setPointShare(pointShare);
                item.setShareAmount(shareAmount);
                item.setCostPrice(line.costPrice == null ? BigDecimal.ZERO : line.costPrice);
                item.setCreateTime(now);
                orderItemMapper.insert(item);
                lockStock(line, order.getOrderId(), orderNo);
            }

            if (pricing.coupon != null)
            {
                pricing.coupon.setStatus(2);
                pricing.coupon.setUseOrderId(order.getOrderId());
                pricing.coupon.setUseTime(now);
                couponMapper.updateById(pricing.coupon);
            }
            if (pricing.usePoints > 0)
            {
                memberPointService.deductPoints(memberId, pricing.usePoints, orderNo, "订单抵扣");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", order.getOrderId());
            data.put("orderNo", orderNo);
            data.put("payAmount", pricing.payAmount);
            data.put("payExpireTime", order.getPayExpireTime());
            data.put("pickupCode", pickupCode);
            data.put("status", Order.STATUS_UNPAID);
            bucket.set(toJson(data), Duration.ofHours(24));
            return data;
        }
        catch (RuntimeException e)
        {
            bucket.delete();
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pay(Long orderId, PayRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        Order order = requireOwnedOrder(orderId, memberId);
        if (!Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus()))
        {
            throw BizErrors.conflict("订单已支付或已取消");
        }
        if (order.getPayExpireTime() != null && LocalDateTime.now().isAfter(order.getPayExpireTime()))
        {
            throw BizErrors.conflict("订单已超时");
        }
        if (request.getPayAmount() == null
                || request.getPayAmount().compareTo(order.getPayAmount()) != 0)
        {
            throw BizErrors.ruleViolated("支付金额与订单应付金额不一致");
        }
        int method = request.getPayMethod() == null ? 2 : request.getPayMethod();
        String payNo = "PAY" + LocalDateTime.now().format(NO_FMT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        Payment payment = new Payment();
        payment.setStoreId(STORE_ID);
        payment.setOrderId(orderId);
        payment.setOrderNo(order.getOrderNo());
        payment.setPayNo(payNo);
        payment.setPayMethod(method);
        payment.setPayAmount(order.getPayAmount());
        payment.setPayStatus(method == 4 ? 2 : 1);
        payment.setTradeNo("");
        payment.setPayTime(method == 4 ? LocalDateTime.now() : null);
        payment.setCreateTime(LocalDateTime.now());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payNo", payNo);
        if (method == 4)
        {
            MemberBalance balance = memberBalanceService.selectMemberBalanceList(
                    balanceQuery(memberId)).stream().findFirst().orElse(null);
            BigDecimal total = balance == null ? BigDecimal.ZERO
                    : balance.getPrincipalBalance().add(balance.getGiftBalance());
            if (total.compareTo(order.getPayAmount()) < 0)
            {
                throw BizErrors.ruleViolated("储值余额不足");
            }
            BigDecimal giftPart = balance.getGiftBalance().min(order.getPayAmount());
            BigDecimal principalPart = order.getPayAmount().subtract(giftPart);
            memberBalanceService.deductBalance(memberId, principalPart, giftPart, order.getPayAmount(),
                    order.getOrderNo(), "订单消费-本金", "订单消费-赠送金");
            paymentMapper.insert(payment);
            order.setStatus(Order.STATUS_PAID);
            order.setPayTime(LocalDateTime.now());
            orderMapper.updateById(order);
            memberService.addConsume(memberId, order.getPayAmount());
            if (order.getPointGain() != null && order.getPointGain() > 0)
            {
                memberPointService.awardPoints(memberId, order.getPointGain(), order.getOrderNo(), "消费获得");
            }
            data.put("balanceAvailable", true);
            data.put("mock", false);
        }
        else
        {
            paymentMapper.insert(payment);
            Map<String, Object> wx = new LinkedHashMap<>();
            wx.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
            wx.put("nonceStr", java.util.UUID.randomUUID().toString().replace("-", ""));
            wx.put("package", "prepay_id=MOCK" + payNo);
            wx.put("signType", "RSA");
            wx.put("paySign", "MOCK");
            wx.put("mock", true);
            data.put("wxPayParams", wx);
            data.put("balanceAvailable", balanceEnough(memberId, order.getPayAmount()));
            data.put("mock", true);
        }
        return data;
    }

    @Override
    public Map<String, Object> orderList(Integer status, int pageNum, int pageSize)
    {
        Long memberId = AppContext.requireMemberId();
        int size = Math.max(1, Math.min(pageSize, 100));
        int offset = (Math.max(1, pageNum) - 1) * size;
        List<Map<String, Object>> list = tradeMapper.orderList(memberId, status, offset, size);
        for (Map<String, Object> order : list)
        {
            List<Map<String, Object>> items = tradeMapper.orderItems(
                    Long.valueOf(String.valueOf(order.get("orderId"))));
            order.put("items", items.size() > 2 ? items.subList(0, 2) : items);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", tradeMapper.countOrderList(memberId, status));
        return data;
    }

    @Override
    public Map<String, Object> orderDetail(Long orderId)
    {
        Long memberId = AppContext.requireMemberId();
        Map<String, Object> order = tradeMapper.orderDetail(orderId, memberId);
        if (order == null)
        {
            Order exists = orderMapper.selectById(orderId);
            if (exists == null)
            {
                throw BizErrors.notFound("订单", orderId);
            }
            throw BizErrors.forbidden("订单不属于当前会员");
        }
        order.put("items", tradeMapper.orderItems(orderId));
        order.put("payments", tradeMapper.orderPayments(orderId));
        String pickupCode = String.valueOf(order.get("pickupCode") == null ? "" : order.get("pickupCode"));
        order.put("pickupQrUrl", pickupCode.isEmpty() ? "" : AppQrCode.dataUri("PICKUP:" + pickupCode));
        LocalDateTime base = toLocalDateTime(order.get("finishTime") != null
                ? order.get("finishTime") : order.get("payTime"));
        int returnDays = intConfig("biz.return.days", 7);
        boolean refundable = Integer.valueOf(Order.STATUS_FINISHED).equals(toInt(order.get("status")))
                && base != null && !LocalDateTime.now().isAfter(base.plusDays(returnDays));
        order.put("refundable", refundable);
        order.put("refundDeadline", base == null ? null : base.plusDays(returnDays));
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(Long orderId, CancelOrderRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        Order order = requireOwnedOrder(orderId, memberId);
        cancelInternal(order, request == null ? "" : request.getReason());
        return Map.of("orderId", orderId, "status", Order.STATUS_CANCELLED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refundApply(Long orderId, RefundApplyRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        Order order = requireOwnedOrder(orderId, memberId);
        if (!Integer.valueOf(Order.STATUS_FINISHED).equals(order.getStatus()))
        {
            throw BizErrors.ruleViolated("订单状态不可退（未支付/已取消/未完成）");
        }
        LocalDateTime base = order.getFinishTime() == null ? order.getPayTime() : order.getFinishTime();
        int returnDays = intConfig("biz.return.days", 7);
        if (base == null || LocalDateTime.now().isAfter(base.plusDays(returnDays)))
        {
            throw BizErrors.ruleViolated("超过退货时限（" + returnDays + " 个自然日）");
        }
        List<Map<String, Object>> items = tradeMapper.orderItems(orderId);
        Map<Long, Map<String, Object>> itemMap = new LinkedHashMap<>();
        for (Map<String, Object> item : items)
        {
            itemMap.put(toLong(item.get("itemId")), item);
        }
        String returnNo = "RT" + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        BigDecimal refund = BigDecimal.ZERO;
        int totalQty = 0;
        for (RefundItemRequest line : request.getItems())
        {
            Map<String, Object> origin = itemMap.get(line.getOrderItemId());
            if (origin == null)
            {
                throw BizErrors.ruleViolated("退货明细不属于该订单");
            }
            if (Integer.valueOf(2).equals(toInt(origin.get("bookType"))))
            {
                throw BizErrors.ruleViolated("二手书不支持无理由退货");
            }
            int buyQty = toInt(origin.get("qty"));
            if (line.getReturnQty() == null || line.getReturnQty() < 1 || line.getReturnQty() > buyQty)
            {
                throw BizErrors.ruleViolated("退货数量非法");
            }
            refund = refund.add(decimal(origin.get("shareAmount"))
                    .multiply(BigDecimal.valueOf(line.getReturnQty()))
                    .divide(BigDecimal.valueOf(buyQty), 2, RoundingMode.HALF_UP));
            totalQty += line.getReturnQty();
        }
        ReturnOrder returnOrder = new ReturnOrder();
        returnOrder.setStoreId(STORE_ID);
        returnOrder.setReturnNo(returnNo);
        returnOrder.setOriginOrderId(orderId);
        returnOrder.setOriginOrderNo(order.getOrderNo());
        returnOrder.setMemberId(memberId);
        returnOrder.setReturnType(2);
        returnOrder.setIsNoOrder(0);
        returnOrder.setTotalQty(totalQty);
        returnOrder.setRefundAmount(refund);
        returnOrder.setRefundPoint(0);
        returnOrder.setRefundCoupon(0);
        returnOrder.setReason(request.getReason());
        returnOrder.setBookCondition(request.getItems().stream().anyMatch(i -> toInt(i.getBookCondition()) == 2) ? 2 : 1);
        returnOrder.setStatus(1);
        returnOrder.setCreateTime(LocalDateTime.now());
        returnOrderMapper.insert(returnOrder);
        for (RefundItemRequest line : request.getItems())
        {
            Map<String, Object> origin = itemMap.get(line.getOrderItemId());
            ReturnOrderItem item = new ReturnOrderItem();
            item.setReturnId(returnOrder.getReturnId());
            item.setOrderItemId(line.getOrderItemId());
            item.setBookId(toLong(origin.get("bookId")));
            item.setUsedBookId(toLong(origin.get("usedBookId")));
            item.setIsbn(String.valueOf(origin.get("isbn")));
            item.setTitle(String.valueOf(origin.get("title")));
            item.setBuyQty(toInt(origin.get("qty")));
            item.setReturnQty(line.getReturnQty());
            item.setUnitPrice(decimal(origin.get("unitPrice")));
            item.setRefundAmount(decimal(origin.get("shareAmount"))
                    .multiply(BigDecimal.valueOf(line.getReturnQty()))
                    .divide(BigDecimal.valueOf(toInt(origin.get("qty"))), 2, RoundingMode.HALF_UP));
            item.setBookCondition(line.getBookCondition() == null ? 1 : line.getBookCondition());
            item.setStockTo(1);
            item.setCreateTime(LocalDateTime.now());
            returnOrderItemMapper.insert(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("returnId", returnOrder.getReturnId());
        data.put("returnNo", returnNo);
        data.put("estimatedRefund", refund);
        data.put("status", 1);
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelExpired(Long orderId)
    {
        Order order = orderMapper.selectById(orderId);
        if (order != null && Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus()))
        {
            cancelInternal(order, "支付超时自动取消");
        }
    }

    private void cancelInternal(Order order, String reason)
    {
        if (!Integer.valueOf(Order.STATUS_UNPAID).equals(order.getStatus()))
        {
            throw BizErrors.conflict("订单已支付或已完成，无法取消");
        }
        List<Map<String, Object>> items = tradeMapper.orderItems(order.getOrderId());
        for (Map<String, Object> item : items)
        {
            int qty = toInt(item.get("qty"));
            Long usedBookId = toLong(item.get("usedBookId"));
            if (usedBookId > 0)
            {
                tradeMapper.releaseUsedBook(usedBookId, order.getOrderId());
            }
            else
            {
                Long bookId = toLong(item.get("bookId"));
                Map<String, Object> stock = tradeMapper.selectStockForRelease(bookId);
                if (stock != null)
                {
                    int locked = toInt(stock.get("qtyLocked"));
                    int availableBefore = toInt(stock.get("qtyAvailable"));
                    tradeMapper.releaseStock(toLong(stock.get("stockId")), qty);
                    tradeMapper.insertStockFlow(STORE_ID, bookId, toLong(stock.get("locationId")), 1, 12,
                            order.getOrderNo(), qty, availableBefore, availableBefore + qty, "app",
                            LocalDateTime.now(), "订单取消释放");
                }
            }
        }
        if (order.getCouponId() != null && order.getCouponId() > 0)
        {
            Coupon coupon = couponMapper.selectById(order.getCouponId());
            if (coupon != null && Integer.valueOf(2).equals(coupon.getStatus())
                    && order.getOrderId().equals(coupon.getUseOrderId()))
            {
                coupon.setStatus(1);
                coupon.setUseOrderId(0L);
                coupon.setUseTime(null);
                couponMapper.updateById(coupon);
            }
        }
        if (order.getPointUsed() != null && order.getPointUsed() > 0)
        {
            memberPointService.returnPoints(order.getMemberId(), order.getPointUsed(),
                    order.getOrderNo(), "订单取消返还");
        }
        order.setStatus(Order.STATUS_CANCELLED);
        order.setRemark((order.getRemark() == null ? "" : order.getRemark() + "；")
                + (StringUtils.hasText(reason) ? reason : "用户取消"));
        orderMapper.updateById(order);
    }

    private Order requireOwnedOrder(Long orderId, Long memberId)
    {
        Order order = orderMapper.selectById(orderId);
        if (order == null)
        {
            throw BizErrors.notFound("订单", orderId);
        }
        if (!memberId.equals(order.getMemberId()))
        {
            throw BizErrors.forbidden("订单不属于当前会员");
        }
        return order;
    }

    private void lockStock(Line line, Long orderId, String orderNo)
    {
        if (line.used != null)
        {
            if (tradeMapper.lockUsedBook(line.used.getUsedBookId(), orderId) == 0)
            {
                throw BizErrors.ruleViolated("二手书副本已被他人锁定");
            }
            return;
        }
        Map<String, Object> stock = tradeMapper.selectStockForLock(line.book.getBookId());
        if (stock == null || toInt(stock.get("qtyAvailable")) < line.qty)
        {
            throw BizErrors.ruleViolated("部分商品库存不足");
        }
        int before = toInt(stock.get("qtyAvailable"));
        if (tradeMapper.lockStock(toLong(stock.get("stockId")), line.qty) == 0)
        {
            throw BizErrors.ruleViolated("部分商品库存不足");
        }
        tradeMapper.insertStockFlow(STORE_ID, line.book.getBookId(), toLong(stock.get("locationId")),
                1, 12, orderNo, -line.qty, before, before - line.qty, "app",
                LocalDateTime.now(), "线上下单锁库存");
    }

    private Pricing price(List<CartItemRequest> items, Long couponId, Integer usePoints)
    {
        if (items == null || items.isEmpty())
        {
            throw BizErrors.badRequest("商品明细不能为空");
        }
        Long memberId = AppContext.requireMemberId();
        Member member = memberService.selectMemberById(memberId);
        MemberLevel level = memberLevelService.getById(member.getLevelId());
        if (level == null)
        {
            throw BizErrors.serverError("会员等级配置缺失");
        }
        BigDecimal discountRate = level.getDiscountRate() == null
                ? BigDecimal.ONE : level.getDiscountRate();
        List<Line> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal memberDiscount = BigDecimal.ZERO;
        for (CartItemRequest item : items)
        {
            if (item == null || item.getQty() == null || item.getQty() < 1)
            {
                throw BizErrors.badRequest("购买数量至少为 1");
            }
            boolean used = item.getUsedBookId() != null && item.getUsedBookId() > 0;
            boolean newBook = item.getBookId() != null && item.getBookId() > 0;
            if (used == newBook)
            {
                throw BizErrors.badRequest("bookId 与 usedBookId 必须二选一");
            }
            if (used)
            {
                UsedBook usedBook = usedBookMapper.selectById(item.getUsedBookId());
                if (usedBook == null || !Integer.valueOf(1).equals(usedBook.getStockStatus()))
                {
                    throw BizErrors.ruleViolated("二手书副本不存在或已售出");
                }
                BigDecimal origin = usedBook.getOriginPrice() == null ? BigDecimal.ZERO : usedBook.getOriginPrice();
                BigDecimal unit = usedBook.getUsedPrice() == null ? BigDecimal.ZERO : usedBook.getUsedPrice();
                BigDecimal amount = unit.multiply(BigDecimal.valueOf(item.getQty()));
                lines.add(new Line(null, usedBook, usedBook.getIsbn(), usedBook.getTitle(), item.getQty(),
                        origin, unit, BigDecimal.ZERO, amount, usedBook.getCostPrice()));
                total = total.add(origin.multiply(BigDecimal.valueOf(item.getQty())));
            }
            else
            {
                Book book = bookMapper.selectById(item.getBookId());
                if (book == null || !"0".equals(book.getDelFlag())
                        || !Integer.valueOf(1).equals(book.getPublishStatus())
                        || Integer.valueOf(4).equals(book.getStatus()))
                {
                    throw BizErrors.ruleViolated("图书已下架或停售");
                }
                BigDecimal origin = book.getPrice() == null ? BigDecimal.ZERO : book.getPrice();
                BigDecimal unit = book.getMemberPrice() != null && book.getMemberPrice().signum() > 0
                        ? book.getMemberPrice()
                        : origin.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal amount = unit.multiply(BigDecimal.valueOf(item.getQty()));
                BigDecimal lineDiscount = origin.subtract(unit)
                        .multiply(BigDecimal.valueOf(item.getQty())).max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);
                lines.add(new Line(book, null, book.getIsbn(), book.getTitle(), item.getQty(),
                        origin, unit, lineDiscount, amount, book.getCostPrice()));
                total = total.add(origin.multiply(BigDecimal.valueOf(item.getQty())));
                memberDiscount = memberDiscount.add(lineDiscount);
            }
        }
        BigDecimal eligible = total.subtract(memberDiscount).max(BigDecimal.ZERO);
        Coupon coupon = null;
        BigDecimal couponAmount = BigDecimal.ZERO;
        if (couponId != null && couponId > 0)
        {
            coupon = couponMapper.selectById(couponId);
            couponAmount = couponAmount(coupon, memberId, eligible);
        }
        BigDecimal afterCoupon = eligible.subtract(couponAmount).max(BigDecimal.ZERO);
        int maxPoints = afterCoupon.multiply(BigDecimal.valueOf(20))
                .setScale(0, RoundingMode.DOWN).intValue();
        maxPoints = Math.min(maxPoints, member.getPoints() == null ? 0 : member.getPoints());
        int points = usePoints == null ? 0 : usePoints;
        if (points < 0 || points > maxPoints)
        {
            throw BizErrors.ruleViolated("积分抵扣超过订单金额的 20% 或积分不足");
        }
        BigDecimal pointAmount = BigDecimal.valueOf(points).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal payAmount = afterCoupon.subtract(pointAmount).max(BigDecimal.ZERO);
        int pointGain = payAmount.setScale(0, RoundingMode.DOWN).intValue();
        return new Pricing(lines, level, total.setScale(2, RoundingMode.HALF_UP),
                memberDiscount.setScale(2, RoundingMode.HALF_UP), couponAmount.setScale(2, RoundingMode.HALF_UP),
                pointAmount, payAmount.setScale(2, RoundingMode.HALF_UP), points, pointGain,
                coupon == null ? null : couponId, coupon, maxPoints);
    }

    private BigDecimal couponAmount(Coupon coupon, Long memberId, BigDecimal eligible)
    {
        if (coupon == null || !memberId.equals(coupon.getMemberId())
                || !Integer.valueOf(1).equals(coupon.getStatus()))
        {
            throw BizErrors.ruleViolated("优惠券不可用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidStart() != null && now.isBefore(coupon.getValidStart())
                || coupon.getValidEnd() != null && now.isAfter(coupon.getValidEnd()))
        {
            throw BizErrors.ruleViolated("优惠券已过期");
        }
        if (coupon.getThresholdAmount() != null && eligible.compareTo(coupon.getThresholdAmount()) < 0)
        {
            throw BizErrors.ruleViolated("未达到优惠券使用门槛");
        }
        CouponTemplate template = coupon.getTemplateId() == null ? null
                : couponTemplateMapper.selectById(coupon.getTemplateId());
        if (template != null && Integer.valueOf(2).equals(template.getCouponType())
                && template.getDiscountRate() != null)
        {
            return eligible.multiply(BigDecimal.ONE.subtract(template.getDiscountRate()))
                    .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal face = coupon.getFaceValue() == null ? BigDecimal.ZERO : coupon.getFaceValue();
        return face.min(eligible).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> pricingData(Pricing pricing)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalAmount", pricing.totalAmount);
        data.put("singleDiscount", BigDecimal.ZERO);
        data.put("memberDiscount", pricing.memberDiscount);
        data.put("couponAmount", pricing.couponAmount);
        data.put("pointAmount", pricing.pointAmount);
        data.put("payAmount", pricing.payAmount);
        data.put("pointGain", pricing.pointGain);
        data.put("availableCoupons", availableCoupons(pricing));
        data.put("maxUsablePoints", pricing.maxPoints);
        data.put("changes", List.of());
        return data;
    }

    private List<Map<String, Object>> availableCoupons(Pricing pricing)
    {
        Long memberId = AppContext.requireMemberId();
        List<Coupon> coupons = couponMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getMemberId, memberId).eq(Coupon::getStatus, 1));
        BigDecimal eligible = pricing.totalAmount.subtract(pricing.memberDiscount);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Coupon coupon : coupons)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("couponId", coupon.getCouponId());
            item.put("couponName", coupon.getCouponName());
            try
            {
                BigDecimal amount = couponAmount(coupon, memberId, eligible);
                item.put("usable", true);
                item.put("discountAmount", amount);
                item.put("reason", null);
            }
            catch (com.tbgbook.common.exception.ServiceException e)
            {
                item.put("usable", false);
                item.put("reason", e.getMessage());
            }
            result.add(item);
        }
        return result;
    }

    private boolean balanceEnough(Long memberId, BigDecimal amount)
    {
        MemberBalance balance = memberBalanceService.selectMemberBalanceList(balanceQuery(memberId))
                .stream().findFirst().orElse(null);
        return balance != null && balance.getPrincipalBalance().add(balance.getGiftBalance())
                .compareTo(amount) >= 0;
    }

    private MemberBalance balanceQuery(Long memberId)
    {
        MemberBalance query = new MemberBalance();
        query.setMemberId(memberId);
        return query;
    }

    private BigDecimal share(BigDecimal totalShare, BigDecimal line, BigDecimal eligible)
    {
        if (totalShare == null || totalShare.signum() == 0 || eligible == null || eligible.signum() == 0)
        {
            return BigDecimal.ZERO;
        }
        return totalShare.multiply(line).divide(eligible, 2, RoundingMode.HALF_UP);
    }

    private String toJson(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception e)
        {
            return "{}";
        }
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (!StringUtils.hasText(raw))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

    private LocalDateTime toLocalDateTime(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof LocalDateTime time)
        {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp)
        {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    private int toInt(Object value)
    {
        if (value == null)
        {
            return 0;
        }
        if (value instanceof Boolean bool)
        {
            return bool ? 1 : 0;
        }
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return 0L;
        }
        if (value instanceof Boolean bool)
        {
            return bool ? 1L : 0L;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private BigDecimal decimal(Object value)
    {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private record Line(Book book, UsedBook used, String isbn, String title, int qty,
            BigDecimal originPrice, BigDecimal unitPrice, BigDecimal memberDiscount,
            BigDecimal amount, BigDecimal costPrice)
    {
    }

    private record Pricing(List<Line> lines, MemberLevel level, BigDecimal totalAmount,
            BigDecimal memberDiscount, BigDecimal couponAmount, BigDecimal pointAmount,
            BigDecimal payAmount, int usePoints, int pointGain, Long couponId, Coupon coupon,
            int maxPoints)
    {
    }
}
