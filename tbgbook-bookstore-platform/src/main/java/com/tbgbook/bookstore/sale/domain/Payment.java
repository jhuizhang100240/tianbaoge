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
 * 支付流水对象 biz_payment（A-29 订单详情 / A-39 日结对账）。
 *
 * <p><b>只读</b>（契约 `kind=log`）：一单可多条（**组合支付**，FR-4.3.4）。
 * 写入由收银结算内部完成，不开放手工增删改 —— 支付流水是资金凭据。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表有 {@code create_time} / {@code update_time}，
 * 但**没有** {@code create_by} / {@code update_by} / {@code remark} / {@code del_flag}
 * —— 资金流水的操作人由订单的 {@code cashierBy} 承载。
 *
 * <p><b>{@code tradeNo} 是第三方交易号</b>，由支付回调写入（微信/支付宝）。
 * 本项目单机部署、无真实商户号，故**微信/支付宝的流水停在 {@code payStatus=1待支付}**，
 * 回调接口预留给阶段九联调（已登记 `plan/待确认项.md`）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_payment")
public class Payment implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 支付方式：现金 */
    public static final int METHOD_CASH = 1;

    /** 支付方式：微信 */
    public static final int METHOD_WECHAT = 2;

    /** 支付方式：支付宝 */
    public static final int METHOD_ALIPAY = 3;

    /** 支付方式：储值余额 */
    public static final int METHOD_BALANCE = 4;

    /** 支付方式：校园卡（契约注「待确认」，本期**保留枚举但不做特殊逻辑**，与现金同路径） */
    public static final int METHOD_CAMPUS_CARD = 5;

    /** 支付状态：待支付 */
    public static final int STATUS_UNPAID = 1;

    /** 支付状态：成功 */
    public static final int STATUS_SUCCESS = 2;

    /** 支付状态：失败 */
    public static final int STATUS_FAILED = 3;

    /** 支付状态：已退款 */
    public static final int STATUS_REFUNDED = 4;

    /** 支付ID */
    @TableId(value = "payment_id", type = IdType.AUTO)
    private Long paymentId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 订单ID */
    @TableField("order_id")
    private Long orderId;

    /** 订单号（冗余） */
    @TableField("order_no")
    private String orderNo;

    /** 支付流水号（服务端生成） */
    @TableField("pay_no")
    private String payNo;

    /** 支付方式（1现金 2微信 3支付宝 4储值余额 5校园卡） */
    @TableField("pay_method")
    private Integer payMethod;

    /** 支付金额 */
    @TableField("pay_amount")
    private BigDecimal payAmount;

    /** 支付状态（1待支付 2成功 3失败 4已退款） */
    @TableField("pay_status")
    private Integer payStatus;

    /** 第三方交易号（微信/支付宝回调） */
    @TableField("trade_no")
    private String tradeNo;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("pay_time")
    private LocalDateTime payTime;

    /** 已退款金额 */
    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /** 退款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("refund_time")
    private LocalDateTime refundTime;

    /** 创建时间（⚠️ 不继承 BaseEntity，必须自己声明 @JsonFormat） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
