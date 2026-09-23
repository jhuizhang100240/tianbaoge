package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 库存总览（`GET /inventory/overview/summary`，A-14 顶部指标卡）。
 *
 * <p>四类互斥，合计等于实物总量（FR-4.2 规则 2）—— 前端据此做恒等式校验提示。
 *
 * @author tbgbook
 */
@Data
public class StockOverview implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 可售库存合计 */
    private Integer qtyAvailable;

    /** 锁定库存合计 */
    private Integer qtyLocked;

    /** 借出库存合计 */
    private Integer qtyBorrowed;

    /** 残损库存合计 */
    private Integer qtyDamaged;

    /** 品种数（去重后的图书数） */
    private Integer bookKind;

    /**
     * 库存总金额（**成本口径**：{@code sum(实物总量 × 成本价)}）。
     *
     * <p>仅持有 {@code inventory:cost:view} 时返回；否则由服务端置 {@code null}
     * —— **不是报错**，前端据此隐藏该项（FR-4.6 规则 5）。
     */
    private BigDecimal totalAmount;

    /** 四类预警数量 */
    private WarningCounts warningCounts;
}
