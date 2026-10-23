package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 库存快照主表对象 biz_stock_snapshot（页面 A-27 库存快照）。
 *
 * <p><b>只读</b>（契约 `kind=readonly`）：快照由定时任务「库存快照生成」（每日 02:00）
 * 产出，人只看不写 —— 它是**历史回溯的原始凭证**（FR-4.2.11），
 * 允许手工增删改就等于允许事后改写「某日的库存账面」。
 * ⚠️ 生成逻辑属**阶段十**的定时任务范围（`plan/backend-plan.md` 阶段十），
 * 本步骤只交付读与对比；届时定时任务与明细**同事务**写入。
 *
 * <p><b>{@code uk_store_date} 保证每日每店一条</b>：定时任务重复执行不会造出两行。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time}，
 * 没有 {@code create_by} / {@code update_*} / {@code remark} / {@code del_flag}
 * —— 系统产出的数据没有「谁改的」这一问。代价是 {@code @JsonFormat}
 * 与瞬态 {@code params} 要自己补。
 *
 * <p><b>{@code totalAmount} 是成本类字段</b>（FR-4.6 规则 5）：无
 * {@code inventory:cost:view} 权限时**返回 null 而不是报错**，前端据此隐藏该列。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock_snapshot")
public class StockSnapshot implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 快照ID */
    @TableId(value = "snapshot_id", type = IdType.AUTO)
    private Long snapshotId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 快照日期（`uk_store_date` 的一部分，每日每店一条） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("snapshot_date")
    private LocalDate snapshotDate;

    /** 品种数 */
    @TableField("book_kind")
    private Integer bookKind;

    /** 实物总量 */
    @TableField("total_qty")
    private Integer totalQty;

    /** 可售总量 */
    @TableField("qty_available")
    private Integer qtyAvailable;

    /** 锁定总量 */
    @TableField("qty_locked")
    private Integer qtyLocked;

    /** 借出总量 */
    @TableField("qty_borrowed")
    private Integer qtyBorrowed;

    /** 残损总量 */
    @TableField("qty_damaged")
    private Integer qtyDamaged;

    /** 库存总金额（**成本口径**，成本类字段，无 `inventory:cost:view` 时返回 null） */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ------------------------------------------------------------------
    // 瞬态：只用于查询条件
    // ------------------------------------------------------------------

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>列表的日期区间筛选走 {@code params[beginSnapshotDate]} / {@code params[endSnapshotDate]}，
     * 由 XML 读取 —— 不补这个 map 会让列表接口 500
     * （{@code There is no getter for property named 'params'}，实测踩到）。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
