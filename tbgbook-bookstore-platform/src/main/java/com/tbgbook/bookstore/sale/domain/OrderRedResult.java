package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/** 订单红冲结果（`POST /sales/order/{orderId}/red`）。 */
@Data
public class OrderRedResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 原单号 */
    private String originOrderNo;

    /** 红冲单号 */
    private String redOrderNo;

    private Long redOrderId;

    /** 退回金额 */
    private BigDecimal redAmount;

    /** 红冲后逐行库存结存 */
    private List<String> stockAfter;
}
