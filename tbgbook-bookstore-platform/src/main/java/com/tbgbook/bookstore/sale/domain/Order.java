package com.tbgbook.bookstore.sale.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 销售订单对象 biz_order（页面 A-28 收银台 / A-29 订单列表，FR-4.3）。
 *
 * <p><b>收银开单不建议直接 POST 本接口</b>（契约明写）：门店收银走
 * {@code POST /sales/cashier/checkout}、线上下单走 {@code POST /app/order/submit}、
 * 教材预订走 {@code POST /sales/reserve}。本资源的 POST **仅用于后台补录手工单**。
 *
 * <p><b>四类优惠分列存储</b>，对应固定计算顺序「单品折扣 → 会员等级折扣 → 优惠券 → 积分抵扣」
 * （§4.3 规则 2）。故四个优惠金额**都不接受客户端写入**（契约 `skip`），由服务端试算写入 ——
 * 让客户端直接填优惠额等于把「算错钱」的权力交给前端。
 *
 * <p><b>{@code memberLevelId} / {@code memberDiscountRate} 是下单时的折扣快照</b>：
 * 会员后续升级不影响历史单的金额。
 *
 * <p><b>{@code payExpireTime}</b> 默认 = 下单时间 + {@code biz.order.pay.timeout.minutes}（15 分钟），
 * 超时未支付自动取消并释放锁定库存（§4.3 规则 1）。
 *
 * <p><b>日结后订单锁定</b>（{@code isSettled=1}）不可改，调整必须走红冲
 * （{@code POST /{orderId}/red} 生成红冲单，{@code redOriginOrderId} 指向原单，永久保留，§4.3 规则 6）。
 *
 * <p><b>{@code needApproval} 由服务端判定</b>：折扣低于 {@code biz.discount.approval.rate}（0.7）
 * 或折扣总额超订单金额 {@code biz.discount.approval.ratio}（0.3）任一命中即置 1（§3.2 价格规则 2）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order")
