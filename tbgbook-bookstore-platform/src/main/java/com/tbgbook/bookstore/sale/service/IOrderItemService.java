package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.OrderItem;

/** 订单明细 Service（本步骤只交付**只读**）。 */
public interface IOrderItemService extends IService<OrderItem>
{
    List<OrderItem> selectOrderItemList(OrderItem item);

    OrderItem selectOrderItemById(Long itemId);

    /**
     * 按 `book:cost:view` 裁剪成本字段（无权限时 `costPrice` 置 **null 而非报错**，FR-4.6 规则 5）。
     *
     * <p>⚠️ 用 `book:cost:view` 而非新造 `sales:order:cost:view`：这一列本就是**图书成本价**快照，
     * 而根 CLAUDE.md §6.1 把成本权限收敛为固定的 5 个。已登记 `plan/待确认项.md`。
     */
    List<OrderItem> maskCost(List<OrderItem> items);
}
