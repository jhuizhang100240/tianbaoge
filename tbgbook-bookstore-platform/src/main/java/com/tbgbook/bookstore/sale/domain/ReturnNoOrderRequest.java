package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

/**
 * 无单退货请求（`POST /sales/return/no-order`，FR-4.3.5 / §4.3 规则 4）。
 *
 * <p>⚠️ **{@code refundAmount} 由客户端传入**（契约把它列为必填）—— 与「有原单退货」的
 * 服务端计算（NFR-S9）**口径不同**：无单退货没有原单可依据，金额无从算起，只能由收银员填。
 * 审批人（店长）在 `approve` 时对该金额负责。已登记 `plan/待确认项.md`。
 */
@Data
public class ReturnNoOrderRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID，0=散客 */
    private Long memberId;

    /** 退货明细 */
    @NotEmpty(message = "退货明细不能为空")
    private List<Item> items;

    /** 退款金额（无单退货无原单可算，故由收银员填、店长审批） */
    private BigDecimal refundAmount;

    /** 退货原因 */
    @NotBlank(message = "退货原因不能为空")
    private String reason;

    /** 一行退货明细 */
    @Data
    public static class Item implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private Long bookId;
        private Long usedBookId;
        private Integer returnQty;
        /** 图书状况（1无损 2有损） */
        private Integer bookCondition;
        /** 库存去向（1回退可售 2转报损） */
        private Integer stockTo;
    }
}
