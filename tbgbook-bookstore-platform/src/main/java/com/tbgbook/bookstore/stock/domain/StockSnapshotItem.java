package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 库存快照明细对象 biz_stock_snapshot_item（页面 A-27 快照详情）。
 *
 * <p><b>只读</b>：与主表同属「系统产出、人只看」的一类。原契约把它误标为可增删改
 * （`kind=child`），与本表自己的「只读」约束及父资源的 `readonly` 自相矛盾，
 * 已修正（2026-09-23）：快照明细是**历史回溯的原始凭证**，
 * 开放手工增删改会让「某日的库存账面」可以被事后改写。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表**连 {@code create_time} 都没有**
 * （明细行与主表同事务写入，时间取主表的 {@code create_time} 即可），
 * 也没有 {@code del_flag}。故本实体没有任何审计字段，
 * 连 {@code @JsonFormat} 都不需要补 —— 这是全项目字段最少的一张业务表。
 *
 * <p><b>{@code costPrice} / {@code amount} 是成本类字段</b>（FR-4.6 规则 5）：
 * 无 {@code inventory:cost:view} 时返回 null，前端隐藏这两列。
 * 数量列（四类）不受权限影响。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock_snapshot_item")
public class StockSnapshotItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 快照ID */
    @TableField("snapshot_id")
    private Long snapshotId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余，生成时回填） */
    @TableField("isbn")
    private String isbn;

    /** 库位ID（0=未分配库位） */
    @TableField("location_id")
    private Long locationId;

    /** 可售库存（快照时点） */
    @TableField("qty_available")
    private Integer qtyAvailable;

    /** 锁定库存（快照时点） */
    @TableField("qty_locked")
    private Integer qtyLocked;

    /** 借出库存（快照时点） */
    @TableField("qty_borrowed")
    private Integer qtyBorrowed;

    /** 残损库存（快照时点） */
    @TableField("qty_damaged")
    private Integer qtyDamaged;

    /** 快照时点成本价（**成本类字段**，无 `inventory:cost:view` 时返回 null） */
    @TableField("cost_price")
    private BigDecimal costPrice;

    /** 库存金额 = 实物总量 × 成本价（**成本类字段**，同上） */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 请求参数（瞬态，不落库；日期区间筛选走 {@code params[...]}，理由同 {@code StockSnapshot#params}）。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
