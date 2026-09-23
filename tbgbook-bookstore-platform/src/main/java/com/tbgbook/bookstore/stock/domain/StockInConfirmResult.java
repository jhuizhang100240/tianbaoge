package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 确认入库的结果（`POST /inventory/stockin/{inId}/confirm` 的 {@code data}）。
 *
 * <p>契约要求返回 {@code inNo/totalQty/totalAmount/status} 以及
 * **逐行的 {@code stockAfter} 变动后结存** —— 后者是给操作员当场核对用的：
 * 入完库立刻能看到每本书在每个库位的结存，不必再点进库存明细页。
 *
 * @author tbgbook
 */
@Data
public class StockInConfirmResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 入库单ID */
    private Long inId;

    /** 入库单号 */
    private String inNo;

    /** 品种数 */
    private Integer bookKind;

    /** 总数量 */
    private Integer totalQty;

    /** 总金额（成本口径） */
    private BigDecimal totalAmount;

    /** 状态（确认后恒为 2 已入库） */
    private Integer status;

    /** 入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inTime;

    /** 逐行的库存变动结果 */
    private List<Row> rows;

    /** 单行结果：某本书在某库位入库后的结存 */
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

        /** 库位ID */
        private Long locationId;

        /** 本次入库数量（= 实收数量） */
        private Integer qtyChange;

        /** 入库前该库位可售数 */
        private Integer stockBefore;

        /** 入库后该库位可售数（契约里的 stockAfter） */
        private Integer stockAfter;

        /** 该书变更前成本价（移动加权平均） */
        private BigDecimal beforeCost;

        /** 该书变更后成本价 */
        private BigDecimal afterCost;
    }
}
