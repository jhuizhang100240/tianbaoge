package com.tbgbook.bookstore.stock.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.StockSnapshotItem;
import com.tbgbook.bookstore.stock.mapper.StockSnapshotItemMapper;
import com.tbgbook.bookstore.stock.service.IStockSnapshotItemService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 库存快照明细 Service 实现。
 *
 * <p>只读实现，唯一的处理是**成本字段按权限裁剪**（FR-4.6 规则 5）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockSnapshotItemServiceImpl extends ServiceImpl<StockSnapshotItemMapper, StockSnapshotItem>
        implements IStockSnapshotItemService
{
    /** 成本类字段的权限串（FR-4.6 规则 5，全项目统一） */
    private static final String COST_VIEW_PERM = "inventory:cost:view";

    private final ReferenceGuard referenceGuard;

    @Override
    public List<StockSnapshotItem> selectStockSnapshotItemList(StockSnapshotItem item)
    {
        List<StockSnapshotItem> list = baseMapper.selectStockSnapshotItemList(item);
        if (!costVisible())
        {
            for (StockSnapshotItem row : list)
            {
                hideCost(row);
            }
        }
        return list;
    }

    @Override
    public StockSnapshotItem selectStockSnapshotItemById(Long itemId)
    {
        StockSnapshotItem item = referenceGuard.requireExists(baseMapper, itemId, "库存快照明细");
        if (!costVisible())
        {
            hideCost(item);
        }
        return item;
    }

    /** 无成本权限时把两个成本列置 null —— 置 0 会被误解成「成本为 0」 */
    private void hideCost(StockSnapshotItem row)
    {
        row.setCostPrice(null);
        row.setAmount(null);
    }

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
}
