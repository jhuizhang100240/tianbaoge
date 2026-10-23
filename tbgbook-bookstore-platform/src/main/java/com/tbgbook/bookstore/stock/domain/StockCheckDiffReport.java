package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 盘点差异报告（`GET /inventory/stockcheck/{checkId}/diff-report`，页面 A-22）。
 *
 * <p><b>为什么把两类差异分成两个数组</b>（§4.2 规则 5）：
 * 「盘点期差异」是盘点期间发生的正常出入库（卖掉了、调拨走了），
 * **不该记在盘点人头上**。混在一个列表里，盘点人就得逐条解释不属于自己的差异，
 * 复核人也分不清哪些要追责。故拆成 {@code diffItems}（盘点人的责任差异）
 * 与 {@code periodDiffItems}（盘点期差异）两组，各自独立呈现。
 *
 * @author tbgbook
 */
@Data
public class StockCheckDiffReport implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 盘点单ID */
    private Long checkId;

    /** 盘点单号 */
    private String checkNo;

    /** 状态（1进行中 2待复核 3已完成 4已作废） */
    private Integer status;

    /** 是否需二次盘点 */
    private Integer needRecheck;

    /** 差异率阈值（%，取自 `sys_config.biz.stocktake.diff.rate`） */
    private BigDecimal threshold;

    /** 汇总块 */
    private Summary summary;

    /** 盘点人责任差异（{@code isPeriodDiff=0} 且 {@code diffType != 0}） */
    private List<StockCheckItem> diffItems;

    /**
     * 盘点期差异（{@code isPeriodDiff=1}），**单独列出**。
     */
    private List<StockCheckItem> periodDiffItems;

    /** 处理建议（服务端按差异情况给一句可执行的话） */
    private String suggestion;

    /** 汇总块：与单据头同口径的一组统计 */
    @Data
    public static class Summary implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 账面品种数 */
        private Integer bookKindTotal;

        /** 已盘品种数 */
        private Integer bookKindChecked;

        /** 账面总量 */
        private Integer qtyBookTotal;

        /** 实盘总量 */
        private Integer qtyActualTotal;

        /** 盘盈数量 */
        private Integer qtyGain;

        /** 盘亏数量 */
        private Integer qtyLoss;

        /** 差异品种数 */
        private Integer diffKindCount;

        /** 差异率（%） */
        private BigDecimal diffRate;
    }
}
