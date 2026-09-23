package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 确认调拨的结果（`POST /inventory/transfer/{transferId}/confirm` 的 {@code data}）。
 *
 * <p>契约只要求 {@code transferNo/totalQty/status}；这里**多返回了一个 {@code rows} 数组**
 * （逐行给出调出前 / 调出后、调入前 / 调入后的结存）。多给是安全的（响应超集），
 * 而它对操作员很有用：调拨是唯一会**同时改两个库位**的动作，只回单据号的话，
 * 现场无法当场确认两个库位都对了。与 {@code StockInConfirmResult} 的 {@code rows} 同思路。
 *
 * @author tbgbook
 */
@Data
public class StockTransferConfirmResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 调拨单ID */
    private Long transferId;

    /** 调拨单号 */
    private String transferNo;

    /** 品种数 */
    private Integer bookKind;

    /** 总数量 */
    private Integer totalQty;

    /** 状态（确认后恒为 2 已调拨） */
    private Integer status;

    /** 调拨时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transferTime;

    /** 逐行的两个库位结存变动 */
    private List<Row> rows;

    /** 单行结果：某本书在调出 / 调入库位的结存变化 */
    @Data
    public static class Row implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 明细ID */
        private Long itemId;

        /** 图书ID */
        private Long bookId;

        /** 书名（冗余，便于前端直接展示） */
        private String title;

        /** 本次调拨数量 */
        private Integer qty;

        /** 调出库位ID */
        private Long fromLocationId;

        /** 调出前该库位可售数 */
        private Integer fromStockBefore;

        /** 调出后该库位可售数 */
        private Integer fromStockAfter;

        /** 调入库位ID */
        private Long toLocationId;

        /** 调入前该库位可售数 */
        private Integer toStockBefore;

        /** 调入后该库位可售数 */
        private Integer toStockAfter;
    }
}
