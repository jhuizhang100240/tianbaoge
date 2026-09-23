package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 四类库存预警数量（A-14 指标卡）。
 *
 * <p>对应 `biz_stock_warning.warning_type` 的四档，**只统计待处理**（{@code handle_status=1}）。
 *
 * @author tbgbook
 */
@Data
public class WarningCounts implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 1 补货 */
    private Integer refill;

    /** 2 滞销 */
    private Integer slowMoving;

    /** 3 临期 */
    private Integer nearExpiry;

    /** 4 零库存 */
    private Integer zeroStock;
}
