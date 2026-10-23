package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.ExchangeOrder;

/** 换货单 Mapper。 */
public interface ExchangeOrderMapper extends BaseMapper<ExchangeOrder>
{
    List<ExchangeOrder> selectExchangeOrderList(ExchangeOrder order);

    /** 取下一个自增主键（用于预生成 exchangeNo）。 */
    Long selectNextExchangeId();
}
