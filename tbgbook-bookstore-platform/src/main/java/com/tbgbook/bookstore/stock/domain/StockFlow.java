package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
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
 * 库存流水对象 biz_stock_flow（页面 A-17 库存流水）。
 *
 * <p><b>只增不改不删</b>（根 CLAUDE.md §6.2 点名的 7 张流水表之一）：
 * 表里**没有** {@code del_flag}、没有 {@code update_by} / {@code update_time}，
 * 因此：
 * <ul>
 *   <li>实体不继承 {@code BaseEntity} —— 继承会让 MP 去写不存在的列</li>
 *   <li>**只提供查询接口**（`/inventory/flow/list` 与 `/{flowId}` 两个端点），
 *       不提供 POST / PUT / DELETE。反例：把代码生成器产出的 CRUD 直接套上来，
 *       {@code removeByIds} 会退化成**物理删除**、{@code updateById} 会写不存在的列</li>
 * </ul>
 *
 * <p>每条含变动前 / 变动后结存（{@code qtyBefore} / {@code qtyAfter}），可完整回溯（FR-4.2 规则 1）。
 *
 * <p>{@code bizType} 12 种取值：1采购入库 2销售出库 3退货入库 4借出 5归还 6报损
 * 7盘盈 8盘亏 9调拨入 10调拨出 11二手入库 12预订锁定。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock_flow")
public class StockFlow implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    @TableId(value = "flow_id", type = IdType.AUTO)
    private Long flowId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** 库位ID */
    @TableField("location_id")
    private Long locationId;

    /** 库存类型（1可售 2锁定 3借出 4残损） */
    @TableField("stock_type")
    private Integer stockType;

    /** 业务类型（1采购入库 2销售出库 3退货入库 4借出 5归还 6报损 7盘盈 8盘亏 9调拨入 10调拨出 11二手入库 12预订锁定） */
    @TableField("biz_type")
    private Integer bizType;

    /** 业务单据号 */
    @TableField("biz_no")
    private String bizNo;

    /** 变动数量（正入负出） */
    @TableField("qty_change")
    private Integer qtyChange;

    /** 变动前结存 */
    @TableField("qty_before")
    private Integer qtyBefore;

    /** 变动后结存 */
    @TableField("qty_after")
    private Integer qtyAfter;

    /** 操作人 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 备注 */
    @TableField("remark")
    private String remark;

    // ------------------------------------------------------------------
    // 瞬态字段：只用于查询条件
    // ------------------------------------------------------------------

    /** ISBN（筛选用，JOIN `biz_book` 精确匹配） */
    @TableField(exist = false)
    private String isbn;

    /** 书名（筛选用，供前端展示与按书名找流水；不参与 SQL 条件） */
    @TableField(exist = false)
    private String title;

    /**
     * 请求参数（瞬态，不落库）。
     * 本实体不继承 {@code BaseEntity}，日期区间筛选走 {@code params[beginTime]}，故需自补。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
