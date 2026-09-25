package com.tbgbook.bookstore.sale.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 交班结果：订单笔数 / 收款总额 / 实点金额 / **现金差异** / 交班时间。
 *
 * <p>{@code cashDiff = 实点现金 − 系统现金收款}（正数=长款、负数=短款）。
 */
@Data
public class ShiftCloseResult
{
    private Long shiftId;
    private String shiftCode;
    private String shiftName;
    private Integer orderCount;
    private BigDecimal totalAmount;

    /** 系统现金收款（同窗口内现金支付的实付合计） */
    private BigDecimal cashAmount;

    /** 交班现金实点金额 */
    private BigDecimal closeAmount;

    /** 现金差异 = closeAmount − cashAmount */
    private BigDecimal cashDiff;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closeTime;
}
