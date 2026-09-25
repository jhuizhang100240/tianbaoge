package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 生成日结对账请求（`POST /sales/settlement/generate`）。
 *
 * <p>{@code settleDate} 必填；{@code shift} 与 {@code shiftId} 都是选填 ——
 * 不给就是「当天全店」，给了 {@code shiftId} 才可能算出**现金差异**（需要交班实点数）。
 */
@Data
public class SettlementGenerateRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @NotNull(message = "对账日期不能为空")
    private LocalDate settleDate;

    /** 班次名（如 早班 08:00-15:00）；不传则汇总当天全部班次 */
    private String shift;

    /** 收银班次ID；传了才能算出交班现金差异 */
    private Long shiftId;
}
