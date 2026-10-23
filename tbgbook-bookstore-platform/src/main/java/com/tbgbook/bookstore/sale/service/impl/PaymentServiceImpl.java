package com.tbgbook.bookstore.sale.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.Payment;
import com.tbgbook.bookstore.sale.mapper.PaymentMapper;
import com.tbgbook.bookstore.sale.service.IPaymentService;

import lombok.RequiredArgsConstructor;

/** 支付流水 Service 实现（只读）。 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements IPaymentService
{
    private final ReferenceGuard referenceGuard;

    @Override
    public List<Payment> selectPaymentList(Payment payment)
    {
        return baseMapper.selectPaymentList(payment);
    }

    @Override
    public Payment selectPaymentById(Long paymentId)
    {
        return referenceGuard.requireExists(baseMapper, paymentId, "支付流水");
    }
}
