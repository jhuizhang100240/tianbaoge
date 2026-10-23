package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 发起二次盘点的结果（`POST /inventory/stockcheck/{checkId}/recheck` 的 {@code data}）。
 *
 * <p>契约要求返回新单的 {@code checkId}/{@code checkNo} 以及
 * **原盘点单号 {@code originCheckNo}**（让复核人能一路追溯回去）。
 *
 * <p>⚠️ {@code biz_stock_check} **没有 `origin_check_id` 列**，
 * 故「新单出身于哪张原单」只能写进 {@code remark}（加「【二次盘点】」标记），
 * 与作废入库单记原因是同一处理。要真正可查询需要加列，已登记 `plan/待确认项.md`。
 *
 * @author tbgbook
 */
@Data
public class StockCheckRecheckResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 新盘点单ID */
    private Long checkId;

    /** 新盘点单号 */
    private String checkNo;

    /** 原盘点单号（追溯用） */
    private String originCheckNo;

    /** 新单纳入的差异行数 */
    private Integer itemCount;
}
