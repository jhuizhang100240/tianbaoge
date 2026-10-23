package com.tbgbook.bookstore.sale.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.mapper.OrderItemMapper;
import com.tbgbook.bookstore.sale.service.IOrderItemService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/** 订单明细 Service 实现（阶段四步骤③，只读 + 成本裁剪）。 */
@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem>
        implements IOrderItemService
{
    /** 成本类字段的权限串（用图书成本权限，理由见接口注释） */
    private static final String COST_VIEW_PERM = "book:cost:view";

    private final ReferenceGuard referenceGuard;

    @Override
    public List<OrderItem> selectOrderItemList(OrderItem item)
    {
        return maskCost(baseMapper.selectOrderItemList(item));
    }

    @Override
    public OrderItem selectOrderItemById(Long itemId)
    {
        OrderItem item = referenceGuard.requireExists(baseMapper, itemId, "订单明细");
        if (!costVisible())
        {
            item.setCostPrice(null);
        }
        return item;
    }

    @Override
    public List<OrderItem> maskCost(List<OrderItem> items)
    {
        if (items == null || costVisible())
        {
            return items;
        }
        for (OrderItem it : items)
        {
            it.setCostPrice(null);
        }
        return items;
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
