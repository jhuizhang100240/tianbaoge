package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.OrderHold;

/**
 * 收银挂单 Mapper。
 *
 * <p>⚠️ 列表查询**不带 `cart_data`**（json 列）：挂单列表按单号/金额/时间/会员筛，
 * 不需要购物车内容；详情（`selectById`）走 MP 的 autoResultMap 才带。
 */
public interface OrderHoldMapper extends BaseMapper<OrderHold>
{
    List<OrderHold> selectOrderHoldList(OrderHold hold);

    /** 取下一个自增主键（用于预生成 holdNo；`hold_no` 是 NOT NULL + 唯一索引）。 */
    Long selectNextHoldId();
}
