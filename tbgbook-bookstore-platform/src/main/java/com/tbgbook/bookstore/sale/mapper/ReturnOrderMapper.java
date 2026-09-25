package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.ReturnOrder;

/** 退货单 Mapper。 */
public interface ReturnOrderMapper extends BaseMapper<ReturnOrder>
{
    List<ReturnOrder> selectReturnOrderList(ReturnOrder order);

    /** 取下一个自增主键（用于预生成 returnNo；`return_no` 是 NOT NULL + 唯一索引）。 */
    Long selectNextReturnId();
}
