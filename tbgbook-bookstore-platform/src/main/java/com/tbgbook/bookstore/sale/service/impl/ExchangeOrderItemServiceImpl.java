package com.tbgbook.bookstore.sale.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.ExchangeOrder;
import com.tbgbook.bookstore.sale.domain.ExchangeOrderItem;
import com.tbgbook.bookstore.sale.mapper.ExchangeOrderItemMapper;
import com.tbgbook.bookstore.sale.service.IExchangeOrderItemService;
import com.tbgbook.bookstore.sale.service.IExchangeOrderService;

import lombok.RequiredArgsConstructor;

/**
 * 换货单明细 Service 实现（阶段四步骤⑤）。
 *
 * <p><b>单价 + 小计一律服务端算</b>：调 {@link IExchangeOrderService#priceLine} 按父单的原订单定价
 * （换出按当前定价、换回按原明细分摊后实付单价），客户端传的价格一律忽略 ——
 * 让前端传金额等于把「算错钱」的权力交给前端。
 *
 * <p>与退货明细一致的约束：**只有「1待处理」的父单能增删改明细**，改完回算父单金额。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class ExchangeOrderItemServiceImpl extends ServiceImpl<ExchangeOrderItemMapper, ExchangeOrderItem>
        implements IExchangeOrderItemService
{
    private final ReferenceGuard referenceGuard;

    private final IExchangeOrderService exchangeOrderService;

    @Override
    public List<ExchangeOrderItem> selectExchangeOrderItemList(ExchangeOrderItem item)
    {
        return baseMapper.selectExchangeOrderItemList(item);
    }

    @Override
    public ExchangeOrderItem selectExchangeOrderItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "换货单明细");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addExchangeOrderItem(ExchangeOrderItem item)
    {
        ExchangeOrder parent = exchangeOrderService.requireEditable(item.getExchangeId(), "新增明细");
        exchangeOrderService.priceLine(parent, item);

        item.setItemId(null);
        int rows = baseMapper.insert(item);
        exchangeOrderService.recalcTotals(parent.getExchangeId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateExchangeOrderItem(ExchangeOrderItem item)
    {
        if (item.getItemId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的换货单明细");
        }
        ExchangeOrderItem current = referenceGuard.requireExists(baseMapper, item.getItemId(), "换货单明细");
        ExchangeOrder parent = exchangeOrderService.requireEditable(current.getExchangeId(), "修改明细");

        // 只接受「方向 / 数量」：单价与小计由服务端按父单重算，图书也不允许换（换书等于换一行）
        ExchangeOrderItem priced = new ExchangeOrderItem();
        priced.setDirection(item.getDirection() == null ? current.getDirection() : item.getDirection());
        priced.setBookId(current.getBookId());
        priced.setQty(item.getQty() == null ? current.getQty() : item.getQty());
        exchangeOrderService.priceLine(parent, priced);

        ExchangeOrderItem patch = new ExchangeOrderItem();
        patch.setItemId(current.getItemId());
        patch.setDirection(priced.getDirection());
        patch.setQty(priced.getQty());
        patch.setUnitPrice(priced.getUnitPrice());
        patch.setAmount(priced.getAmount());
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "换货单明细");
        exchangeOrderService.recalcTotals(parent.getExchangeId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeExchangeOrderItems(Long[] itemIds)
    {
        List<Long> idList = (itemIds == null) ? new ArrayList<>() : Arrays.asList(itemIds);
        var ids = referenceGuard.requireIds(idList, "换货单明细");
        Long exchangeId = null;
        for (var id : ids)
        {
            ExchangeOrderItem one = referenceGuard.requireExists(baseMapper, id, "换货单明细");
            Long eid = one.getExchangeId();
            exchangeOrderService.requireEditable(eid, "删除明细");
            // 一行明细只属于一张单；跨单批量删除无意义，显式拒绝以免删出半截
            if (exchangeId != null && !Objects.equals(exchangeId, eid))
            {
                throw BizErrors.badRequest("一次只能删除同一张换货单下的明细");
            }
            exchangeId = eid;
        }
        int rows = baseMapper.deleteByIds(ids);
        if (exchangeId != null)
        {
            exchangeOrderService.recalcTotals(exchangeId);
        }
        return rows;
    }
}
