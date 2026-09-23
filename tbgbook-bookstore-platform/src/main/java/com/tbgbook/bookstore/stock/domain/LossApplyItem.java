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

import jakarta.validation.constraints.Min;

import lombok.Data;

/**
 * 报损申请明细对象 biz_loss_apply_item。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time}，
 * 没有 {@code create_by} / {@code update_*} / {@code remark} / {@code del_flag}。
 *
 * <p><b>三个派生列全由服务端写</b>（契约的 {@code skip}）：
 * {@code stockQty}（报损时点可用库存快照，用于校验不超库存）、
 * {@code unitCost}（取 {@code biz_book.cost_price}）、{@code amount = qty × unitCost}。
 *
 * <p>⚠️ <b>为什么 {@code unitCost} 也不接受客户端传入</b>：它决定单据头的
 * {@code totalAmount}，而后者是「超 500 元需二次确认」这道闸门的判据。
 * 客户端若能自填成本单价，该闸门即可被绕过；且漏传一次就会让金额静默归零、
 * 闸门永不触发。报损金额本就是**成本口径**，成本单价只有一个权威来源 —— {@code biz_book}。
 * 已登记 `plan/待确认项.md`。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_loss_apply_item")
public class LossApplyItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 报损单ID */
    @TableField("apply_id")
    private Long applyId;

    /**
     * 图书ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：本实体 POST/PUT 共用，PUT 允许只改数量。
     * 必填性由 {@code LossApplyItemServiceImpl#addLossApplyItem} 按**操作**判定
     * （与 {@code StockInItem#bookId} 同一处理）。
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
     * 库位ID（0=未分配库位）。
     *
     * <p>不加 {@code @NotNull}：本项目的库存行允许 {@code location_id = 0}
     * （见 {@code biz_stock.location_id} 注释），为 0 时由 service 显式判定。
     */
    @Min(value = 0, message = "库位ID不能为负")
    @TableField("location_id")
    private Long locationId;

    /**
     * 报损数量。
     *
     * <p>必填性由 service 按操作判定。下界为 1 —— 报损 0 册没有意义，
     * 且 0 值行会让明细行数与「品种数」对不上。
     */
    @Min(value = 1, message = "报损数量至少为 1")
    @TableField("qty")
    private Integer qty;

    /** 成本单价（服务端取 {@code biz_book.cost_price}） */
    @TableField("unit_cost")
    private BigDecimal unitCost;

    /** 成本金额（服务端算：qty × unitCost） */
    @TableField("amount")
    private BigDecimal amount;

    /** 报损时当前库存（服务端快照，用于校验不超库存） */
    @TableField("stock_qty")
    private Integer stockQty;

    /**
     * 创建时间。
     *
     * <p>⚠️ 本实体不继承 {@code BaseEntity}，必须自己声明 {@code @JsonFormat}
     * —— 否则 Jackson 会输出 ISO-8601 带时区的字符串，违反根 CLAUDE.md §6.1。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 请求参数（瞬态，不落库；理由同 {@code LossApply#params}） */
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
