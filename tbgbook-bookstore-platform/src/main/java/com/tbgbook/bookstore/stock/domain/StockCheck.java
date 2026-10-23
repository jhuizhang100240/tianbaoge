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
 * 库存盘点单对象 biz_stock_check（页面 A-19~A-22：创建 / 录入 / 差异报告）。
 *
 * <p><b>盘点要达成的不变量</b>：结束盘点后，被盘图书的**在场实物量 == 实盘数**。
 * 这是本单据存在的意义，也是自测的核心断言（见 {@code diffQty} 与
 * {@code StockCheckItem#bookQty} 的口径说明）。
 *
 * <p><b>状态机</b>：{@code 1进行中 → 2待复核}（差异率超阈值）、
 * {@code 1进行中 → 3已完成}（差异率达标或强制结束）、{@code → 4已作废}。
 * 「待复核」是关键设计：差异率超过 `biz.stocktake.diff.rate`（默认 2%）时
 * **不直接应用差异**，先让店长复核（§3.2 库存规则 4）。
 *
 * <p><b>{@code showBookQty} 是盲盘开关</b>：默认 0（**盲盘**，不显示账面数）——
 * 让盘点人独立报数，避免照着账面抄。置 1 为明盘。
 *
 * <p><b>统计列全部由明细聚合</b>，客户端传了会被忽略（契约的 {@code skip}）。
 * 本表**没有 {@code del_flag}**（作废是 {@code status=4}），
 * 沿用 {@link BaseEntity} 是安全的 —— 它只提供审计四字段与 {@code remark}。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_stock_check")
public class StockCheck extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：进行中。可录入实盘数 */
    public static final int STATUS_RUNNING = 1;

    /** 状态：待复核。差异率超阈值，**差异尚未应用**，等店长复核 */
    public static final int STATUS_PENDING_REVIEW = 2;

    /** 状态：已完成。差异已应用到库存 */
    public static final int STATUS_FINISHED = 3;

    /** 状态：已作废 */
    public static final int STATUS_VOIDED = 4;

    /** 盘点类型：全盘 */
    public static final int TYPE_FULL = 1;

    /** 盘点类型：抽盘 */
    public static final int TYPE_SAMPLE = 2;

    /** 范围类型：按分类（**含子分类**） */
    public static final int SCOPE_CATEGORY = 1;

    /** 范围类型：按库位 */
    public static final int SCOPE_LOCATION = 2;

    /** 范围类型：指定图书 */
    public static final int SCOPE_BOOKS = 3;

    /** 盘点单ID */
    @TableId(value = "check_id", type = IdType.AUTO)
    private Long checkId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 盘点单号（PD+yyyyMMdd+流水）。**服务端生成** */
    @TableField("check_no")
    private String checkNo;

    /** 盘点类型（1全盘 2抽盘） */
    @Min(value = 1, message = "盘点类型取值 1 或 2")
    @Max(value = 2, message = "盘点类型取值 1 或 2")
    @TableField("check_type")
    private Integer checkType;

    /** 范围类型（1按分类 2按库位 3指定图书） */
    @Min(value = 1, message = "范围类型取值 1~3")
    @Max(value = 3, message = "范围类型取值 1~3")
    @TableField("scope_type")
    private Integer scopeType;

    /** 范围值（分类ID / 库位ID / 图书ID列表，逗号分隔） */
    @Size(max = 500, message = "范围值不能超过 500 个字符")
    @TableField("scope_value")
    private String scopeValue;

    /**
     * 是否显示账面数（1明盘 0盲盘）。
     *
     * <p>⚠️ **默认 0 盲盘**，与 §3.2 库存规则 4 说的「明盘」模式存在口径张力
     * （需求说采用明盘模式，而建表注释与接口契约的默认值都是隐藏账面数）。
     * 已按**契约与建表注释**实现（默认盲盘、可切明盘），并把该张力登记在
     * `plan/待确认项.md`。切换动作按契约要求记入变更日志。
     */
    @Min(value = 0, message = "是否显示账面数取值 0 或 1")
    @Max(value = 1, message = "是否显示账面数取值 0 或 1")
    @TableField("show_book_qty")
    private Integer showBookQty;

    /** 账面品种数（由明细聚合，服务端维护） */
    @TableField("book_kind_total")
    private Integer bookKindTotal;

    /** 已盘品种数（由明细聚合，服务端维护） */
    @TableField("book_kind_checked")
    private Integer bookKindChecked;

    /** 账面总量（由明细聚合，服务端维护） */
    @TableField("qty_book_total")
    private Integer qtyBookTotal;

    /** 实盘总量（由明细聚合，服务端维护） */
    @TableField("qty_actual_total")
    private Integer qtyActualTotal;

    /** 盘盈数量（由明细聚合，服务端维护） */
    @TableField("qty_gain")
    private Integer qtyGain;

    /** 盘亏数量（由明细聚合，服务端维护） */
    @TableField("qty_loss")
    private Integer qtyLoss;

    /** 差异品种数（由明细聚合，服务端维护） */
    @TableField("diff_kind_count")
    private Integer diffKindCount;

    /** 差异率（%）（服务端算：差异品种数 ÷ 账面品种数 × 100） */
    @TableField("diff_rate")
    private BigDecimal diffRate;

    /** 是否触发二次盘点（差异率 > `biz.stocktake.diff.rate`，服务端维护） */
    @TableField("need_recheck")
    private Integer needRecheck;

    /** 状态（1进行中 2待复核 3已完成 4已作废） */
    @TableField("status")
    private Integer status;

    /** 开始时间（生成盘点单时写） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间（应用差异时写） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 明细数组（瞬态，不落本表）。{@code @Valid} 级联校验每个元素 */
    @Valid
    @TableField(exist = false)
    private List<StockCheckItem> items;
}