public class Order extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 订单类型 / 状态（契约「销售订单」字段字典）
    // ------------------------------------------------------------------

    /** 类型：门店收银 */
    public static final int TYPE_STORE = 1;

    /** 类型：线上下单 */
    public static final int TYPE_ONLINE = 2;

    /** 类型：教材预订 */
    public static final int TYPE_RESERVE = 3;

    /** 状态：待支付 */
    public static final int STATUS_UNPAID = 1;

    /** 状态：已支付 */
    public static final int STATUS_PAID = 2;

    /** 状态：待取货 */
    public static final int STATUS_WAIT_PICKUP = 3;

    /** 状态：已完成 */
    public static final int STATUS_FINISHED = 4;

    /** 状态：已取消 */
    public static final int STATUS_CANCELLED = 5;

    /** 状态：退款中 */
    public static final int STATUS_REFUNDING = 6;

    /** 状态：已退款 */
    public static final int STATUS_REFUNDED = 7;

    /** 订单ID */
    @TableId(value = "order_id", type = IdType.AUTO)
    private Long orderId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 订单号（SO+yyyyMMddHHmmss+4位流水）。**服务端生成** */
    @TableField("order_no")
    private String orderNo;

    /** 订单类型（1门店收银 2线上下单 3教材预订） */
    @Min(value = 1, message = "订单类型取值 1~3")
    @Max(value = 3, message = "订单类型取值 1~3")
    @TableField("order_type")
    private Integer orderType;

    /** 会员ID（**0=散客**，故不加 @NotNull） */
    @Min(value = 0, message = "会员ID不能为负")
    @TableField("member_id")
    private Long memberId;

    /** 会员卡号（冗余） */
    @Size(max = 30, message = "会员卡号不能超过 30 个字符")
    @TableField("member_no")
    private String memberNo;

    /** 会员昵称（冗余） */
    @Size(max = 50, message = "会员昵称不能超过 50 个字符")
    @TableField("member_name")
    private String memberName;

    /** 下单时会员等级ID（**折扣快照**） */
    @TableField("member_level_id")
    private Long memberLevelId;

    /** 下单时等级折扣率（**折扣快照**，1.00 = 无折扣） */
    @TableField("member_discount_rate")
    private BigDecimal memberDiscountRate;

    /** 商品品种数（**服务端按明细聚合**） */
    @TableField("book_kind")
    private Integer bookKind;

    /** 商品总件数（**服务端按明细聚合**） */
    @TableField("total_qty")
    private Integer totalQty;

    /** 商品合计（原价小计，**服务端按明细聚合**） */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** ① 单品折扣金额（**服务端试算写入**） */
    @TableField("single_discount")
    private BigDecimal singleDiscount;

    /** ② 会员等级折扣金额（**服务端试算写入**） */
    @TableField("member_discount")
    private BigDecimal memberDiscount;

    /** ③ 优惠券抵扣金额（**服务端试算写入**） */
    @TableField("coupon_amount")
    private BigDecimal couponAmount;

    /** ④ 积分抵扣金额（**服务端试算写入**） */
    @TableField("point_amount")
    private BigDecimal pointAmount;

    /** 优惠合计（**服务端算**） */
    @TableField("discount_total")
    private BigDecimal discountTotal;

    /** 应付/实付金额（**服务端算**） */
    @TableField("pay_amount")
    private BigDecimal payAmount;

    /** 使用积分数（**服务端算**，100 积分 = 1 元） */
    @TableField("point_used")
    private Integer pointUsed;

    /** 本单获得积分（**服务端算**，消费 1 元 = 1 积分） */
    @TableField("point_gain")
    private Integer pointGain;

    /** 使用的优惠券ID（**单笔订单最多 1 张**，§3.2 价格规则 3） */
    @TableField("coupon_id")
    private Long couponId;

    /** 是否含特价商品（特价不参与优惠券） */
    @Min(value = 0, message = "是否含特价只能填 0 或 1")
    @Max(value = 1, message = "是否含特价只能填 0 或 1")
    @TableField("has_special_price")
    private Integer hasSpecialPrice;

    /** 是否触发折扣审批（**服务端判定**） */
    @TableField("need_approval")
    private Integer needApproval;

    /** 审批人 */
    @TableField("approval_by")
    private String approvalBy;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("approval_time")
    private LocalDateTime approvalTime;

    /**
     * 状态（1待支付 2已支付 3待取货 4已完成 5已取消 6退款中 7已退款）。
     *
     * <p>⚠️ **刻意不加范围校验**：契约把 {@code status} 列在 `skip` 里（客户端传了会被忽略），
     * 状态流转走各动作端点（cancel / pickup / red）。标了注解会把「忽略」变成「拒绝」。
     */
    @TableField("status")
    private Integer status;

    /** 支付超时时间（默认 15 分钟，超时释放锁定库存） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("pay_expire_time")
    private LocalDateTime payExpireTime;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("pay_time")
    private LocalDateTime payTime;

    /** 取货码 */
    @Size(max = 20, message = "取货码不能超过 20 个字符")
    @TableField("pickup_code")
    private String pickupCode;

    /** 取货核销时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("pickup_time")
    private LocalDateTime pickupTime;

    /** 核销人 */
    @TableField("pickup_by")
    private String pickupBy;

    /** 完成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /** 是否红冲单（**服务端维护**） */
    @TableField("is_red")
    private Integer isRed;

    /** 红冲的原订单ID（**服务端维护**） */
    @TableField("red_origin_order_id")
    private Long redOriginOrderId;

    /** 是否已日结（**日结后锁定不可改**，**服务端维护**） */
    @TableField("is_settled")
    private Integer isSettled;

    /** 日结单ID（**服务端维护**） */
    @TableField("settle_id")
    private Long settleId;

    /** 来源挂单ID（0=非挂单转入） */
    @TableField("hold_id")
    private Long holdId;

    /** 收银员 */
    @Size(max = 64, message = "收银员不能超过 64 个字符")
    @TableField("cashier_by")
    private String cashierBy;

    // ------------------------------------------------------------------
    // 子表：与订单头**同事务**落库（契约 extra="items,payments"）
    // ------------------------------------------------------------------

    /** 订单明细数组（瞬态，不落本表） */
    @Valid
    @TableField(exist = false)
    private List<OrderItem> items;

    /** 支付流水数组（瞬态，不落本表；组合支付一单多条） */
    @Valid
    @TableField(exist = false)
    private List<Payment> payments;
}
