package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.CashierHoldRequest;
import com.tbgbook.bookstore.sale.domain.CashierHoldResult;
import com.tbgbook.bookstore.sale.domain.HoldResumeResult;
import com.tbgbook.bookstore.sale.domain.OrderHold;
import com.tbgbook.bookstore.sale.mapper.OrderHoldMapper;
import com.tbgbook.bookstore.sale.service.IOrderHoldService;
import com.tbgbook.bookstore.sale.support.PricingSupport;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 收银挂单 Service 实现（阶段四步骤③）。
 *
 * <p>三处口径：
 * <ol>
 *   <li><b>挂单不锁库存</b>（契约明写）：挂单只是存购物车快照，不碰 {@code biz_stock}。
 *       故取单时必须重校验 —— 这是本资源与「下单锁库存」最大的区别</li>
 *   <li><b>取单重校验库存与价格，变动如实列出</b>（{@code changes}）：书可能被买走、
 *       活动可能结束。**不静默修正**，交给收银员当面跟顾客确认</li>
 *   <li><b>购物车结构由本实现定义</b>（契约未定义，已登记）：
 *       {@code {"items":[{"bookId","usedBookId","qty","unitPrice"}]}}。取单要靠它重校验</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class OrderHoldServiceImpl extends ServiceImpl<OrderHoldMapper, OrderHold>
        implements IOrderHoldService
{
    /** 挂单号前缀：{@code GH} + yyyyMMdd + 6 位流水（需求未定义，本次自定） */
    private static final String HOLD_NO_PREFIX = "GH";

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final StockMapper stockMapper;

    private final PricingSupport pricingSupport;

    @Override
    public List<OrderHold> selectOrderHoldList(OrderHold hold)
    {
        return baseMapper.selectOrderHoldList(hold);
    }

    @Override
    public OrderHold selectOrderHoldById(Long holdId)
    {
        return referenceGuard.requireExists(baseMapper, holdId, "挂单");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addOrderHold(OrderHold hold)
    {
        requireCart(hold.getCartData());
        hold.setHoldId(null);
        hold.setHoldNo(null);
        hold.setStatus(OrderHold.STATUS_HOLDING);
        hold.setHoldBy(SecurityUtils.getUsername());
        hold.setHoldTime(LocalDateTime.now());
        hold.setResumeTime(null);
        summarize(hold);

        Long predictedId = baseMapper.selectNextHoldId();
        hold.setHoldNo(buildHoldNo(predictedId == null ? 1L : predictedId));
        int rows = baseMapper.insert(hold);

        String actualNo = buildHoldNo(hold.getHoldId());
        if (!actualNo.equals(hold.getHoldNo()))
        {
            OrderHold patch = new OrderHold();
            patch.setHoldId(hold.getHoldId());
            patch.setHoldNo(actualNo);
            baseMapper.updateById(patch);
            hold.setHoldNo(actualNo);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOrderHold(OrderHold hold)
    {
        if (hold.getHoldId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的挂单");
        }
        OrderHold current = referenceGuard.requireExists(baseMapper, hold.getHoldId(), "挂单");
        requireHolding(current, "修改");
        if (hold.getCartData() != null)
        {
            requireCart(hold.getCartData());
        }
        hold.setHoldNo(null);
        hold.setStatus(null);
        hold.setHoldBy(null);
        hold.setHoldTime(null);
        hold.setResumeTime(null);
        if (hold.getCartData() != null)
        {
            summarize(hold);
        }
        int rows = baseMapper.updateById(hold);
        referenceGuard.requireAffected(rows, "挂单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeOrderHolds(Long[] holdIds)
    {
        List<Long> idList = (holdIds == null) ? Collections.emptyList() : Arrays.asList(holdIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "挂单");
        for (Serializable id : ids)
        {
            referenceGuard.requireExists(baseMapper, id, "挂单");
        }
        // 本表无 del_flag，删行即物理删（挂单是 T+0 临时态，没有财务含义）
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashierHoldResult cashierHold(CashierHoldRequest request)
    {
        requireCart(request.getCartData());

        OrderHold hold = new OrderHold();
        hold.setMemberId(request.getMemberId() == null ? 0L : request.getMemberId());
        hold.setCartData(request.getCartData());
        hold.setTotalQty(request.getTotalQty());
        hold.setTotalAmount(request.getTotalAmount());
        addOrderHold(hold);

        CashierHoldResult result = new CashierHoldResult();
        result.setHoldId(hold.getHoldId());
        result.setHoldNo(hold.getHoldNo());
        result.setHoldTime(hold.getHoldTime());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelHold(Long holdId)
    {
        OrderHold hold = referenceGuard.requireExists(baseMapper, holdId, "挂单");
        // 契约：409 挂单已被取单结算
        if (Objects.equals(hold.getStatus(), OrderHold.STATUS_RESUMED))
        {
            throw BizErrors.conflict("挂单「" + hold.getHoldNo() + "」已被取单结算，不能作废");
        }
        if (Objects.equals(hold.getStatus(), OrderHold.STATUS_VOIDED))
        {
            throw BizErrors.conflict("挂单「" + hold.getHoldNo() + "」已作废");
        }
        OrderHold patch = new OrderHold();
        patch.setHoldId(holdId);
        patch.setStatus(OrderHold.STATUS_VOIDED);
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "挂单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HoldResumeResult resumeHold(Long holdId)
    {
        OrderHold hold = referenceGuard.requireExists(baseMapper, holdId, "挂单");
        // 契约：409 挂单已被取单或已作废
        if (!Objects.equals(hold.getStatus(), OrderHold.STATUS_HOLDING))
        {
            throw BizErrors.conflict("挂单「" + hold.getHoldNo() + "」已"
                    + (Objects.equals(hold.getStatus(), OrderHold.STATUS_RESUMED) ? "取单结算" : "作废")
                    + "，不能重复取单");
        }

        List<String> changes = revalidate(hold.getCartData());

        OrderHold patch = new OrderHold();
        patch.setHoldId(holdId);
        patch.setStatus(OrderHold.STATUS_RESUMED);
        patch.setResumeTime(LocalDateTime.now());
        referenceGuard.requireAffected(baseMapper.updateById(patch), "挂单");

        HoldResumeResult result = new HoldResumeResult();
        result.setHoldId(holdId);
        result.setHoldNo(hold.getHoldNo());
        result.setCartData(hold.getCartData());
        result.setMemberId(hold.getMemberId());
        result.setChanges(changes);
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 取单时的重校验：逐行查库存与当前价，把变动收进 {@code changes}。
     *
     * <p>**不抛异常、不静默修正** —— 挂单不锁库存是设计使然，变动是正常现象，
     * 该做的是告诉收银员，而不是拒绝取单或悄悄改价。
     */
    private List<String> revalidate(Map<String, Object> cartData)
    {
        List<String> changes = new ArrayList<>();
        for (Map<String, Object> line : cartLines(cartData))
        {
            Long bookId = asLong(line.get("bookId"));
            if (bookId == null || bookId <= 0)
            {
                continue; // 二手书行（usedBookId）本期不做重校验
            }
            Book book = bookMapper.selectById(bookId);
            if (book == null)
            {
                changes.add("有一行商品已下架或不存在（图书ID=" + bookId + "），请移除后重新扫码");
                continue;
            }
            int want = (int) nzl(asLong(line.get("qty")));
            int have = availableOf(bookId);
            if (want > have)
            {
                changes.add("《" + book.getTitle() + "》库存不足：挂单时 " + want + " 册，现仅剩 " + have + " 册");
            }
            BigDecimal stored = asDecimal(line.get("unitPrice"));
            BigDecimal now = pricingSupport.resolve(book, null).getUnitPrice();
            if (stored != null && now != null && stored.compareTo(now) != 0)
            {
                changes.add("《" + book.getTitle() + "》价格已调整：" + stored + " → " + now);
            }
        }
        return changes;
    }

    /** 某图书跨库位的可售合计 */
    private int availableOf(Long bookId)
    {
        Integer sum = stockMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.tbgbook.bookstore.stock.domain.Stock>lambdaQuery()
                        .eq(com.tbgbook.bookstore.stock.domain.Stock::getBookId, bookId))
                .stream().map(s -> s.getQtyAvailable() == null ? 0 : s.getQtyAvailable())
                .reduce(0, Integer::sum);
        return sum == null ? 0 : sum;
    }

    /** 购物车行（容错：结构不对就当空） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cartLines(Map<String, Object> cartData)
    {
        if (cartData == null)
        {
            return Collections.emptyList();
        }
        Object items = cartData.get("items");
        if (!(items instanceof List))
        {
            return Collections.emptyList();
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Object o : (List<Object>) items)
        {
            if (o instanceof Map)
            {
                lines.add((Map<String, Object>) o);
            }
        }
        return lines;
    }

    /** 按购物车汇总件数与金额（客户端没传时用） */
    private void summarize(OrderHold hold)
    {
        int qty = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (Map<String, Object> line : cartLines(hold.getCartData()))
        {
            int q = (int) nzl(asLong(line.get("qty")));
            qty += q;
            BigDecimal unit = asDecimal(line.get("unitPrice"));
            if (unit != null)
            {
                amount = amount.add(unit.multiply(BigDecimal.valueOf(q)));
            }
        }
        if (hold.getTotalQty() == null)
        {
            hold.setTotalQty(qty);
        }
        if (hold.getTotalAmount() == null)
        {
            hold.setTotalAmount(amount.setScale(2, java.math.RoundingMode.HALF_UP));
        }
    }

    private void requireCart(Map<String, Object> cartData)
    {
        if (cartLines(cartData).isEmpty())
        {
            throw BizErrors.ruleViolated("购物车为空，没有可挂单的商品");
        }
    }

    private void requireHolding(OrderHold hold, String action)
    {
        if (!Objects.equals(hold.getStatus(), OrderHold.STATUS_HOLDING))
        {
            throw BizErrors.ruleViolated("挂单「" + hold.getHoldNo() + "」已"
                    + (Objects.equals(hold.getStatus(), OrderHold.STATUS_RESUMED) ? "取单结算" : "作废")
                    + "，不能" + action);
        }
    }

    private String buildHoldNo(Long holdId)
    {
        return HOLD_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", holdId);
    }

    private Long asLong(Object v)
    {
        if (v == null)
        {
            return null;
        }
        if (v instanceof Number)
        {
            return ((Number) v).longValue();
        }
        try
        {
            return Long.valueOf(String.valueOf(v).trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private BigDecimal asDecimal(Object v)
    {
        if (v == null)
        {
            return null;
        }
        try
        {
            return new BigDecimal(String.valueOf(v).trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private long nzl(Long v)
    {
        return (v == null) ? 0L : v;
    }
}
