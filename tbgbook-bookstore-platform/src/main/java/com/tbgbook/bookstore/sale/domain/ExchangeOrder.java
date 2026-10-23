package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
 * 换货单对象 biz_exchange_order（FR-4.3.6）。
 *
 * <p><b>{@code diffAmount} 由服务端计算：正数 = 会员补款，负数 = 退款</b>（契约原文）。
 * 确认换货时若差额不为 0 必须给支付方式（{@code payMethod}）。
 *
 * <p>换回的商品按与退货明细**同一套规则**判定库存去向（契约业务约束）：
 * 无损且选「回退可售」才回可售，否则不回（残次品不能重新上架）。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表有 {@code create_time} / {@code update_time}，
 * 但**没有** {@code create_by} / {@code update_by} / {@code remark} / {@code del_flag}
 * —— 操作人由 {@code exchangeBy} 承载。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_exchange_order")
public class ExchangeOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 状态：待处理 */
    public static final int STATUS_PENDING = 1;

    /** 状态：已完成 */
    public static final int STATUS_DONE = 2;

    /** 状态：已作废 */
    public static final int STATUS_VOIDED = 3;

    @TableId(value = "exchange_id", type = IdType.AUTO)
    private Long exchangeId;

    @TableField("store_id")
    private Long storeId;

    /** 换货单号（HH+yyyyMMdd+流水），**服务端生成** */
    @TableField("exchange_no")
    private String exchangeNo;

    @TableField("origin_order_id")
    private Long originOrderId;

    @TableField("origin_order_no")
    private String originOrderNo;

    @TableField("member_id")
    private Long memberId;

    /** 换出金额（**服务端算**：direction=1 的明细合计） */
    @TableField("out_amount")
    private BigDecimal outAmount;

    /** 换回金额（**服务端算**：direction=2 的明细合计） */
    @TableField("in_amount")
    private BigDecimal inAmount;

    /** 差额（**服务端算**）：正=补款 负=退款 */
    @TableField("diff_amount")
    private BigDecimal diffAmount;

    @TableField("status")
    private Integer status;

    @TableField("exchange_by")
    private String exchangeBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("exchange_time")
    private LocalDateTime exchangeTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 明细数组（瞬态） */
    @TableField(exist = false)
    private List<ExchangeOrderItem> items;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
