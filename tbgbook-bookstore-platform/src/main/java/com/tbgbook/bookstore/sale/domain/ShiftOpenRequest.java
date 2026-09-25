package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** 开班请求（`POST /sales/shift/open`，A-28）。 */
@Data
public class ShiftOpenRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 班次编码（如 MORNING 早班） */
    @NotBlank(message = "班次编码不能为空")
    private String shiftCode;

    /** 班次名称（如 早班 08:00-15:00）—— 日结的 shift 列用的就是它 */
    @NotBlank(message = "班次名称不能为空")
    private String shiftName;

    /** 开班备用金（现金找零备用） */
    @NotNull(message = "开班备用金不能为空")
    private BigDecimal openAmount;
}
