package com.tbgbook.bookstore.stock.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采购入库单对象 biz_stock_in（页面 A-16 采购入库开单 / A-15 入库单列表）。
 *
 * <p><b>这是库存数量的唯一合法入口</b>（FR-4.2 规则 1）：四类库存都只能由单据改动，
 * 建档时写不进数量。{@code confirm} 才真正累加库存并写
 * {@link StockFlow 库存流水}、重算移动加权平均成本并写 {@link CostChangeLog 成本变更流水}。
 * <b>草稿状态不占用库存</b>。
 *
 * <p><b>{@code status} 三态取代了逻辑删除</b>：本表**没有 {@code del_flag}**，
 * 作废是 {@code status=3}，不是删行。沿用 {@link BaseEntity} 是安全的 ——
 * 它只提供审计四字段与 {@code remark}，**本身不含 {@code delFlag}**。
 *
 * <p><b>服务端接管、客户端传了会被忽略的字段</b>（契约的 {@code skip}）：
 * {@code inNo}（RK+yyyyMMdd+流水）、{@code bookKind} / {@code totalQty} / {@code totalAmount}
 * （由明细聚合）、{@code status}（初始恒为 1 草稿）、{@code inTime}（确认时才写）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_stock_in")
public class StockIn extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：草稿。**草稿不占库存**，可自由改明细 */
    public static final int STATUS_DRAFT = 1;

    /** 状态：已入库。已累加库存并写过流水，**不可再改明细** */
    public static final int STATUS_CONFIRMED = 2;

    /** 状态：已作废 */
    public static final int STATUS_VOIDED = 3;

    /** 入库类型：采购入库 */
    public static final int TYPE_PURCHASE = 1;

    /** 入库类型：调拨入库 */
    public static final int TYPE_TRANSFER = 2;

    /** 入库类型：其他 */
    public static final int TYPE_OTHER = 3;

    /** 入库单ID */
    @TableId(value = "in_id", type = IdType.AUTO)
    private Long inId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 入库单号（RK+yyyyMMdd+流水）。**服务端生成** */
    @TableField("in_no")
    private String inNo;

    /** 入库类型（1采购入库 2调拨入库 3其他） */
    @Min(value = 1, message = "入库类型取值 1~3")
    @Max(value = 3, message = "入库类型取值 1~3")
    @TableField("in_type")
    private Integer inType;

    /** 供应商名称 */
    @Size(max = 100, message = "供应商名称不能超过 100 个字符")
    @TableField("supplier_name")
    private String supplierName;

    /** 品种数（由明细聚合，服务端维护） */
    @TableField("book_kind")
    private Integer bookKind;

    /** 总数量（由明细聚合，服务端维护） */
    @TableField("total_qty")
    private Integer totalQty;

    /** 总金额（成本口径，由明细聚合，服务端维护） */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 状态（1草稿 2已入库 3已作废）。
     *
     * <p>⚠️ **刻意不加 {@code @Min}/{@code @Max}**：契约把 {@code status} 列在 `skip` 里
     * ——「客户端传了会被忽略」。若在字段上标范围校验，客户端传个 {@code status=9}
     * 会先被 {@code @Valid} 拦成 <b>400</b>，而契约说的是**忽略**，两者语义相反
     * （实测踩到）。本字段由服务端无条件赋值（新增恒为 1 草稿），故不校验。
     *
     * <p>对照：{@code inType} **不在** `skip` 里、由客户端决定，所以它有 {@code @Min/@Max}。
     */
    @TableField("status")
    private Integer status;

    /** 入库时间（确认入库时才写） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("in_time")
    private LocalDateTime inTime;

    // ------------------------------------------------------------------
    // 子表：与单据头**同事务**落库（契约「开单携带明细数组 items」）
    // ------------------------------------------------------------------

    /**
     * 明细数组（瞬态，不落本表）。
     *
     * <p>{@code @Valid} 会级联校验每个元素 —— 明细的必填项因此也在「参数校验前置」
     * 的覆盖范围内（NFR-M2），不必等到 service 里手写一堆判空。
     */
    @Valid
    @TableField(exist = false)
    private List<StockInItem> items;
}
