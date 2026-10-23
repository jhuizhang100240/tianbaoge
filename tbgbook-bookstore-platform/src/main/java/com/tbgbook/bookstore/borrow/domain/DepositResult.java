package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 押金缴纳 / 退还结果（`POST /borrow/deposit/pay` 与 `/refund` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class DepositResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 变动后押金余额 */
    private BigDecimal depositAfter;

    /** 等级名 */
    private String levelName;

    /** 该等级应缴押金 */
    private BigDecimal depositRequired;

    /** 是否已足额 */
    private Boolean paid;

    /** 退还时间（仅退还时返回） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundTime;
}
