package com.tbgbook.bookstore.stock.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.SnapshotBookAgg;
import com.tbgbook.bookstore.stock.domain.SnapshotCompare;
import com.tbgbook.bookstore.stock.domain.StockSnapshot;
import com.tbgbook.bookstore.stock.mapper.StockSnapshotItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockSnapshotMapper;
import com.tbgbook.bookstore.stock.service.IStockSnapshotService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 库存快照 Service 实现（阶段三步骤⑤）。
 *
 * <p><b>只读实现</b>：不写任何表。生成逻辑属阶段十的定时任务（见接口注释）。
 *
 * <p>本类最要紧的一条是**成本字段的裁剪口径**：无 {@code inventory:cost:view} 时
 * 把金额字段置 **null 而不是 0**。「金额没变」与「不给你看」是两件事 ——
 * 都返回 0 的话，前端无法区分该隐藏列还是该显示 0，只能整列不显示或整列误显示。
 * 这与阶段三步骤①在 `summary` 上踩过的是同一条规则（FR-4.6 规则 5）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockSnapshotServiceImpl extends ServiceImpl<StockSnapshotMapper, StockSnapshot>
        implements IStockSnapshotService
{
    /** 成本类字段的权限串（FR-4.6 规则 5，全项目统一） */
    private static final String COST_VIEW_PERM = "inventory:cost:view";

    private final ReferenceGuard referenceGuard;

    private final StockSnapshotItemMapper itemMapper;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<StockSnapshot> selectStockSnapshotList(StockSnapshot snapshot)
    {
        List<StockSnapshot> list = baseMapper.selectStockSnapshotList(snapshot);
        if (!costVisible())
        {
            for (StockSnapshot row : list)
            {
                row.setTotalAmount(null);
            }
        }
        return list;
    }

    @Override
    public StockSnapshot selectStockSnapshotById(Long snapshotId)
    {
        StockSnapshot snapshot = referenceGuard.requireExists(baseMapper, snapshotId, "库存快照");
        if (!costVisible())
        {
            snapshot.setTotalAmount(null);
        }
        return snapshot;
    }

    // ==================================================================
    // 对比
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public SnapshotCompare compare(Long snapshotId, Long compareSnapshotId, LocalDate compareDate)
    {
        StockSnapshot base = referenceGuard.requireExists(baseMapper, snapshotId, "库存快照");
        StockSnapshot target = resolveCompareTarget(compareSnapshotId, compareDate);

        Map<Long, SnapshotBookAgg> before = indexByBook(itemMapper.selectBookAggBySnapshot(snapshotId));
        Map<Long, SnapshotBookAgg> after = indexByBook(itemMapper.selectBookAggBySnapshot(target.getSnapshotId()));

        boolean showCost = costVisible();

        // 并集：只在一侧出现的书也要进结果（缺的一侧按 0 算）——
        // 「新增了」与「清空了」正是对比页最该看出来的两种变化
        Map<Long, SnapshotBookAgg> union = new LinkedHashMap<>();
        union.putAll(before);
        for (Map.Entry<Long, SnapshotBookAgg> e : after.entrySet())
        {
            union.putIfAbsent(e.getKey(), e.getValue());
        }

        List<SnapshotCompare.Item> items = new ArrayList<>(union.size());
        int beforeQtyTotal = 0;
        int afterQtyTotal = 0;
        BigDecimal amountChangeTotal = BigDecimal.ZERO;

        for (Long bookId : union.keySet())
        {
            SnapshotBookAgg b = before.get(bookId);
            SnapshotBookAgg a = after.get(bookId);
            int beforeQty = (b == null) ? 0 : nzi(b.getQty());
            int afterQty = (a == null) ? 0 : nzi(a.getQty());
            BigDecimal beforeAmount = (b == null) ? BigDecimal.ZERO : nz(b.getAmount());
            BigDecimal afterAmount = (a == null) ? BigDecimal.ZERO : nz(a.getAmount());
            BigDecimal amountChange = afterAmount.subtract(beforeAmount);

            SnapshotCompare.Item item = new SnapshotCompare.Item();
            item.setBookId(bookId);
            item.setIsbn((a != null) ? a.getIsbn() : (b == null ? null : b.getIsbn()));
            item.setTitle((a != null) ? a.getTitle() : (b == null ? null : b.getTitle()));
            item.setBeforeQty(beforeQty);
            item.setAfterQty(afterQty);
            item.setQtyChange(afterQty - beforeQty);
            item.setAmountChange(showCost ? amountChange : null);
            items.add(item);

            beforeQtyTotal += beforeQty;
            afterQtyTotal += afterQty;
            amountChangeTotal = amountChangeTotal.add(amountChange);
        }

        // 按「变化量绝对值」倒序：复核的人先看变动大的，而不是先看书的 ID
        items.sort(Comparator
                .comparingInt((SnapshotCompare.Item it) -> Math.abs(nzi(it.getQtyChange())))
                .reversed()
                .thenComparing(SnapshotCompare.Item::getBookId));

        SnapshotCompare result = new SnapshotCompare();
        result.setSnapshotId(snapshotId);
        result.setSnapshotDate(base.getSnapshotDate());
        result.setCompareSnapshotId(target.getSnapshotId());
        result.setCompareSnapshotDate(target.getSnapshotDate());
        result.setItems(items);

        SnapshotCompare.Summary summary = new SnapshotCompare.Summary();
        summary.setBeforeQtyTotal(beforeQtyTotal);
        summary.setAfterQtyTotal(afterQtyTotal);
        summary.setQtyChangeTotal(afterQtyTotal - beforeQtyTotal);
        summary.setAmountChangeTotal(showCost ? amountChangeTotal : null);
        result.setSummary(summary);
        return result;
    }

    /**
     * 解析对比对象：ID 优先，其次按日期找。
     *
     * <p>两个都没给 → 422（契约「未指定对比对象」）；给了日期而那天没有快照 → 404
     * —— 「那一天还没生成快照」与「你指错了单子」都是「找不到可比的东西」，
     * 但文案分开写，前端提示才好照抄（原型 A-27 的空态就是「该日快照尚未生成或已归档」）。
     */
    private StockSnapshot resolveCompareTarget(Long compareSnapshotId, LocalDate compareDate)
    {
        if (compareSnapshotId != null)
        {
            return referenceGuard.requireExists(baseMapper, compareSnapshotId, "库存快照");
        }
        if (compareDate == null)
        {
            throw BizErrors.ruleViolated("未指定对比对象：请给出 compareSnapshotId 或 compareDate");
        }
        StockSnapshot byDate = baseMapper.selectByDate(compareDate);
        if (byDate == null)
        {
            throw BizErrors.notFound("该日期（" + compareDate + "）未生成库存快照，无法对比");
        }
        return byDate;
    }

    private Map<Long, SnapshotBookAgg> indexByBook(List<SnapshotBookAgg> rows)
    {
        Map<Long, SnapshotBookAgg> map = new LinkedHashMap<>();
        for (SnapshotBookAgg row : rows)
        {
            map.put(row.getBookId(), row);
        }
        return map;
    }

    /**
     * 当前用户是否可看成本类字段。
     *
     * <p>无登录上下文时（定时任务、匿名调用）返回 false —— 与
     * {@code InventoryOverviewServiceImpl} / {@code BookServiceImpl} 同一口径。
     */
    private boolean costVisible()
    {
        try
        {
            return SecurityUtils.hasPermi(COST_VIEW_PERM);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }
}
