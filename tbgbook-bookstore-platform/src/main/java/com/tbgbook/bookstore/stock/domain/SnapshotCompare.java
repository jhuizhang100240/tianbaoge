package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 库存快照对比结果（`GET /inventory/snapshot/{snapshotId}/compare` 的 {@code data}）。
 *
 * <p>把基准快照与另一份快照（按 ID 或按日期）逐图书相减，输出数量与金额增减
 * （FR-4.2.11 历史回溯 / 原型 A-27「支持任意两个快照日期的对比视图」）。
 *
 * <p><b>只出现在一侧的图书也要列出来</b>：某本书可能「新增了」（对比侧有、基准侧无）
 * 或「清空了」（基准侧有、对比侧无）。缺的一侧按 **0** 参与计算 ——
 * 直接把这类书过滤掉会漏掉最值得看的两种变化（新引进 / 已清空）。
 *
 * <p><b>成本类字段受权限保护</b>（FR-4.6 规则 5）：无 {@code inventory:cost:view} 时
 * {@link Item#getAmountChange()} 与 {@link Summary#getAmountChangeTotal()} 为
 * <b>null 而不是 0</b> —— 0 的意思是「金额没变」，与「不给你看」是两件事，
 * 混成一个值前端就没法正确隐藏这一列了。
 *
 * @author tbgbook
 */
@Data
public class SnapshotCompare implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 基准快照ID */
    private Long snapshotId;

    /** 基准快照日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate snapshotDate;

    /** 对比快照ID */
    private Long compareSnapshotId;

    /** 对比快照日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate compareSnapshotDate;

    /** 逐图书对比明细 */
    private List<Item> items;

    /** 汇总 */
    private Summary summary;

    /** 单书对比行 */
    @Data
    public static class Item implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 图书ID */
        private Long bookId;

        /** ISBN */
        private String isbn;

        /** 书名 */
        private String title;

        /** 基准快照的实物总量（该书只在对比侧出现时为 0） */
        private Integer beforeQty;

        /** 对比快照的实物总量（该书只在基准侧出现时为 0） */
        private Integer afterQty;

        /** 数量增减 = afterQty − beforeQty */
        private Integer qtyChange;

        /** 金额增减（成本口径）；无 `inventory:cost:view` 时为 null */
        private BigDecimal amountChange;
    }

    /** 汇总块 */
    @Data
    public static class Summary implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 基准快照实物总量 */
        private Integer beforeQtyTotal;

        /** 对比快照实物总量 */
        private Integer afterQtyTotal;

        /** 数量增减合计 = afterQtyTotal − beforeQtyTotal */
        private Integer qtyChangeTotal;

        /** 金额增减合计（成本口径）；无 `inventory:cost:view` 时为 null */
        private BigDecimal amountChangeTotal;
    }
}
