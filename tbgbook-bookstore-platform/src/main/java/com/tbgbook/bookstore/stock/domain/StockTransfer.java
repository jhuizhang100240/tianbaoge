package com.tbgbook.bookstore.stock.domain;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存调拨单对象 biz_stock_transfer（页面 A-26 库存调拨）。
 *
 * <p><b>调拨免审批</b>（Q22 已追认）：{@code confirm} 即时生效，
 * 没有「待审批」这一态。确认时调出库位扣减、调入库位累加，
 * **同一事务内写两条流水**（{@code biz_type=10 调拨出} 与 {@code 9 调拨入}），
 * 避免出现「已经出去了但还没进来」的中间态（FR-4.2.10）。
 *
 * <p><b>调拨前后实物总量不变</b>：只是把书从一个库位挪到另一个库位，
 * 本单不增不减任何一本。这一条是确认逻辑的断言 —— 四类数量之和在调拨前后必须相等。
 *
 * <p><b>{@code status} 三态取代了逻辑删除</b>：本表**没有 {@code del_flag}**，
 * 作废是 {@code status=3}，不是删行。沿用 {@link BaseEntity} 是安全的 ——
 * 它只提供审计四字段与 {@code remark}，**本身不含 {@code delFlag}**。
 *
 * <p><b>服务端接管、客户端传了会被忽略的字段</b>（契约的 {@code skip}）：
 * {@code transferNo}（DB+yyyyMMdd+流水）、{@code bookKind} / {@code totalQty}
 * （由明细聚合）、{@code status}（初始恒为 1 草稿）、{@code transferTime}（确认时才写）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_stock_transfer")
public class StockTransfer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：草稿。**调拨尚未发生，不动任何库存** */
    public static final int STATUS_DRAFT = 1;

    /** 状态：已调拨。库存已按本单挪过，**不可再改明细** */
    public static final int STATUS_TRANSFERRED = 2;

    /** 状态：已作废 */
    public static final int STATUS_VOIDED = 3;

    /** 调拨单ID */
    @TableId(value = "transfer_id", type = IdType.AUTO)
    private Long transferId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 调拨单号（DB+yyyyMMdd+流水）。**服务端生成** */
    @TableField("transfer_no")
    private String transferNo;

    /**
     * 调出库位ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：本实体 POST/PUT 共用，PUT 允许只改明细
     * （库位沿用原单）。在字段上标必填会拦掉这种合法请求 ——
     * 与 {@code StockInItem#bookId} 上踩过的是同一个坑。必填性由
     * {@code StockTransferServiceImpl#addStockTransfer} 按**操作**判定。
     */
    @TableField("from_location_id")
    private Long fromLocationId;

    /** 调入库位ID。必填性同 {@code fromLocationId}（由 service 按操作判定） */
    @TableField("to_location_id")
    private Long toLocationId;

    /** 品种数（由明细聚合，服务端维护） */
    @TableField("book_kind")
    private Integer bookKind;

    /** 总数量（由明细聚合，服务端维护） */
    @TableField("total_qty")
    private Integer totalQty;

    /**
     * 状态（1草稿 2已调拨 3已作废）。
     *
     * <p>⚠️ **刻意不加 {@code @Min}/{@code @Max}**：契约把 {@code status} 列在 `skip` 里
     * ——「客户端传了会被忽略」。若在字段上标范围校验，客户端传个 {@code status=9}
     * 会先被 {@code @Valid} 拦成 <b>400</b>，而契约说的是**忽略**，两者语义相反（实测踩到）。
     */
    @TableField("status")
    private Integer status;

    /** 调拨时间（确认调拨时才写） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("transfer_time")
    private LocalDateTime transferTime;

    // ------------------------------------------------------------------
    // 子表：与单据头**同事务**落库（契约「开单携带明细数组 items」）
    // ------------------------------------------------------------------

    /**
     * 明细数组（瞬态，不落本表）。
     *
     * <p>{@code @Valid} 会级联校验每个元素，明细的必填项因此也在「参数校验前置」
     * 的覆盖范围内（NFR-M2）。
     */
    @Valid
    @TableField(exist = false)
    private List<StockTransferItem> items;

    // ------------------------------------------------------------------
    // 以下为瞬态展示字段：只由列表查询的 JOIN 填充，不落库
    // ------------------------------------------------------------------

    /**
     * 调出库位编码（JOIN {@code biz_location}）。
     *
     * <p>契约的 {@code resp_extra}：原型 A-26 要展示「源库区 → 目标库区」，
     * 只给 {@code fromLocationId} 前端渲染不出可读的库位名，只能逐行回查（N+1）。
     */
    @TableField(exist = false)
    private String fromLocationCode;

    /** 调出库位名称（同上） */
    @TableField(exist = false)
    private String fromLocationName;

    /** 调出库位所在区域（同上） */
    @TableField(exist = false)
    private String fromLocationZone;

    /** 调入库位编码（同上） */
    @TableField(exist = false)
    private String toLocationCode;

    /** 调入库位名称（同上） */
    @TableField(exist = false)
    private String toLocationName;

    /** 调入库位所在区域（同上） */
    @TableField(exist = false)
    private String toLocationZone;
}
