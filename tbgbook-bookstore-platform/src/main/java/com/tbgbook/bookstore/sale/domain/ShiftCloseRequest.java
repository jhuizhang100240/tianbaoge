package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** 交班请求（`POST /sales/shift/close`）：实点现金，与系统应收现金不符时记入日结差异。 */
@Data
public class ShiftCloseRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotNull(message = "班次ID不能为空")
    private Long shiftId;

    /** 交班现金实点金额 */
    @NotNull(message = "交班现金实点金额不能为空")
    private BigDecimal closeAmount;
}
