package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 二手书上架定价请求（`POST /usedbook/stock/{usedBookId}/shelf`）。
 *
 * @author tbgbook
 */
@Data
public class ShelfRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 二手售价 */
    private BigDecimal usedPrice;

    /** 库位ID（可空 = 沿用原库位） */
    private Long locationId;
}
