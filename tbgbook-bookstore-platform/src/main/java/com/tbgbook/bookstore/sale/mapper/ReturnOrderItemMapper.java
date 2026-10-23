package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.ReturnOrderItem;

/** 退货单明细 Mapper。 */
public interface ReturnOrderItemMapper extends BaseMapper<ReturnOrderItem>
{
    List<ReturnOrderItem> selectReturnOrderItemList(ReturnOrderItem item);

    int deleteByReturnIds(@Param("returnIds") Long[] returnIds);

    /**
     * 某原订单明细**已成功退掉的**数量（防超退）。
     *
     * <p>只统计**已完成**退货单（{@code status=2}）的明细：待审核/已驳回的不该占用可退额度
     * —— 否则一张被驳回的申请会把顾客的退货权利永久占掉。
     *
     * @param orderItemId 原订单明细ID
     * @return 已退数量；从未退过返回 0
     */
    int sumReturnedQty(@Param("orderItemId") Long orderItemId);
}
