package com.tbgbook.bookstore.sale.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.Order;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.domain.Payment;
import com.tbgbook.bookstore.sale.mapper.OrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.OrderMapper;
import com.tbgbook.bookstore.sale.mapper.PaymentMapper;
import com.tbgbook.bookstore.sale.service.IOrderItemService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.bookstore.sale.domain.CheckoutRequest;
import com.tbgbook.bookstore.sale.domain.OrderExportRequest;
import com.tbgbook.bookstore.sale.domain.OrderExportResult;
import com.tbgbook.bookstore.sale.domain.OrderPickupResult;
import com.tbgbook.bookstore.sale.domain.OrderRedResult;
import com.tbgbook.bookstore.sale.mapper.ExportTaskMapper;
import com.tbgbook.bookstore.sale.mapper.MemberWriteMapper;
import com.tbgbook.bookstore.sale.service.ICheckoutService;
import com.tbgbook.bookstore.sale.support.StockDeductSupport;
import com.tbgbook.bookstore.sale.service.IOrderService;

import lombok.RequiredArgsConstructor;

/**
 * 销售订单 Service 实现（阶段四步骤③，只读）。
 *
 * <p>详情一次返回**明细 + 支付流水**：订单详情页（A-29）要同屏展示三段，
 * 分开三次请求会让页面先出骨架再跳动。
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService
{
    private final ReferenceGuard referenceGuard;

    private final OrderItemMapper orderItemMapper;

    private final PaymentMapper paymentMapper;

    private final IOrderItemService orderItemService;

    private final ICheckoutService checkoutService;

    private final StockDeductSupport stockDeduct;

    private final MemberWriteMapper memberWriteMapper;

    private final ExportTaskMapper exportTaskMapper;

    @Override
    public List<Order> selectOrderList(Order order)
    {
        return baseMapper.selectOrderList(order);
    }

    @Override
    public Order selectOrderById(Long orderId)
    {
        Order order = referenceGuard.requireExists(baseMapper, orderId, "销售订单");

        OrderItem itemQuery = new OrderItem();
        itemQuery.setOrderId(orderId);
        order.setItems(orderItemService.maskCost(orderItemMapper.selectOrderItemList(itemQuery)));

        Payment payQuery = new Payment();
        payQuery.setOrderId(orderId);
        order.setPayments(paymentMapper.selectPaymentList(payQuery));
        return order;
    }

    // ==================================================================
    // 写：补录 / 改 / 删 / 取消 / 红冲 / 取货核销 / 导出（阶段四步骤④）
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p>委托结算执行：**优惠计算、库存扣减、券核销、积分口径与结算完全一致** ——
     * 另写一套必然分叉。requestId 由服务端生成（补录不是幂等场景）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addOrder(Order order)
    {
        if (order.getItems() == null || order.getItems().isEmpty())
        {
            throw BizErrors.badRequest("订单明细不能为空");
        }
        if (order.getPayments() == null || order.getPayments().isEmpty())
        {
            throw BizErrors.badRequest("支付方式不能为空（补录也必须落支付流水）");
        }
        CheckoutRequest req = new CheckoutRequest();
        req.setRequestId("MANUAL-" + UUID.randomUUID());
        req.setMemberId(order.getMemberId());
        req.setCouponId(order.getCouponId());
        req.setUsePoints(order.getPointUsed());
        req.setApprovalBy(order.getApprovalBy());
        List<CheckoutRequest.Item> items = new ArrayList<>();
        for (OrderItem it : order.getItems())
        {
            CheckoutRequest.Item one = new CheckoutRequest.Item();
            one.setBookId(it.getBookId());
            one.setUsedBookId(it.getUsedBookId());
            one.setQty(it.getQty());
            items.add(one);
        }
        req.setItems(items);
        List<CheckoutRequest.Pay> pays = new ArrayList<>();
        for (Payment p : order.getPayments())
        {
            CheckoutRequest.Pay one = new CheckoutRequest.Pay();
            one.setPayMethod(p.getPayMethod());
            one.setPayAmount(p.getPayAmount());
            one.setTradeNo(p.getTradeNo());
            pays.add(one);
        }
        req.setPayments(pays);
        checkoutService.checkout(req);
        return 1;
    }

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ **只接受「不影响金额与库存」的字段**（备注、收银员）：金额与明细一改就要同步调库存
     * 与四类优惠的分摊，那属于红冲的范畴（§4.3 规则 6）。故金额类字段一律置 null。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOrder(Order order)
    {
        if (order.getOrderId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的订单");
        }
        requireEditable(order.getOrderId());

        order.setOrderNo(null);
        order.setOrderType(null);
        order.setMemberId(null);
        order.setMemberNo(null);
        order.setMemberName(null);
        order.setMemberLevelId(null);
        order.setMemberDiscountRate(null);
        order.setBookKind(null);
        order.setTotalQty(null);
        order.setTotalAmount(null);
        order.setSingleDiscount(null);
        order.setMemberDiscount(null);
        order.setCouponAmount(null);
        order.setPointAmount(null);
        order.setDiscountTotal(null);
        order.setPayAmount(null);
        order.setPointUsed(null);
        order.setPointGain(null);
        order.setCouponId(null);
        order.setHasSpecialPrice(null);
        order.setNeedApproval(null);
        order.setStatus(null);
        order.setPayTime(null);
        order.setPayExpireTime(null);
        order.setPickupTime(null);
        order.setFinishTime(null);
        order.setIsRed(null);
        order.setRedOriginOrderId(null);
        order.setIsSettled(null);
        order.setSettleId(null);
        order.setItems(null);
        order.setPayments(null);

        int rows = baseMapper.updateById(order);
        referenceGuard.requireAffected(rows, "销售订单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeOrders(Long[] orderIds)
    {
        List<Long> idList = (orderIds == null) ? Collections.emptyList() : Arrays.asList(orderIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "销售订单");
        for (Serializable id : ids)
        {
            Order one = requireEditable((Long) id);
            releaseStock(one, "订单删除");
        }
        // 主从级联：先删明细与支付流水，再删订单头
        orderItemMapper.deleteByOrderIds(idList.toArray(new Long[0]));
        paymentMapper.delete(Wrappers.<Payment>lambdaQuery().in(Payment::getOrderId, idList));
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelOrder(Long orderId, String reason)
    {
        Order order = referenceGuard.requireExists(baseMapper, orderId, "销售订单");
        // 契约：422 订单已支付，需走退货流程
        if (Objects.equals(order.getStatus(), Order.STATUS_PAID))
        {
            throw BizErrors.ruleViolated("订单「" + order.getOrderNo() + "」已支付，不能直接取消 —— 请走退货流程");
        }
        // 契约：409 订单已完成或已取消
        if (!Objects.equals(order.getStatus(), Order.STATUS_UNPAID)
                && !Objects.equals(order.getStatus(), Order.STATUS_WAIT_PICKUP))
        {
            throw BizErrors.conflict("订单「" + order.getOrderNo() + "」当前为「"
                    + statusName(order.getStatus()) + "」，不能取消");
        }
        releaseStock(order, "订单取消：" + reason);

        Order patch = new Order();
        patch.setOrderId(orderId);
        patch.setStatus(Order.STATUS_CANCELLED);
        patch.setRemark(appendNote(order.getRemark(), "【取消】" + reason));
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "销售订单");
        return rows;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>反向单的金额取负数</b>：按订单汇总时正负自然相抵，报表不必额外处理红冲；
     * 单据本身仍带 {@code isRed=1} 与 {@code redOriginOrderId} 可追溯（§4.3 规则 6：红冲记录永久保留）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderRedResult redOrder(Long orderId, String reason)
    {
        Order origin = referenceGuard.requireExists(baseMapper, orderId, "销售订单");
        if (Objects.equals(origin.getIsRed(), 1))
        {
            throw BizErrors.conflict("订单「" + origin.getOrderNo() + "」本身是红冲单，不能再红冲");
        }
        Long exists = baseMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getRedOriginOrderId, orderId));
        if (exists != null && exists > 0)
        {
            throw BizErrors.conflict("订单「" + origin.getOrderNo() + "」已存在红冲记录，不能重复红冲");
        }

        List<String> stockAfter = releaseStock(origin, "订单红冲：" + reason);

        Order red = new Order();
        red.setOrderNo(origin.getOrderNo() + "R");
        red.setOrderType(origin.getOrderType());
        red.setMemberId(origin.getMemberId());
        red.setMemberNo(origin.getMemberNo());
        red.setMemberName(origin.getMemberName());
        red.setMemberLevelId(origin.getMemberLevelId());
        red.setMemberDiscountRate(origin.getMemberDiscountRate());
        red.setBookKind(origin.getBookKind());
        red.setTotalQty(neg(origin.getTotalQty()));
        red.setTotalAmount(negB(origin.getTotalAmount()));
        red.setSingleDiscount(negB(origin.getSingleDiscount()));
        red.setMemberDiscount(negB(origin.getMemberDiscount()));
        red.setCouponAmount(negB(origin.getCouponAmount()));
        red.setPointAmount(negB(origin.getPointAmount()));
        red.setDiscountTotal(negB(origin.getDiscountTotal()));
        red.setPayAmount(negB(origin.getPayAmount()));
        red.setPointUsed(neg(origin.getPointUsed()));
        red.setPointGain(neg(origin.getPointGain()));
        red.setCouponId(origin.getCouponId());
        red.setHasSpecialPrice(origin.getHasSpecialPrice());
        red.setNeedApproval(0);
        red.setStatus(Order.STATUS_REFUNDED);
        red.setIsRed(1);
        red.setRedOriginOrderId(orderId);
        red.setIsSettled(0);
        red.setHoldId(0L);
        red.setCashierBy(SecurityUtils.getUsername());
        red.setRemark("【红冲】原单 " + origin.getOrderNo() + "：" + reason);
        baseMapper.insert(red);

        OrderRedResult result = new OrderRedResult();
        result.setOriginOrderNo(origin.getOrderNo());
        result.setRedOrderNo(red.getOrderNo());
        result.setRedOrderId(red.getOrderId());
        result.setRedAmount(origin.getPayAmount());
        result.setStockAfter(stockAfter);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPickupResult pickupOrder(Long orderId, String pickupCode, String pickupBy)
    {
        Order order = referenceGuard.requireExists(baseMapper, orderId, "销售订单");
        if (!Objects.equals(order.getPickupCode(), pickupCode))
        {
            throw BizErrors.notFound("取货码无效（订单 " + order.getOrderNo() + "）");
        }
        if (Objects.equals(order.getStatus(), Order.STATUS_FINISHED))
        {
            throw BizErrors.conflict("订单「" + order.getOrderNo() + "」已核销");
        }
        if (!Objects.equals(order.getStatus(), Order.STATUS_WAIT_PICKUP))
        {
            throw BizErrors.ruleViolated("订单「" + order.getOrderNo() + "」当前为「"
                    + statusName(order.getStatus()) + "」，不是「3待取货」，不能核销");
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = (pickupBy == null || pickupBy.isBlank()) ? SecurityUtils.getUsername() : pickupBy;
        Order patch = new Order();
        patch.setOrderId(orderId);
        patch.setStatus(Order.STATUS_FINISHED);
        patch.setPickupTime(now);
        patch.setPickupBy(operator);
        patch.setFinishTime(now);
        referenceGuard.requireAffected(baseMapper.updateById(patch), "销售订单");

        // 核销时才完成消费 → 此时累加积分与成长值（线上下单的口径）
        if (order.getMemberId() != null && order.getMemberId() > 0 && nzi(order.getPointGain()) > 0)
        {
            memberWriteMapper.addPoints(order.getMemberId(), order.getPointGain());
            memberWriteMapper.insertPointLog(order.getMemberId(), 1, order.getPointGain(), 0,
                    order.getOrderNo(), "取货核销获得");
            memberWriteMapper.addConsume(order.getMemberId(), nz(order.getPayAmount()));
        }

        OrderPickupResult result = new OrderPickupResult();
        result.setOrderId(orderId);
        result.setOrderNo(order.getOrderNo());
        result.setStatus(Order.STATUS_FINISHED);
        result.setPickupTime(now);
        result.setPickupBy(operator);
        result.setPointGain(order.getPointGain());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只建任务行（FR-4.6 规则 4：导出统一走异步任务 + 下载中心）；**文件的生成与下载属阶段八**。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderExportResult exportOrders(OrderExportRequest request)
    {
        // 契约：422 导出时间跨度超过 1 年（需分批）
        if (request != null && request.getBeginTime() != null && request.getEndTime() != null
                && !request.getBeginTime().isBlank() && !request.getEndTime().isBlank())
        {
            LocalDateTime b = LocalDateTime.parse(request.getBeginTime().replace(" ", "T"));
            LocalDateTime e = LocalDateTime.parse(request.getEndTime().replace(" ", "T"));
            if (b.plusYears(1).isBefore(e))
            {
                throw BizErrors.ruleViolated("导出时间跨度超过 1 年，请分批导出");
            }
        }
        boolean cost = request != null && Boolean.TRUE.equals(request.getCostFields());
        String name = "订单导出_" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 含成本字段 → 标 isSensitive（NFR-S8：导出含敏感/成本字段需二次授权）
        exportTaskMapper.insertTask(name, "订单", cost ? 1 : 0, SecurityUtils.getUsername());

        OrderExportResult result = new OrderExportResult();
        result.setTaskId(exportTaskMapper.selectLastTaskId());
        result.setTaskName(name);
        result.setStatus(1);
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 订单必须**待支付且未日结**（日结后只能红冲，§4.3 规则 6） */
    private Order requireEditable(Long orderId)
    {
        Order one = referenceGuard.requireExists(baseMapper, orderId, "销售订单");
        if (Objects.equals(one.getIsSettled(), 1))
        {
            throw BizErrors.ruleViolated("订单「" + one.getOrderNo()
                    + "」已日结锁定，不能修改或删除 —— 调整必须走红冲（§4.3 规则 6）");
        }
        if (!Objects.equals(one.getStatus(), Order.STATUS_UNPAID))
        {
            throw BizErrors.ruleViolated("订单「" + one.getOrderNo() + "」当前为「"
                    + statusName(one.getStatus()) + "」，只有「1待支付」的订单可以修改或删除");
        }
        return one;
    }

    /**
     * 把订单明细的库存**回补**（取消 / 删除 / 红冲都要做，否则库存会凭空少掉）。
     *
     * @return 逐行的结存描述
     */
    private List<String> releaseStock(Order order, String remark)
    {
        List<String> details = new ArrayList<>();
        OrderItem query = new OrderItem();
        query.setOrderId(order.getOrderId());
        for (OrderItem it : orderItemMapper.selectOrderItemList(query))
        {
            if (it.getBookId() != null && it.getBookId() > 0 && nzi(it.getQty()) > 0)
            {
                details.addAll(stockDeduct.giveBack(it.getBookId(), it.getQty(), order.getOrderNo(), remark));
            }
        }
        return details;
    }

    private String appendNote(String remark, String note)
    {
        String base = (remark == null) ? "" : remark.trim();
        return base.isEmpty() ? note : base + " | " + note;
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case Order.STATUS_UNPAID -> "待支付";
            case Order.STATUS_PAID -> "已支付";
            case Order.STATUS_WAIT_PICKUP -> "待取货";
            case Order.STATUS_FINISHED -> "已完成";
            case Order.STATUS_CANCELLED -> "已取消";
            case Order.STATUS_REFUNDING -> "退款中";
            case Order.STATUS_REFUNDED -> "已退款";
            default -> "未知";
        };
    }

    private Integer neg(Integer v)
    {
        return (v == null) ? null : -v;
    }

    private BigDecimal negB(BigDecimal v)
    {
        return (v == null) ? null : v.negate();
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
