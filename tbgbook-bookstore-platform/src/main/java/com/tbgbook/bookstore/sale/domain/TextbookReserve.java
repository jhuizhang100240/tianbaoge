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
 * 教材预订对象 biz_textbook_reserve（A-30 教材核销，FR-4.3.10）。
 *
 * <p><b>预订即锁库存</b>：{@code POST /sales/reserve} 成功后对应图书的 {@code qty_locked} 增加、
 * {@code qty_available} 减少，并写库存流水（{@code biz_type=12 预订锁定}）。
 *
 * <p><b>不收定金</b>（2026-09-21 澄清确认，§4.3 规则 7）：预订只锁库存，到店核销时按
 * **实际取书数量**结算付款 —— 多取补款、少取释放锁定库存。原型文档 Q14 的
 * 「定金 10 元/本、多退少补」**已作废**，本表亦无定金字段。
 *
 * <p>⚠️ <b>{@code lockedQty} 是「累计已取书数量」而不是「已锁定数量」</b> ——
 * DDL 注释与接口文档都这么写（可小于 {@code qty}），列名有歧义但语义一致。
 * 真正的「锁定」体现在库存行的 {@code qty_locked} 上。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time} / {@code update_time}，
 * **没有** {@code create_by} / {@code update_by} / {@code remark} / {@code del_flag}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_textbook_reserve")
public class TextbookReserve implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 状态：待到货 */
    public static final int STATUS_WAITING = 1;

    /** 状态：待到店取书 */
    public static final int STATUS_ARRIVED = 2;

    /** 状态：部分取书 */
    public static final int STATUS_PARTIAL = 3;

    /** 状态：已取完 */
    public static final int STATUS_DONE = 4;

    /** 状态：已取消 */
    public static final int STATUS_CANCELED = 5;

    @TableId(value = "reserve_id", type = IdType.AUTO)
    private Long reserveId;

    @TableField("store_id")
    private Long storeId;

    /** 预订单号（YD+yyyyMMdd+流水），**服务端生成** */
    @TableField("reserve_no")
    private String reserveNo;

    @TableField("member_id")
    private Long memberId;

    /** 教材清单ID（0 = 手工预订，不挂清单） */
    @TableField("list_id")
    private Long listId;

    /** 清单明细ID（0 = 未挂明细；核销时要回写该明细的已取数） */
    @TableField("list_item_id")
    private Long listItemId;

    @TableField("book_id")
    private Long bookId;

    @TableField("isbn")
    private String isbn;

    @TableField("title")
    private String title;

    /** 预订数量 */
    @TableField("qty")
    private Integer qty;

    /** **累计已取书数量**（分批取书累加，可小于 {@code qty}） */
    @TableField("locked_qty")
    private Integer lockedQty;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    /** 总金额（服务端算 = unitPrice × qty） */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 关联订单ID（**0 = 仅锁库存未支付**，取书时才真正成单） */
    @TableField("order_id")
    private Long orderId;

    /** 状态（1待到货 2待到店取书 3部分取书 4已取完 5已取消） */
    @TableField("status")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("reserve_time")
    private LocalDateTime reserveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("lock_time")
    private LocalDateTime lockTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("arrive_time")
    private LocalDateTime arriveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("notify_time")
    private LocalDateTime notifyTime;

    /** 取货码（**服务端生成**） */
    @TableField("pickup_code")
    private String pickupCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("pickup_time")
    private LocalDateTime pickupTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("cancel_time")
    private LocalDateTime cancelTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
