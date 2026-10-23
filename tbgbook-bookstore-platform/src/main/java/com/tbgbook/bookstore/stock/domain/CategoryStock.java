package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 分类库存分布（`GET /inventory/overview/by-category`，A-14 饼图）。
 *
 * @author tbgbook
 */
@Data
public class CategoryStock implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 品种数 */
    private Integer bookKind;

    /** 实物总量（四类合计） */
    private Integer totalQty;

    /** 库存金额（成本口径；无权限时为 null） */
    private BigDecimal totalAmount;

    /** 占比（0~1，按金额算；金额不可见时按数量算） */
    private BigDecimal ratio;
}
