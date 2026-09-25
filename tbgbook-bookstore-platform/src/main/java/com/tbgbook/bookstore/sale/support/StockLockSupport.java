package com.tbgbook.bookstore.sale.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockFlow;
import com.tbgbook.bookstore.stock.mapper.StockFlowMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;

import lombok.RequiredArgsConstructor;

/**
 * 教材预订的**可售 ⇄ 锁定**搬家（`biz_type=12 预订锁定`）。
 *
 * <p>与 {@link StockDeductSupport} 的关键差别：那条路是「出库」（可售减少、总量减少），
 * 这条路是**同一条库存行内的两个桶之间搬家**（{@code qty_available} −n、{@code qty_locked} +n），
 * 实物总量不变，书还在店里，只是被预订者占住了。
 *
 * <p>三处要点：
 * <ol>
 *   <li><b>两桶各写一条流水</b>：{@code stock_type=1 可售} 记 −n、{@code stock_type=2 锁定} 记 +n。
 *       只写一边的话「流水加起来等于结存」这条对账规则会失效 —— 而这里两边都变了
 *   <li><b>多库位贪心</b>：可售多的先锁；解锁时锁定多的先还（与扣减/回补的口径对称）
 *   <li><b>乐观锁必须显式检查</b>：{@code Stock} 带 {@code @Version}，MP 在影响 0 行时
 *       **不抛异常**，不检查就会静默超锁（§4.2 规则 3）
 * </ol>
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class StockLockSupport
{
    /** `biz_stock_flow.biz_type`：预订锁定 */
    public static final int BIZ_TYPE_RESERVE_LOCK = 12;

    /** `biz_stock_flow.stock_type`：可售 */
    private static final int STOCK_TYPE_AVAILABLE = 1;

    /** `biz_stock_flow.stock_type`：锁定 */
    private static final int STOCK_TYPE_LOCKED = 2;

    private final ReferenceGuard referenceGuard;

    private final StockMapper stockMapper;

    private final StockFlowMapper stockFlowMapper;

    /**
     * 锁定：{@code qty_available} −qty、{@code qty_locked} +qty（多库位贪心）。
     *
     * @return 逐行的结存描述（如「A-1-1 可售 10→8 / 锁定 0→2」），供响应回显
     * @throws com.tbgbook.common.exception.ServiceException 422 可售合计不足；409 乐观锁冲突
     */
    public List<String> lock(Long bookId, int qty, String bizNo, String remark)
    {
        return move(bookId, qty, true, bizNo, remark);
    }

    /**
     * 解锁（取书核销 / 取消预订用）：{@code qty_locked} −qty、{@code qty_available} +qty。
     *
     * @return 逐行的结存描述
     */
    public List<String> unlock(Long bookId, int qty, String bizNo, String remark)
    {
        return move(bookId, qty, false, bizNo, remark);
    }

    private List<String> move(Long bookId, int qty, boolean locking, String bizNo, String remark)
    {
        if (qty <= 0)
        {
            throw BizErrors.badRequest("库存变动数量必须大于 0");
        }
        List<String> details = new ArrayList<>();
        if (locking)
        {
            // 从可售搬到锁定：可售多的行先搬
            List<Stock> rows = stockMapper.selectList(Wrappers.<Stock>lambdaQuery()
                    .eq(Stock::getBookId, bookId).orderByDesc(Stock::getQtyAvailable));
            int available = 0;
            for (Stock s : rows)
            {
                available += nzi(s.getQtyAvailable());
            }
            if (available < qty)
            {
                throw BizErrors.notEnough("可售库存", "该图书当前可售 " + available + " 册，本单需要锁定 " + qty + " 册");
            }
            int left = qty;
            for (Stock s : rows)
            {
                if (left <= 0)
                {
                    break;
                }
                int have = nzi(s.getQtyAvailable());
                if (have <= 0)
                {
                    continue;
                }
                int take = Math.min(have, left);
                int availBefore = have;
                int availAfter = have - take;
                int lockBefore = nzi(s.getQtyLocked());
                int lockAfter = lockBefore + take;
                patch(s, availAfter, lockAfter);
                writeFlow(bookId, s.getLocationId(), STOCK_TYPE_AVAILABLE, -take,
                        availBefore, availAfter, bizNo, remark);
                writeFlow(bookId, s.getLocationId(), STOCK_TYPE_LOCKED, take,
                        lockBefore, lockAfter, bizNo, remark);
                details.add(s.getLocationId() + " 可售 " + availBefore + "→" + availAfter
                        + " / 锁定 " + lockBefore + "→" + lockAfter);
                left -= take;
            }
            return details;
        }

        // 从锁定搬回可售：锁定多的行先还
        List<Stock> rows = stockMapper.selectList(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId).orderByDesc(Stock::getQtyLocked));
        int locked = 0;
        for (Stock s : rows)
        {
            locked += nzi(s.getQtyLocked());
        }
        if (locked < qty)
        {
            throw BizErrors.notEnough("锁定库存", "该图书当前锁定 " + locked + " 册，本单需要释放 " + qty + " 册");
        }
        int left = qty;
        for (Stock s : rows)
        {
            if (left <= 0)
            {
                break;
            }
            int have = nzi(s.getQtyLocked());
            if (have <= 0)
            {
                continue;
            }
            int take = Math.min(have, left);
            int lockBefore = have;
            int lockAfter = have - take;
            int availBefore = nzi(s.getQtyAvailable());
            int availAfter = availBefore + take;
            patch(s, availAfter, lockAfter);
            writeFlow(bookId, s.getLocationId(), STOCK_TYPE_LOCKED, -take,
                    lockBefore, lockAfter, bizNo, remark);
            writeFlow(bookId, s.getLocationId(), STOCK_TYPE_AVAILABLE, take,
                    availBefore, availAfter, bizNo, remark);
            details.add(s.getLocationId() + " 锁定 " + lockBefore + "→" + lockAfter
                    + " / 可售 " + availBefore + "→" + availAfter);
            left -= take;
        }
        return details;
    }

    private void patch(Stock row, int qtyAvailable, int qtyLocked)
    {
        Stock patch = new Stock();
        patch.setStockId(row.getStockId());
        patch.setVersion(row.getVersion());
        patch.setQtyAvailable(qtyAvailable);
        patch.setQtyLocked(qtyLocked);
        referenceGuard.requireAffected(stockMapper.updateById(patch), "库存");
    }

    private void writeFlow(Long bookId, Long locationId, int stockType, int change,
            int before, int after, String bizNo, String remark)
    {
        StockFlow flow = new StockFlow();
        flow.setBookId(bookId);
        flow.setLocationId(locationId == null ? 0L : locationId);
        flow.setStockType(stockType);
        flow.setBizType(BIZ_TYPE_RESERVE_LOCK);
        flow.setBizNo(bizNo == null ? "" : bizNo);
        flow.setQtyChange(change);
        flow.setQtyBefore(before);
        flow.setQtyAfter(after);
        flow.setRemark(remark);
        stockFlowMapper.insert(flow);
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
