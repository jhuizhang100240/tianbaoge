package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.OrderItem;

/** 订单明细 Mapper。 */
public interface OrderItemMapper extends BaseMapper<OrderItem>
{
    List<OrderItem> selectOrderItemList(OrderItem item);
    /** 按订单主键删除全部明细（主从级联；本表无 del_flag，物理删） */
    int deleteByOrderIds(@org.apache.ibatis.annotations.Param("orderIds") Long[] orderIds);
}
