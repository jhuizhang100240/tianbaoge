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
import java.util.LinkedHashMap;
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
import com.tbgbook.bookstore.sale.domain.ExchangeConfirmResult;
import com.tbgbook.bookstore.sale.domain.ExchangeOrder;
import com.tbgbook.bookstore.sale.domain.ExchangeOrderItem;
import com.tbgbook.bookstore.sale.domain.Order;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.mapper.ExchangeOrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.ExchangeOrderMapper;
import com.tbgbook.bookstore.sale.mapper.OrderItemMapper;
import com.tbgbook.bookstore.sale.mapper.OrderMapper;
import com.tbgbook.bookstore.sale.mapper.ReturnOrderItemMapper;
import com.tbgbook.bookstore.sale.service.IExchangeOrderService;
import com.tbgbook.bookstore.sale.support.StockDeductSupport;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 换货单 Service 实现（阶段四步骤⑤）。
 *
 * <p><b>定价口径（本次自定，已登记进度日志）</b>：
 * 换出按图书**当前定价**、换回按原明细的**分摊后实付单价**（与退货同口径）。
 * 差额 = 换出 − 换回，正数补款、负数退款（FR-4.3.6）。
 *
 * <p><b>防重复换货</b>：换回额度 = 原购数量 − 已退数量 − 已换回数量，
 * 后两者都只算**已完成**的单据 —— 待处理的不占用额度，否则一张被弃置的换货单
 * 会把顾客的换货权利永久占掉（换货单的「3已作废」目前没有任何端点能到达，已登记缺口）。
 *
 * <p>⚠️ 换货明细**没有** {@code order_item_id} 列，已换回量只能按「原单 + 图书」聚合，
 * 同一本书在原单里出现多行时会被合并计算。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class ExchangeOrderServiceImpl extends ServiceImpl<ExchangeOrderMapper, ExchangeOrder>
        implements IExchangeOrderService
{
    /** 换货单号前缀（需求未定义，本次自定）：HH + yyyyMMdd + 6 位流水 */
    private static final String EXCHANGE_NO_PREFIX = "HH";

    private static final int SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final BookMapper bookMapper;

    private final ExchangeOrderItemMapper itemMapper;

    private final ReturnOrderItemMapper returnItemMapper;

    private final StockDeductSupport stockDeduct;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<ExchangeOrder> selectExchangeOrderList(ExchangeOrder order)
    {
        return baseMapper.selectExchangeOrderList(order);
    }

    @Override
    public ExchangeOrder selectExchangeOrderById(Long exchangeId)
    {
        ExchangeOrder order = referenceGuard.requireExists(baseMapper, exchangeId, "换货单");
        ExchangeOrderItem query = new ExchangeOrderItem();
        query.setExchangeId(exchangeId);
        order.setItems(itemMapper.selectExchangeOrderItemList(query));
        return order;
    }

    // ==================================================================
    // 建单 / 改单 / 删单
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addExchangeOrder(ExchangeOrder order)
    {
        if (order.getItems() == null || order.getItems().isEmpty())
        {
            throw BizErrors.badRequest("换货单至少要有一行明细");
        }
        if (order.getOriginOrderId() == null || order.getOriginOrderId() <= 0)
        {
            throw BizErrors.badRequest("换货必须指定原订单（换回的书要对得上他买过、还没退过）");
        }
        Order origin = referenceGuard.requireExists(orderMapper, order.getOriginOrderId(), "原订单");

        // 服务端接管：金额、状态、办理人一律不收客户端传值
        order.setExchangeId(null);
        order.setExchangeNo(null);
        order.setOriginOrderNo(origin.getOrderNo());
        order.setMemberId(origin.getMemberId());
        order.setStatus(ExchangeOrder.STATUS_PENDING);
        order.setExchangeBy(null);
        order.setExchangeTime(null);

        prepareItems(order, origin);

        Long predictedId = baseMapper.selectNextExchangeId();
        order.setExchangeNo(buildExchangeNo(predictedId == null ? 1L : predictedId));
        int rows = baseMapper.insert(order);
        correctNo(order);
        persistItems(order);
        return rows;
    }

    /**
     * {@inheritDoc}
     *
     * <p>本表除明细外没有可写字段（操作人、时间、三个金额都由服务端算），
     * 故本接口的语义是**整体替换明细**：删掉原明细、按新明细重建并重算金额。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateExchangeOrder(ExchangeOrder order)
    {
        ExchangeOrder current = requireEditable(order.getExchangeId(), "修改");
        if (order.getItems() == null || order.getItems().isEmpty())
        {
            throw BizErrors.badRequest("换货单的修改必须提交明细（本表其余字段都由服务端维护）");
        }
        Order origin = referenceGuard.requireExists(orderMapper, current.getOriginOrderId(), "原订单");

        // 服务端接管：原订单、会员、金额、状态一律取自库里那张单，不接受客户端改写
        order.setExchangeId(current.getExchangeId());
        order.setOriginOrderId(current.getOriginOrderId());
        order.setOriginOrderNo(current.getOriginOrderNo());
        order.setMemberId(current.getMemberId());
        order.setStatus(current.getStatus());
        order.setExchangeBy(current.getExchangeBy());
        order.setExchangeTime(current.getExchangeTime());

        prepareItems(order, origin);
        itemMapper.deleteByExchangeIds(new Long[] { current.getExchangeId() });
        persistItems(order);
        return baseMapper.updateById(totalPatch(order));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeExchangeOrders(Long[] exchangeIds)
    {
        List<Long> idList = (exchangeIds == null) ? Collections.emptyList() : Arrays.asList(exchangeIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "换货单");
        for (Serializable id : ids)
        {
            ExchangeOrder one = referenceGuard.requireExists(baseMapper, id, "换货单");
            requireEditable(one.getExchangeId(), "删除");
        }
        itemMapper.deleteByExchangeIds(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 确认换货
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExchangeConfirmResult confirm(Long exchangeId, Integer payMethod)
    {
        ExchangeOrder order = referenceGuard.requireExists(baseMapper, exchangeId, "换货单");
        if (!Objects.equals(order.getStatus(), ExchangeOrder.STATUS_PENDING))
        {
            throw BizErrors.conflict("换货单「" + order.getExchangeNo() + "」已"
                    + statusName(order.getStatus()) + "，不能重复确认");
        }
        if (nz(order.getDiffAmount()).signum() != 0 && payMethod == null)
        {
            throw BizErrors.ruleViolated("本次换货差额为 " + nz(order.getDiffAmount())
                    + " 元，必须传支付方式（正数=会员补款、负数=退款）");
        }

        ExchangeOrderItem query = new ExchangeOrderItem();
        query.setExchangeId(exchangeId);
        List<ExchangeOrderItem> items = itemMapper.selectExchangeOrderItemList(query);
        if (items.isEmpty())
        {
            throw BizErrors.ruleViolated("换货单没有明细，无法确认");
        }

        // 执行前再校验一次换回额度：建单到确认之间可能有别的换货单已完成
        requireReturnableQty(order, items, true);

        List<String> stockAfter = new ArrayList<>();
        for (ExchangeOrderItem it : items)
        {
            if (it.getBookId() == null || it.getBookId() <= 0 || nzi(it.getQty()) <= 0)
            {
                continue;
            }
            if (Objects.equals(it.getDirection(), ExchangeOrderItem.DIR_OUT))
            {
                stockAfter.addAll(stockDeduct.deduct(it.getBookId(), it.getQty(), order.getExchangeNo(),
                        "换货出库 · " + it.getTitle()));
            }
            else
            {
                stockAfter.addAll(stockDeduct.giveBack(it.getBookId(), it.getQty(), order.getExchangeNo(),
                        "换货入库 · " + it.getTitle()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ExchangeOrder patch = new ExchangeOrder();
        patch.setExchangeId(exchangeId);
        patch.setStatus(ExchangeOrder.STATUS_DONE);
        patch.setExchangeBy(SecurityUtils.getUsername());
        patch.setExchangeTime(now);
        referenceGuard.requireAffected(baseMapper.updateById(patch), "换货单");

        ExchangeConfirmResult result = new ExchangeConfirmResult();
        result.setExchangeId(exchangeId);
        result.setExchangeNo(order.getExchangeNo());
        result.setStatus(ExchangeOrder.STATUS_DONE);
        result.setOutAmount(nz(order.getOutAmount()));
        result.setInAmount(nz(order.getInAmount()));
        result.setDiffAmount(nz(order.getDiffAmount()));
        result.setExchangeTime(now);
        result.setStockAfter(stockAfter);
        return result;
    }

    // ==================================================================
    // 明细服务的挂点
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public ExchangeOrder requireEditable(Long exchangeId, String action)
    {
        if (exchangeId == null)
        {
            throw BizErrors.badRequest("未指定换货单");
        }
        ExchangeOrder order = referenceGuard.requireExists(baseMapper, exchangeId, "换货单");
        if (!Objects.equals(order.getStatus(), ExchangeOrder.STATUS_PENDING))
        {
            throw BizErrors.ruleViolated("换货单「" + order.getExchangeNo() + "」当前为「"
                    + statusName(order.getStatus()) + "」，只有「1待处理」的可以" + action);
        }
        return order;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void priceLine(ExchangeOrder parent, ExchangeOrderItem item)
    {
        if (item.getBookId() == null)
        {
            throw BizErrors.badRequest("换货明细的图书不能为空");
        }
        if (item.getQty() == null || item.getQty() <= 0)
        {
            throw BizErrors.ruleViolated("换货数量必须大于 0");
        }
        if (!Objects.equals(item.getDirection(), ExchangeOrderItem.DIR_OUT)
                && !Objects.equals(item.getDirection(), ExchangeOrderItem.DIR_IN))
        {
            throw BizErrors.ruleViolated("换货明细的 direction 只能是 1换出 或 2换回");
        }
        Book book = referenceGuard.requireExists(bookMapper, item.getBookId(), "图书");
        item.setIsbn(book.getIsbn());
        item.setTitle(book.getTitle());

        if (Objects.equals(item.getDirection(), ExchangeOrderItem.DIR_IN))
        {
            // 换回：按原明细的分摊后实付单价计入（与退货同口径）
            OrderItem originItem = requireOriginItem(parent, item.getBookId());
            item.setUnitPrice(perUnitShare(originItem));
        }
        else
        {
            // 换出：按图书当前定价
            item.setUnitPrice(nz(book.getPrice()));
        }
        item.setAmount(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQty()))
                .setScale(SCALE, RoundingMode.HALF_UP));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalcTotals(Long exchangeId)
    {
        ExchangeOrder order = referenceGuard.requireExists(baseMapper, exchangeId, "换货单");
        ExchangeOrderItem query = new ExchangeOrderItem();
        query.setExchangeId(exchangeId);
        List<ExchangeOrderItem> items = itemMapper.selectExchangeOrderItemList(query);
        applyAmounts(order, items);
        baseMapper.updateById(totalPatch(order));
    }

    // ==================================================================
    // 内部：明细准备与校验
    // ==================================================================

    /**
     * 逐行定价 + 校验 + 汇总三个金额。
     *
     * <p>换回额度按**图书聚合**后校验：同一本书拆成两行时，单看每一行都不超，
     * 加起来却可能超 —— 只逐行判会放过这种超换。
     */
    private void prepareItems(ExchangeOrder order, Order origin)
    {
        for (ExchangeOrderItem it : order.getItems())
        {
            priceLine(order, it);
        }
        requireReturnableQty(order, order.getItems(), false);
        applyAmounts(order, order.getItems());
    }

    /** 换回额度 = 原购数量 − 已退数量 − 已换回数量（后两者只算已完成的单据） */
    private void requireReturnableQty(ExchangeOrder order, List<ExchangeOrderItem> items, boolean atConfirm)
    {
        Map<Long, Integer> wantByBook = new LinkedHashMap<>();
        for (ExchangeOrderItem it : items)
        {
            if (Objects.equals(it.getDirection(), ExchangeOrderItem.DIR_IN) && it.getBookId() != null)
            {
                wantByBook.merge(it.getBookId(), nzi(it.getQty()), Integer::sum);
            }
        }
        String stage = atConfirm ? "确认时" : "建单时";
        for (Map.Entry<Long, Integer> e : wantByBook.entrySet())
        {
            OrderItem originItem = requireOriginItem(order, e.getKey());
            int bought = nzi(originItem.getQty());
            int returned = returnItemMapper.sumReturnedQty(originItem.getItemId());
            int exchanged = itemMapper.sumExchangedInQty(order.getOriginOrderId(), e.getKey());
            int canExchange = bought - returned - exchanged;
            if (e.getValue() > canExchange)
            {
                throw BizErrors.ruleViolated("「" + originItem.getTitle() + "」原购 " + bought
                        + " 册、已退 " + returned + " 册、已换回 " + exchanged + " 册，"
                        + stage + "最多还能换回 " + Math.max(canExchange, 0) + " 册");
            }
        }
    }

    private OrderItem requireOriginItem(ExchangeOrder order, Long bookId)
    {
        OrderItem query = new OrderItem();
        query.setOrderId(order.getOriginOrderId());
        query.setBookId(bookId);
        List<OrderItem> list = orderItemMapper.selectOrderItemList(query);
        if (list.isEmpty())
        {
            throw BizErrors.ruleViolated("图书「" + describe(bookId) + "」不在原订单里，不能换回");
        }
        return list.get(0);
    }

    private String describe(Long bookId)
    {
        try
        {
            Book b = bookMapper.selectById(bookId);
            return (b == null || b.getTitle() == null) ? String.valueOf(bookId) : b.getTitle();
        }
        catch (Exception e)
        {
            return String.valueOf(bookId);
        }
    }

    /** 原订单明细的**每册分摊后实付金额**（与退货退款同口径） */
    private BigDecimal perUnitShare(OrderItem originItem)
    {
        BigDecimal share = nz(originItem.getShareAmount());
        if (nzi(originItem.getQty()) <= 0)
        {
            return share.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return share.divide(BigDecimal.valueOf(nzi(originItem.getQty())), SCALE, RoundingMode.HALF_UP);
    }

    private void applyAmounts(ExchangeOrder order, List<ExchangeOrderItem> items)
    {
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal in = BigDecimal.ZERO;
        for (ExchangeOrderItem it : items)
        {
            if (Objects.equals(it.getDirection(), ExchangeOrderItem.DIR_OUT))
            {
                out = out.add(nz(it.getAmount()));
            }
            else
            {
                in = in.add(nz(it.getAmount()));
            }
        }
        order.setOutAmount(out.setScale(SCALE, RoundingMode.HALF_UP));
        order.setInAmount(in.setScale(SCALE, RoundingMode.HALF_UP));
        order.setDiffAmount(out.subtract(in).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private ExchangeOrder totalPatch(ExchangeOrder order)
    {
        ExchangeOrder patch = new ExchangeOrder();
        patch.setExchangeId(order.getExchangeId());
        patch.setOutAmount(order.getOutAmount());
        patch.setInAmount(order.getInAmount());
        patch.setDiffAmount(order.getDiffAmount());
        return patch;
    }

    private void persistItems(ExchangeOrder order)
    {
        for (ExchangeOrderItem it : order.getItems())
        {
            it.setExchangeId(order.getExchangeId());
            it.setItemId(null);
            itemMapper.insert(it);
        }
    }

    private void correctNo(ExchangeOrder order)
    {
        String actual = buildExchangeNo(order.getExchangeId());
        if (!actual.equals(order.getExchangeNo()))
        {
            ExchangeOrder patch = new ExchangeOrder();
            patch.setExchangeId(order.getExchangeId());
            patch.setExchangeNo(actual);
            baseMapper.updateById(patch);
            order.setExchangeNo(actual);
        }
    }

    private String buildExchangeNo(Long exchangeId)
    {
        return EXCHANGE_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", exchangeId);
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case ExchangeOrder.STATUS_PENDING -> "待处理";
            case ExchangeOrder.STATUS_DONE -> "已完成";
            case ExchangeOrder.STATUS_VOIDED -> "已作废";
            default -> "未知";
        };
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
