package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 扫码查书结果（`GET /inventory/stock/scan`，A-14 扫码 / B-05 扫码查书）。
 *
 * <p>契约要点：**按 ISBN 汇总返回，并逐库位展开**，便于店员按库位找书；
 * 扫到二手副本条码时返回 {@code bookType=2} 与副本信息（二手书走独立库存池）。
 *
 * @author tbgbook
 */
@Data
public class ScanResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 正价书 */
    public static final int TYPE_NORMAL = 1;

    /** 二手书副本 */
    public static final int TYPE_USED = 2;

    /** 图书类型（1正价书 2二手书） */
    private Integer bookType;

    /** 图书ID（正价书）；二手副本为 {@code null} */
    private Long bookId;

    /** 二手副本ID（仅 {@code bookType=2}） */
    private Long usedBookId;

    /** 条码（仅 {@code bookType=2}，副本唯一条码） */
    private String barCode;

    /** 书名 */
    private String title;

    /** 定价（正价书取 `price`；二手副本取 `usedPrice` 二手售价） */
    private BigDecimal price;

    /** 封面地址 */
    private String coverUrl;

    /** 成色名称（仅 {@code bookType=2}，如「九成新」） */
    private String conditionGradeName;

    /** 各库位库存（仅 {@code bookType=1}；二手副本按册，无多库位展开） */
    private List<ScanStock> stocks;

    /**
     * 找书提示（如「A 区 3 排 2 层」）。
     *
     * <p>取**可售数最多的那个库位**拼出，前端直接展示，省一次解析。
     * 全部库位都无可售库存时为 {@code null}。
     */
    private String shelfHint;
}
