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
 * 退货单对象 biz_return_order（A-31 退货处理，FR-4.3.5）。
 *
 * <p><b>两条路径</b>：有原单退货**直接完成**（{@code status=2}）；**无单退货需店长审批**
 * （{@code isNoOrder=1}、{@code status=1待审核}，走 {@code POST /{returnId}/approve}，§4.3 规则 4）。
 *
 * <p><b>有原单退货的时限与状况</b>：结算后 {@code biz.return.days}（默认 7）个自然日内、
 * 图书无污损（§3.2 价格规则 4）。
 *
 * <p><b>退款资金按原支付方式分流</b>（§3.2 价格规则 6，2026-09-21 已确认）：
 * 储值→回原储值账户（本金/赠送金**分别**回退）、微信支付宝→原路退回、
 * 现金/校园卡→收银台手工登记并计入日结、混合支付**逐笔按实付金额回退**（不合并、不冲抵）。
 *
 * <p><b>退款金额一律服务端计算</b>（NFR-S9），禁止前端传值 —— 有原单时按原明细的
 * **分摊后实付金额**（{@code share_amount}）比例计算，与毛利口径一致。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_return_order")
public class ReturnOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 状态：待审核（仅无单退货） */
    public static final int STATUS_PENDING = 1;

    /** 状态：已完成 */
    public static final int STATUS_DONE = 2;

    /** 状态：已驳回 */
    public static final int STATUS_REJECTED = 3;

    /** 退货类型：整单退 */
    public static final int TYPE_WHOLE = 1;

    /** 退货类型：单品退 */
    public static final int TYPE_SINGLE = 2;

    @TableId(value = "return_id", type = IdType.AUTO)
    private Long returnId;

    @TableField("store_id")
    private Long storeId;

    /** 退货单号（TH+yyyyMMdd+流水），**服务端生成** */
    @TableField("return_no")
    private String returnNo;

    /** 原订单ID（**0 = 无单退货**） */
    @TableField("origin_order_id")
    private Long originOrderId;

    @TableField("origin_order_no")
    private String originOrderNo;

    @TableField("member_id")
    private Long memberId;

    @TableField("return_type")
    private Integer returnType;

    /** 是否无单退货（1=是，需店长审批） */
    @TableField("is_no_order")
    private Integer isNoOrder;

    @TableField("total_qty")
    private Integer totalQty;

    /** 退款金额（**服务端计算**，NFR-S9） */
    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /** 返还积分数（**已清零积分不返还** → 0，§4.3 规则 3） */
    @TableField("refund_point")
    private Integer refundPoint;

    /** 是否返还券（**已过期券不返还** → 0，§4.3 规则 3） */
    @TableField("refund_coupon")
    private Integer refundCoupon;

    @TableField("reason")
    private String reason;

    /** 图书状况（1无损 2有损） */
    @TableField("book_condition")
    private Integer bookCondition;

    @TableField("status")
    private Integer status;

    @TableField("audit_by")
    private String auditBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("audit_time")
    private LocalDateTime auditTime;

    @TableField("audit_remark")
    private String auditRemark;

    @TableField("return_by")
    private String returnBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("return_time")
    private LocalDateTime returnTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 明细数组（瞬态） */
    @TableField(exist = false)
    private List<ReturnOrderItem> items;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
