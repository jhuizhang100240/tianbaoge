package com.tbgbook.bookstore.sale.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.sale.domain.Payment;
import com.tbgbook.bookstore.sale.service.IPaymentService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 支付流水 Controller（**只读**：一单可多条，组合支付）。
 *
 * <p>权限串复用 `sales:order:list`（契约如此），不为流水另设前缀。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/payment")
public class PaymentController extends BaseController
{
    private final IPaymentService paymentService;

    @PreAuthorize("@ss.hasPermi('sales:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(Payment payment)
    {
        startPage();
        List<Payment> list = paymentService.selectPaymentList(payment);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('sales:order:list')")
    @GetMapping("/{paymentId}")
    public AjaxResult getInfo(@PathVariable("paymentId") Long paymentId)
    {
        return success(paymentService.selectPaymentById(paymentId));
    }
}
