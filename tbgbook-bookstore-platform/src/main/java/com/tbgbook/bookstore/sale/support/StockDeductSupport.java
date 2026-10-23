package com.tbgbook.bookstore.sale.support;

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
 * 销售出库的库存扣减（结算与后台补录共用）。
 *
 * <p>三处要点：
 * <ol>
 *   <li><b>多库位贪心扣减</b>（可售多的先扣）：销售单的请求体没有库位字段（契约如此），
 *       而一本书可能散在多个库位 —— 只从一行扣会「明明有货却报库存不足」</li>
 *   <li><b>先整体校验再动手</b>：不够时给「当前可售 N 册」这种带数字的文案</li>
 *   <li><b>乐观锁必须显式检查</b>：{@code Stock} 带 {@code @Version}，MP 在影响 0 行时
 *       **不抛异常**，不检查就会静默超卖（§4.2 规则 3）</li>
 * </ol>
 * 每条受影响的库存行写一条销售出库流水（{@code biz_type=2}），含变动前后结存。
 */
@Component
@RequiredArgsConstructor
public class StockDeductSupport
{
    /** `biz_stock_flow.biz_type`：销售出库 */
    public static final int BIZ_TYPE_SALE_OUT = 2;

    /** `biz_stock_flow.biz_type`：退货入库 */
    public static final int BIZ_TYPE_RETURN_IN = 3;

    /** `biz_stock_flow.stock_type`：可售 */
    private static final int STOCK_TYPE_AVAILABLE = 1;

    private final ReferenceGuard referenceGuard;

    private final StockMapper stockMapper;

    private final StockFlowMapper stockFlowMapper;

    /**
     * 扣减该图书的可售库存（多库位贪心），逐行写流水。
     *
     * @param bookId 图书ID
     * @param qty    数量（必须 &gt; 0）
     * @param bizNo  业务单据号（订单号）
     * @param remark 流水备注
     * @return 逐行的结存描述（如「A-1-1 10→8」），供响应回显
     * @throws com.tbgbook.common.exception.ServiceException 422 可售合计不足；
     *         409 乐观锁冲突（并发下重试）
     */
    public List<String> deduct(Long bookId, int qty, String bizNo, String remark)
    {
        return move(bookId, -qty, BIZ_TYPE_SALE_OUT, bizNo, remark, "可售库存");
    }

    /**
     * 回补该图书的可售库存（退货 / 红冲用）。
     *
     * <p>回补到**可售最多的那个库位**（与扣减的贪心口径对称）：退货时书回到架上，
     * 具体回哪个库位无从得知，回「原本最可能有货的库位」是最不容易出错的近似。
     *
     * @return 逐行的结存描述
     */
    public List<String> giveBack(Long bookId, int qty, String bizNo, String remark)
    {
        return move(bookId, qty, BIZ_TYPE_RETURN_IN, bizNo, remark, "可售库存");
    }

    /** delta &gt; 0 为回补（落到可售最多的库位），delta &lt; 0 为扣减（多库位贪心） */
    private List<String> move(Long bookId, int delta, int bizType, String bizNo, String remark,
            String subject)
    {
        List<Stock> rows = stockMapper.selectList(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId).orderByDesc(Stock::getQtyAvailable));
        int available = 0;
        for (Stock s : rows)
        {
            available += nzi(s.getQtyAvailable());
        }

        List<String> details = new java.util.ArrayList<>();
        if (delta > 0)
        {
            if (rows.isEmpty())
            {
                // 没有库存行 → 建一行（未分配库位），与入库的 upsert 同口径
                Stock fresh = new Stock();
                fresh.setBookId(bookId);
                fresh.setLocationId(0L);
                fresh.setQtyAvailable(delta);
                fresh.setQtyLocked(0);
                fresh.setQtyBorrowed(0);
                fresh.setQtyDamaged(0);
                fresh.setSafetyQty(0);
                fresh.setVersion(0);
                stockMapper.insert(fresh);
                writeFlow(bookId, 0L, bizType, delta, 0, delta, bizNo, remark);
                details.add("0 → " + delta);
                return details;
            }
            Stock s = rows.get(0);
            int before = nzi(s.getQtyAvailable());
            int after = before + delta;
            Stock patch = new Stock();
            patch.setStockId(s.getStockId());
            patch.setVersion(s.getVersion());
            patch.setQtyAvailable(after);
            referenceGuard.requireAffected(stockMapper.updateById(patch), "库存");
            writeFlow(bookId, s.getLocationId(), bizType, delta, before, after, bizNo, remark);
            details.add(s.getLocationId() + " " + before + "→" + after);
            return details;
        }

        int need = -delta;
        if (available < need)
        {
            throw BizErrors.notEnough(subject, "该图书当前可售 " + available + " 册，本单需要 " + need + " 册");
        }
        int left = need;
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
            int before = have;
            int after = have - take;
            Stock patch = new Stock();
            patch.setStockId(s.getStockId());
            patch.setVersion(s.getVersion());
            patch.setQtyAvailable(after);
            referenceGuard.requireAffected(stockMapper.updateById(patch), "库存");
            writeFlow(bookId, s.getLocationId(), bizType, -take, before, after, bizNo, remark);
            details.add(s.getLocationId() + " " + before + "→" + after);
            left -= take;
        }
        return details;
    }

    private void writeFlow(Long bookId, Long locationId, int bizType, int change, int before, int after,
            String bizNo, String remark)
    {
        StockFlow flow = new StockFlow();
        flow.setBookId(bookId);
        flow.setLocationId(locationId == null ? 0L : locationId);
        flow.setStockType(STOCK_TYPE_AVAILABLE);
        flow.setBizType(bizType);
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
