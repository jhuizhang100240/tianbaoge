package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 审批通过报损单的结果（`POST /inventory/loss/{applyId}/approve` 的 {@code data}）。
 *
 * <p>契约要求 {@code applyNo/totalQty/totalAmount/status/auditBy/auditTime}
 * 以及**逐行扣减后的结存** —— 后者是给审批人当场核对用的：
 * 点完「同意」立刻能看到每行扣完还剩多少，不必再点进库存明细页。
 * 与 {@code StockInConfirmResult} 的 {@code rows} 同思路。
 *
 * @author tbgbook
 */
@Data
public class LossApproveResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 报损单ID */
    private Long applyId;

    /** 报损单号 */
    private String applyNo;

    /** 总数量 */
    private Integer totalQty;

    /** 报损总金额（成本口径） */
    private BigDecimal totalAmount;

    /** 状态（审批后恒为 2 已通过） */
    private Integer status;

    /** 审批人 */
    private String auditBy;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    /** 逐行的库存变动结果 */
    private List<Row> rows;

    /** 单行结果：某本书在某库位报损后的结存 */
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

        /** 本次报损数量（正数，库存的实际变动量为它的相反数） */
        private Integer qtyChange;

        /** 扣减前该库位可售数 */
        private Integer stockBefore;

        /** 扣减后该库位可售数 */
        private Integer stockAfter;

        /** 成本单价 */
        private BigDecimal unitCost;

        /** 成本金额 */
        private BigDecimal amount;
    }
}
