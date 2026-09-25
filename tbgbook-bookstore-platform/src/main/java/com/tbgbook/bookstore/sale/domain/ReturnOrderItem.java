package com.tbgbook.bookstore.sale.domain;

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
 * 退货单明细对象 biz_return_order_item。
 *
 * <p>{@code refundAmount} 由服务端按实退金额计入；{@code bookCondition} 与 {@code stockTo}
 * 决定是否回退可售库存。
 *
 * <p>⚠️ <b>{@code bookCondition=2有损 时 {@code stockTo} 不能是 1回退可售</b>（契约错误码原文）：
 * 有损的书回可售会把残次品重新上架卖出去。这条组合校验在 service 里判。
 */
@Data
@TableName("biz_return_order_item")
public class ReturnOrderItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书状况：无损 */
    public static final int CONDITION_GOOD = 1;

    /** 图书状况：有损 */
    public static final int CONDITION_DAMAGED = 2;

    /** 库存去向：回退可售 */
    public static final int STOCK_TO_AVAILABLE = 1;

    /** 库存去向：转报损 */
    public static final int STOCK_TO_LOSS = 2;

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("return_id")
    private Long returnId;

    /** 原订单明细ID（无单退货为 0） */
    @TableField("order_item_id")
    private Long orderItemId;

    @TableField("book_id")
    private Long bookId;

    @TableField("used_book_id")
    private Long usedBookId;

    @TableField("isbn")
    private String isbn;

    @TableField("title")
    private String title;

    /** 原购买数量 */
    @TableField("buy_qty")
    private Integer buyQty;

    /** 本次退货数量 */
    @TableField("return_qty")
    private Integer returnQty;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 本行退款额（**服务端算**，按原明细分摊后实付金额比例） */
    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /** 图书状况（1无损 2有损） */
    @TableField("book_condition")
    private Integer bookCondition;

    /** 库存去向（1回退可售 2转报损） */
    @TableField("stock_to")
    private Integer stockTo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 请求参数（瞬态，不落库） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
