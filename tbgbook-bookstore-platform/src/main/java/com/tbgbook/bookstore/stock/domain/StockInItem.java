package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 采购入库单明细对象 biz_stock_in_item。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time}，
 * 没有 {@code create_by} / {@code update_*} / {@code remark} / {@code del_flag}。
 * 代价是审计时间的 {@code @JsonFormat} 与瞬态 {@code params} 要自己补。
 *
 * <p><b>两个派生列由服务端算</b>（契约明写）：
 * {@code diffQty = actualQty - orderQty}、{@code amount = actualQty × unitCost}。
 * 客户端传了会被忽略 —— 否则「差异数量」与「金额」可以被人为写歪，
 * 而它们正是收货差异核对与成本重算的依据。
 *
 * <p><b>{@code isbn} / {@code title} 是冗余列</b>：单据要能脱离图书表独立呈现
 * （图书改名后，历史单据应显示当时的书名）。由服务端按 {@code bookId} 回填。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock_in_item")
public class StockInItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 入库单ID */
    @TableField("in_id")
    private Long inId;

    /**
     * 图书ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：本实体 POST/PUT 共用，而 PUT 允许只改数量
     * （{@code bookId} 沿用原行）。在字段上标必填会让这种 PUT 被 {@code @Valid} 拦成 400
     * —— 与 {@code Stock} 上踩过的是同一个坑（那里是 {@code bookId} 挡住「只改库位」）。
     * 必填性由 {@code StockInItemServiceImpl#addStockInItem} 按操作判定。
     */
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余，服务端按 bookId 回填） */
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余，服务端按 bookId 回填） */
    @TableField("title")
    private String title;

    /**
     * 库位ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：该实体 POST/PUT 共用，而本项目的库存行允许
     * {@code location_id = 0}（未分配库位，见 {@code biz_stock.location_id} 注释）。
     * 为 0 时视为「未分配库位」，由 service 显式判定，不在字段上拦。
     */
    @Min(value = 0, message = "库位ID不能为负")
    @TableField("location_id")
    private Long locationId;

    /**
     * 采购数量。**必填性由 service 按操作判定**（新增必填、PUT 可沿用原值）——
     * 理由同 {@code bookId}。范围校验留着：{@code null} 不会触发 {@code @Min}，
     * 所以它与「PUT 可以省略」不冲突。
     */
    @Min(value = 0, message = "采购数量不能为负")
    @TableField("order_qty")
    private Integer orderQty;

    /**
     * 实收数量。**入库累加的是它，不是采购数量**。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：留空表示「无差异收货（订多少收多少）」，
     * 由 service 回填为 {@code orderQty}。开单时先只填采购数量、收货再补实收，
     * 是真实流程里最常见的情形，强制必填会让草稿开不出来。
     */
    @Min(value = 0, message = "实收数量不能为负")
    @TableField("actual_qty")
    private Integer actualQty;

    /** 差异数量（服务端算：actualQty - orderQty） */
    @TableField("diff_qty")
    private Integer diffQty;

    /**
     * 成本单价。必填性同 {@code orderQty}（由 service 按操作判定）。
     *
     * <p>⚠️ 它**不能为空**是硬要求：{@code amount = actualQty × unitCost} 是成本重算的输入，
     * 少一个单价整本书的成本就错了。故 service 在新增路径上显式拦。
     */
    @DecimalMin(value = "0", message = "成本单价不能为负")
    @TableField("unit_cost")
    private BigDecimal unitCost;

    /** 小计金额（服务端算：actualQty × unitCost） */
    @TableField("amount")
    private BigDecimal amount;

    /** 差异原因（有差异时必填，由 service 在确认入库前校验） */
    @Size(max = 200, message = "差异原因不能超过 200 个字符")
    @TableField("diff_reason")
    private String diffReason;

    /**
     * 创建时间。
     *
     * <p>⚠️ 本实体不继承 {@code BaseEntity}，拿不到它对审计时间的 {@code @JsonFormat}，
     * 因此**必须自己声明** —— 否则 Jackson 会输出 ISO-8601 带时区的
     * {@code 2026-09-23T11:45:05.000+08:00}，违反根 CLAUDE.md §6.1。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>列表接口的通用日期区间筛选走 {@code params[beginTime]} / {@code params[endTime]}，
     * 由 XML 读取 —— 不补这个 map 会让列表接口 500
     * （{@code There is no getter for property named 'params'}，实测踩到）。
     */
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
