package com.tbgbook.bookstore.sale.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.sale.domain.Payment;

/** 支付流水 Mapper（只读：写入由收银结算内部完成）。 */
public interface PaymentMapper extends BaseMapper<Payment>
{
    List<Payment> selectPaymentList(Payment payment);
}
