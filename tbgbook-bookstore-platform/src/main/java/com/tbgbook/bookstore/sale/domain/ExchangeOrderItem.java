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
 * 换货单明细对象 biz_exchange_order_item。
 *
 * <p>{@code direction} 区分**换出(1)** 与 **换回(2)**：换出是给顾客的新书（出库），
 * 换回是顾客退回来的书（按退货明细同一套规则判定库存去向）。
 *
 * <p>{@code amount = qty × unitPrice} 由服务端计算（契约明写）——
 * 让客户端传金额等于把「算错钱」的权力交给前端。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_exchange_order_item")
public class ExchangeOrderItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 方向：换出（给顾客的新书，出库） */
    public static final int DIR_OUT = 1;

    /** 方向：换回（顾客退回的书，入库） */
    public static final int DIR_IN = 2;

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    @TableField("exchange_id")
    private Long exchangeId;

    /** 方向（1换出 2换回） */
    @TableField("direction")
    private Integer direction;

    @TableField("book_id")
    private Long bookId;

    @TableField("isbn")
    private String isbn;

    @TableField("title")
    private String title;

    @TableField("qty")
    private Integer qty;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 小计（**服务端算**：qty × unitPrice） */
    @TableField("amount")
    private BigDecimal amount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 请求参数（瞬态，不落库） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
