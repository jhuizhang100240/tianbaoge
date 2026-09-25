package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.Payment;

/** 支付流水 Service（**只读**：写入由收银结算内部完成）。 */
public interface IPaymentService extends IService<Payment>
{
    List<Payment> selectPaymentList(Payment payment);

    Payment selectPaymentById(Long paymentId);
}
