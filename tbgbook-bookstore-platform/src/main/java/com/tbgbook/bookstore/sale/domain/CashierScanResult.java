package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 扫码添加商品的结果（`POST /sales/cashier/scan` 的 {@code data}）。
 *
 * <p>契约要求 {@code bookType} / {@code bookId} / {@code usedBookId} / {@code isbn} /
 * {@code title} / {@code priceType} / {@code originPrice} / {@code unitPrice} /
 * {@code qty} / {@code qtyAvailable}。
 *
 * <p><b>两种书两条路</b>（契约明写）：**正价书按 ISBN 汇总数量**（同一本书加多次就是 qty 累加），
 * **二手书按册扫条码**（每个副本一个 {@code BC...} 码，qty 恒为 1）。
 * 故本响应同时给出 {@code bookId} 与 {@code usedBookId}，由前端决定把这一行加到哪一类里。
 *
 * <p><b>库存不足只提示不阻断</b>（契约明写）：{@code qtyAvailable} 如实返回，
 * 前端据此标黄提示；真正的拦截在**结算**时（那时才锁库存）。
 *
 * @author tbgbook
 */
@Data
public class CashierScanResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书类型（1正价书 2二手书） */
    private Integer bookType;

    /** 图书ID（二手书为 0） */
    private Long bookId;

    /** 二手书副本ID（正价书为 0） */
    private Long usedBookId;

    /** ISBN */
    private String isbn;

    /** 书名 */
    private String title;

    /** 成交价来源（1定价 2会员价 3活动价，**取优先级最高者**） */
    private Integer priceType;

    /** 原价（定价 / 二手标价） */
    private BigDecimal originPrice;

    /** 成交单价 */
    private BigDecimal unitPrice;

    /** 本次扫码的数量（二手书恒为 1） */
    private Integer qty;

    /**
     * 该商品当前**可售数量**（供前端提示，**不足不阻断**）。
     *
     * <p>正价书取该图书跨库位的可售合计；二手书恒为 1（副本唯一）。
     */
    private Integer qtyAvailable;
}
