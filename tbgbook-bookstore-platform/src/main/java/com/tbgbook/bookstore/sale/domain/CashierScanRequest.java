package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 扫码添加商品的请求体（`POST /sales/cashier/scan`）。
 *
 * <p>本 DTO **只服务这一个端点**，故能用注解就不必在 service 里手写判空
 * （与「共用实体不加必填注解」的处置不同）。
 *
 * @author tbgbook
 */
@Data
public class CashierScanRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** ISBN / 图书编号（BK...）/ 二手书条码（BC...） */
    @NotBlank(message = "扫码内容不能为空")
    private String code;

    /** 数量，默认 1（**二手书恒为 1**） */
    @Min(value = 1, message = "数量至少为 1")
    private Integer qty;
}
