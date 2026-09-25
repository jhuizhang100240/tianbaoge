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
 * 订单明细对象 biz_order_item（A-28 收银台明细 / A-29 订单明细）。
 *
 * <p><b>只读（随订单创建写入）</b>：本步骤只提供 list + 详情；
 * 写入路径（POST/PUT/DELETE）属步骤④的订单写入一并交付。
 *
 * <p><b>优惠按金额比例分摊到行</b>（§4.3 规则 5）：
 * {@code singleDiscount} / {@code memberDiscount} / {@code couponShare} / {@code pointShare}
 * 四列之和 = 该行分摊优惠，{@code shareAmount} 为分摊后实付金额，**是毛利核算口径**。
 * 故这五列与 {@code amount} 全部由服务端算，不接受客户端写入。
 *
 * <p><b>{@code costPrice} 是成交时点成本价快照，含成本口径</b> ——
 * 无 {@code book:cost:view} 权限时**返回 null 而不是报错**（FR-4.6 规则 5）。
 * ⚠️ 用 {@code book:cost:view} 而不是新造一个 {@code sales:order:cost:view}：
 * 这一列本就是**图书成本价**的快照，而根 CLAUDE.md §6.1 把成本权限**收敛为固定的 5 个**
 * （dashboard / book / inventory / member 的 `cost:view` + `inventory:loss:approve`），
 * 新造第 6 个会让那条清单失去「固定」的意义。已登记 `plan/待确认项.md`。
 *
 * <p><b>二手书行 {@code qty} 恒为 1</b> 且 {@code usedBookId} 有值、{@code bookId=0}；正价书行反之。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_order_item")
public class OrderItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书类型：正价书 */
    public static final int BOOK_TYPE_NORMAL = 1;

    /** 图书类型：二手书 */
    public static final int BOOK_TYPE_USED = 2;

    /** 成交价来源：定价 */
    public static final int PRICE_TYPE_LIST = 1;

    /** 成交价来源：会员价 */
    public static final int PRICE_TYPE_MEMBER = 2;

    /** 成交价来源：活动价 */
    public static final int PRICE_TYPE_ACTIVITY = 3;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 订单ID */
    @TableField("order_id")
    private Long orderId;

    /** 订单号（冗余） */
    @TableField("order_no")
    private String orderNo;

    /** 图书ID（正价书；二手书为 0） */
    @TableField("book_id")
    private Long bookId;

    /** 二手书副本ID（正价书为 0） */
    @TableField("used_book_id")
    private Long usedBookId;

    /** ISBN（冗余） */
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余） */
    @TableField("title")
    private String title;

    /** 图书类型（1正价书 2二手书） */
    @TableField("book_type")
    private Integer bookType;

    /** 二手书成色等级（正价书为空） */
    @TableField("condition_grade")
    private String conditionGrade;

    /** 成交价来源（1定价 2会员价 3活动价，取优先级最高者） */
    @TableField("price_type")
    private Integer priceType;

    /** 原价（定价/二手标价） */
    @TableField("origin_price")
    private BigDecimal originPrice;

    /** 成交单价 */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 数量（二手书恒为 1） */
    @TableField("qty")
    private Integer qty;

    /** 小计（unit_price × qty，**服务端算**） */
    @TableField("amount")
    private BigDecimal amount;

    /** 本行分摊的单品折扣（**服务端按比例分摊**） */
    @TableField("single_discount")
    private BigDecimal singleDiscount;

    /** 本行分摊的会员等级折扣（同上） */
    @TableField("member_discount")
    private BigDecimal memberDiscount;

    /** 本行分摊的券优惠（同上） */
    @TableField("coupon_share")
    private BigDecimal couponShare;

    /** 本行分摊的积分抵扣（同上） */
    @TableField("point_share")
    private BigDecimal pointShare;

    /** 本行分摊后实付金额（**毛利核算口径**，服务端算） */
    @TableField("share_amount")
    private BigDecimal shareAmount;

    /** 成交时点成本价（快照，用于毛利）。**成本类字段**：无 `book:cost:view` 时返回 null */
    @TableField("cost_price")
    private BigDecimal costPrice;

    /** 创建时间（⚠️ 不继承 BaseEntity，必须自己声明 @JsonFormat） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
