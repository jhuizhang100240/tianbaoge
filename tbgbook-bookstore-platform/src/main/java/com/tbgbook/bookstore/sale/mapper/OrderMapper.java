package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.Order;

/** 销售订单 Mapper。 */
public interface OrderMapper extends BaseMapper<Order>
{
    List<Order> selectOrderList(Order order);
    /** 取下一个自增主键（用于预生成 orderNo） */
    Long selectNextOrderId();
}
