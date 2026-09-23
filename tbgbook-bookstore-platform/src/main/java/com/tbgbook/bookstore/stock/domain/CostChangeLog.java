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
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 成本变更流水对象 biz_cost_change_log（页面 A-18 成本变更流水）。
 *
 * <p><b>只增不删</b>：表里没有 {@code del_flag} / {@code update_*} / {@code remark}，
 * 实体不继承 {@code BaseEntity}，**只提供查询接口**（`/inventory/cost-log/list` 与 `/{logId}`）。
 *
 * <p>移动加权平均的每一次重算都在此留痕（FR-4.2）：记下本次入库的
 * 数量与单价、以及重算前后的成本价，供成本毛利追溯。
 *
 * <p><b>含成本类金额</b> —— 按 FR-4.6 规则 5，本资源只对持有
 * {@code inventory:cost:view} 的角色开放（接口文档把该资源的权限串直接定成
 * {@code inventory:cost:view}，**没有 {@code :list} 形态**，与其它资源的
 * {@code {前缀}:list} 惯例不同，这是契约明写的）。
 *
 * <p>{@code changeType} 4 种取值：1采购入库 2退货入库 3盘点调整 4手工调整。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_cost_change_log")
public class CostChangeLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** 变更类型（1采购入库 2退货入库 3盘点调整 4手工调整） */
    @TableField("change_type")
    private Integer changeType;

    /** 本次入库数量 */
    @TableField("qty")
    private Integer qty;

    /** 本次入库单价 */
    @TableField("unit_cost")
    private BigDecimal unitCost;

    /** 变更前成本价 */
    @TableField("before_cost")
    private BigDecimal beforeCost;

    /** 变更后成本价 */
    @TableField("after_cost")
    private BigDecimal afterCost;

    /** 关联业务单号 */
    @TableField("ref_no")
    private String refNo;

    /** 操作人 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ------------------------------------------------------------------
    // 瞬态字段：只用于查询条件
    // ------------------------------------------------------------------

    /** ISBN（筛选用，JOIN `biz_book` 精确匹配） */
    @TableField(exist = false)
    private String isbn;

    /** 书名（筛选用，供前端展示） */
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
