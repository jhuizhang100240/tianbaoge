package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 预警扫描的单书输入行（**不落库**，只作为扫描算法的输入）。
 *
 * <p>一次查询取回「判定四类预警所需的全部原始数据」，避免逐书回查（N+1）：
 * 扫描要遍历全店图书，逐书发 3 条 SQL 在门店规模上就是几万次往返。
 *
 * <p><b>字段口径</b>：
 * <ul>
 *   <li>{@code availableQty} / {@code totalQty} —— 可售与四类之和（`biz_stock` 聚合，无库存行的书为 0）</li>
 *   <li>{@code saleQty30d} —— 近 30 天**销售出库**数量（`biz_stock_flow.biz_type=2`，取负号还原为正数）</li>
 *   <li>{@code lastSaleTime} —— 最后一次销售时间；{@code firstFlowTime} —— 第一条流水时间，
 *       用于「从未动销」的书判定滞销（以首次入库时间起算，否则它永远不滞销）</li>
 *   <li>{@code textbookEndTime} —— 引用本书的**已发布**教材清单的结束时间（取最近的一份），
 *       临期判定的依据</li>
 * </ul>
 *
 * @author tbgbook
 */
@Data
public class WarningCandidate implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书ID */
    private Long bookId;

    /** ISBN */
    private String isbn;

    /** 书名 */
    private String title;

    /**
     * 图书状态（1在售 2下架 3缺货 4停售）。
     *
     * <p>只对**补货 / 零库存**两类生效：补货是为了卖，下架与停售的书不该提示补货。
     * **滞销 / 临期不看书状态** —— 「有库存的停售书」恰恰是最该被提醒清货的，
     * 用状态把它们排除掉等于把最需要处理的藏起来。
     */
    private Integer bookStatus;

    /** 当前可售数量（`biz_stock` 聚合；无库存行时为 0） */
    private Integer availableQty;

    /** 实物总量（四类之和；无库存行时为 0） */
    private Integer totalQty;

    /** 近30天销售出库数量 */
    private Integer saleQty30d;

    /** 最后动销时间（从未销售为 null） */
    private LocalDateTime lastSaleTime;

    /** 第一条库存流水时间（用于「从未动销」的书判定滞销起算点） */
    private LocalDateTime firstFlowTime;

    /** 引用本书的已发布教材清单的结束时间（无则为 null） */
    private LocalDateTime textbookEndTime;

    /**
     * 库里现有的安全库存（`biz_stock.safety_qty`，同书的各库位行一致故取 max）。
     *
     * <p>用来**跳过无变化的写回** —— 扫描每天都跑，全店每本书都无条件 UPDATE 一次
     * `update_time` 会是上万次无意义的写。
     */
    private Integer safetyQtyInDb;

    /** 扫描时算出的安全库存（服务端填，不来自 SQL） */
    private Integer safetyQty;

    /** 近30天日均销量（服务端算 = saleQty30d ÷ 30） */
    private BigDecimal avgSale30d;
}
