package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 回收价试算请求（`POST /usedbook/grade/price-preview`）。
 *
 * @author tbgbook
 */
@Data
public class PricePreviewRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 基准价 */
    private BigDecimal basePrice;

    /** 成色等级ID（与 gradeCode 二选一） */
    private Long gradeId;

    /** 成色编码（与 gradeId 二选一） */
    private String gradeCode;
}
