package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.ExchangeOrderItem;

/** 换货单明细 Mapper。 */
public interface ExchangeOrderItemMapper extends BaseMapper<ExchangeOrderItem>
{
    List<ExchangeOrderItem> selectExchangeOrderItemList(ExchangeOrderItem item);

    int deleteByExchangeIds(@Param("exchangeIds") Long[] exchangeIds);

    /**
     * 某原订单里**已经换回过的**某本书的册数（按 `origin_order_id + book_id` 定位）。
     *
     * <p>只算**已完成**(status=2)的换货单：待处理/已作废的不该占用换货额度。
     *
     * <p>⚠️ 换货明细**没有** `order_item_id` 列（表结构如此），无法按原订单明细挂钩，
     * 故只能退一步按「原单 + 图书」聚合 —— 同一本书在原单里出现多行时会被合并计算。
     *
     * @return 已换回册数；从未换过返回 0
     */
    int sumExchangedInQty(@Param("originOrderId") Long originOrderId, @Param("bookId") Long bookId);
}
