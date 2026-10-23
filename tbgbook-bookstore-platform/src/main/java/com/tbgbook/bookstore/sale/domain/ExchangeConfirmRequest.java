package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Data;

/**
 * 确认换货请求（`POST /sales/exchange/{exchangeId}/confirm`）。
 *
 * <p>{@code payMethod} 在 {@code diffAmount != 0} 时**必填**（契约错误码：422 差额不为 0 但未传支付方式）
 * —— 由 service 判（注解不知道差额是多少）。
 */
@Data
public class ExchangeConfirmRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 补退款支付方式（1现金 2微信 3支付宝 4储值余额 5校园卡） */
    @Min(value = 1, message = "支付方式取值 1~5")
    @Max(value = 5, message = "支付方式取值 1~5")
    private Integer payMethod;
}
