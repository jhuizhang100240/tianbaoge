package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 二手书上架结果（`POST /usedbook/stock/{usedBookId}/shelf` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class ShelfResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 副本ID */
    private Long usedBookId;

    /** 副本条码 */
    private String barCode;

    /** 二手售价 */
    private BigDecimal usedPrice;

    /** 原价 */
    private BigDecimal originPrice;

    /** 相对原价的折扣率（售价 ÷ 原价，四位小数） */
    private BigDecimal discountRate;

    /** 库存状态（1在售） */
    private Integer stockStatus;

    /** 上架时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime shelfTime;
}
