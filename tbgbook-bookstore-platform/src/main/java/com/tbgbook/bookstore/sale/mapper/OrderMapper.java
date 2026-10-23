package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.Order;

/** 销售订单 Mapper。 */
public interface OrderMapper extends BaseMapper<Order>
{
    List<Order> selectOrderList(Order order);
    /**
     * 取下一个自增主键。
     *
     * @deprecated 收银结算已改为「先落订单头取真实主键，再生成 orderNo」，
     *             不再预生成单号（预测值与真实主键不一致会让库存流水挂错单号）。
     *             且本实现读 {@code information_schema.tables.auto_increment}，MySQL 8 会缓存该值。
     */
    @Deprecated
    Long selectNextOrderId();
}
