package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 回收价试算结果（`POST /usedbook/grade/price-preview` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class PricePreviewResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 成色名称 */
    private String gradeName;

    /** 定价系数 */
    private BigDecimal priceFactor;

    /** 回收价 = 基准价 × 系数 */
    private BigDecimal finalPrice;

    /** 计算式说明 */
    private String formula;
}
