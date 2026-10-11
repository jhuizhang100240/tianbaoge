package com.tbgbook.bookstore.borrow.support;

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
 * 借出 / 归还的库存联动（阶段五步骤④，FR-4.5.2 / FR-4.5.3、§4.5 规则 2）。
 *
 * <p><b>口径</b>：借出把书从「可售」搬到「借出」（{@code qtyAvailable-1} /
 * {@code qtyBorrowed+1}），归还反向搬回；实物总量保持
 * {@code 可售 + 锁定 + 借出 + 残损} 守恒。对应两条库存流水：
 * <ul>
 *   <li>借出 —— {@code stockType=1 可售}、{@code bizType=4 借出}、{@code qtyChange=-1}；
 *       另写一条 {@code stockType=3 借出}、{@code qtyChange=+1}；</li>
 *   <li>归还 —— {@code stockType=3 借出}、{@code bizType=5 归还}、{@code qtyChange=-1}；
 *       另写一条 {@code stockType=1 可售}、{@code qtyChange=+1}。</li>
 * </ul>
 *
 * <p><b>乐观锁</b>：{@code Stock} 带 {@code @Version}，影响 0 行时 MP 不抛异常，
 * 必须显式 {@link ReferenceGuard#requireAffected}，否则会静默超借（§4.2 规则 3）。
 *
 * <p><b>借出并发保护</b>：{@link #lend} 用**条件更新**（{@code qty_available >= take}）
 * 保证两人同时借同一本书时只有一人成功。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class BorrowStockSupport
{
    /** 库存类型：1可售 */
    private static final int STOCK_TYPE_AVAILABLE = 1;

    /** 库存类型：3借出 */
    private static final int STOCK_TYPE_BORROWED = 3;

    /** 业务类型：4借出 */
    private static final int BIZ_TYPE_LEND = 4;

    /** 业务类型：5归还 */
    private static final int BIZ_TYPE_RETURN = 5;

    private final ReferenceGuard referenceGuard;

    private final StockMapper stockMapper;

    private final StockFlowMapper stockFlowMapper;

    /**
     * 借出：可售 -1、借出 +1（按可售最多的库位贪心），并各写一条流水。
     *
     * @param bookId 图书ID
     * @param bizNo  借阅单号
     * @return 实际出库库位ID
     */
    public Long lend(Long bookId, String bizNo)
    {
        List<Stock> rows = stockMapper.selectList(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .gt(Stock::getQtyAvailable, 0)
                .orderByDesc(Stock::getQtyAvailable));
        if (rows.isEmpty())
        {
            throw BizErrors.ruleViolated("该书暂无可借库存（可售 0 册），请先入库或等读者归还");
        }
        Stock row = rows.get(0);
        int before = nzi(row.getQtyAvailable());
        int borrowedBefore = nzi(row.getQtyBorrowed());
        int affected = stockMapper.lendOne(row.getStockId());
        referenceGuard.requireAffected(affected, "库存");
        writeFlow(bookId, row.getLocationId(), STOCK_TYPE_AVAILABLE, BIZ_TYPE_LEND,
                -1, before, before - 1, bizNo, "借阅借出（可售）");
        writeFlow(bookId, row.getLocationId(), STOCK_TYPE_BORROWED, BIZ_TYPE_LEND,
                1, borrowedBefore, borrowedBefore + 1, bizNo, "借阅借出（借出）");
        return row.getLocationId();
    }

    /**
     * 归还：借出 -1、可售 +1（回到原库位），并各写一条流水。
     *
     * @param bookId    图书ID
     * @param locationId 原出库库位（可空 → 落到可售最多的库位或未分配）
     * @param bizNo     借阅单号
     */
    public void giveBack(Long bookId, Long locationId, String bizNo)
    {
        Long location = locationId;
        List<Stock> rows = stockMapper.selectList(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .orderByDesc(Stock::getQtyBorrowed));
        Stock row = null;
        if (location != null && location > 0)
        {
            for (Stock candidate : rows)
            {
                if (location.equals(candidate.getLocationId()))
                {
                    row = candidate;
                    break;
                }
            }
        }
        if (row == null && !rows.isEmpty())
        {
            row = rows.get(0);
            location = row.getLocationId();
        }
        if (row == null)
        {
            // 没有任何库存行 → 建一行「未分配库位」，与入库 upsert 同口径
            Stock fresh = new Stock();
            fresh.setBookId(bookId);
            fresh.setLocationId(location == null ? 0L : location);
            fresh.setQtyAvailable(1);
            fresh.setQtyLocked(0);
            fresh.setQtyBorrowed(0);
            fresh.setQtyDamaged(0);
            fresh.setSafetyQty(0);
            fresh.setVersion(0);
            stockMapper.insert(fresh);
            writeFlow(bookId, fresh.getLocationId(), STOCK_TYPE_AVAILABLE, BIZ_TYPE_RETURN,
                    1, 0, 1, bizNo, "借阅归还（可售）");
            return;
        }
        int borrowedBefore = nzi(row.getQtyBorrowed());
        int availableBefore = nzi(row.getQtyAvailable());
        int affected = stockMapper.giveBackOne(row.getStockId());
        referenceGuard.requireAffected(affected, "库存");
        writeFlow(bookId, row.getLocationId(), STOCK_TYPE_BORROWED, BIZ_TYPE_RETURN,
                -1, borrowedBefore, borrowedBefore - 1, bizNo, "借阅归还（借出）");
        writeFlow(bookId, row.getLocationId(), STOCK_TYPE_AVAILABLE, BIZ_TYPE_RETURN,
                1, availableBefore, availableBefore + 1, bizNo, "借阅归还（可售）");
    }

    private void writeFlow(Long bookId, Long locationId, int stockType, int bizType,
            int change, int before, int after, String bizNo, String remark)
    {
        StockFlow flow = new StockFlow();
        flow.setBookId(bookId);
        flow.setLocationId(locationId == null ? 0L : locationId);
        flow.setStockType(stockType);
        flow.setBizType(bizType);
        flow.setBizNo(bizNo == null ? "" : bizNo);
        flow.setQtyChange(change);
        flow.setQtyBefore(before);
        flow.setQtyAfter(after);
        flow.setRemark(remark);
        stockFlowMapper.insert(flow);
    }

    private int nzi(Integer value)
    {
        return value == null ? 0 : value;
    }
}
