package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 扫码查书结果里的单个库位库存（`GET /inventory/stock/scan`）。
 *
 * @author tbgbook
 */
@Data
public class ScanStock implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 库位ID */
    private Long locationId;

    /** 库位编码（如 A-3-2-1） */
    private String locationCode;

    /** 库位名称 */
    private String locationName;

    /** 所在区域 */
    private String zone;

    /** 可售库存 */
    private Integer qtyAvailable;

    /** 锁定库存 */
    private Integer qtyLocked;

    /** 借出库存 */
    private Integer qtyBorrowed;

    /** 残损库存 */
    private Integer qtyDamaged;
}
