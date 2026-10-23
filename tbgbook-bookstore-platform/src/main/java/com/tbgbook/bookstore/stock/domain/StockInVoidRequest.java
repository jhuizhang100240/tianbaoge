package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 作废入库单的请求体（`POST /inventory/stockin/{inId}/void`）。
 *
 * <p>⚠️ {@code reason} 在契约里是**必填**，但 {@code biz_stock_in} **没有作废原因列**
 * —— 它由 {@code StockInServiceImpl#appendReason} 并入 {@code remark}（加「【作废】」前缀）。
 * 让一个必填字段无处安放是不可接受的，故在此说明去向而不是静默丢弃。
 *
 * @author tbgbook
 */
@Data
public class StockInVoidRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 作废原因（必填） */
    @NotBlank(message = "作废原因不能为空")
    @Size(max = 200, message = "作废原因不能超过 200 个字符")
    private String reason;
}
