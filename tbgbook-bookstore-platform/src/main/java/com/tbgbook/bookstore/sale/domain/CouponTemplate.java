package com.tbgbook.bookstore.sale.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 优惠券模板对象 biz_coupon_template（页面 A-37 券模板，FR-4.3.8）。
 *
 * <p><b>两种券型</b>：{@code 1满减券}（用 {@code faceValue} 面额）、
 * {@code 2折扣券}（用 {@code discountRate}）。
 *
 * <p><b>有效期两种模式</b>（契约业务约束）：{@code validityType=1} 固定日期区间
 * （用 {@code validStart}/{@code validEnd}）、{@code validityType=2} 领取后 N 天（用 {@code validDays}）。
 *
 * <p><b>门槛须 ≥ 面额</b>（满减券）：否则是「满 10 减 20」这种亏本券，保存时 422
 * （在 service 里判，因为要看两个字段的组合关系，字段注解办不到）。
 *
 * <p><b>{@code stackable} 的语义容易被误解</b>：全局规则是「单笔订单最多使用 1 张券」
 * （§3.2 价格规则 3），故置 1 **不表示可与其他券叠加**，只表示该券**可与活动价叠加**
 * —— 契约原文如此，写在字段注释里以免后续实现优惠计算时误读。
 *
 * <p><b>统计四列是服务端累加的</b>（发放/领取/核销）：{@code issuedQty} / {@code receivedQty} /
 * {@code usedQty} 与 {@code templateNo} 一并列在契约的 `skip` 里。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_coupon_template")
public class CouponTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 券类型：满减券 */
    public static final int TYPE_THRESHOLD = 1;

    /** 券类型：折扣券 */
    public static final int TYPE_DISCOUNT = 2;

    /** 有效期类型：固定日期区间 */
    public static final int VALIDITY_FIXED = 1;

    /** 有效期类型：领取后 N 天 */
    public static final int VALIDITY_RELATIVE = 2;

    /** 状态：草稿 */
    public static final int STATUS_DRAFT = 1;

    /** 状态：已发布 */
    public static final int STATUS_PUBLISHED = 2;

    /** 状态：已停用 */
    public static final int STATUS_DISABLED = 3;

    /** 适用范围：全场 */
    public static final int SCOPE_ALL = 1;

    /** 模板ID */
    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 模板编号（CT+yyyyMMdd+流水）。**服务端生成** */
    @TableField("template_no")
    private String templateNo;

    /** 券名称。必填性由 service 按**操作**判定 */
    @Size(max = 100, message = "券名称不能超过 100 个字符")
    @TableField("coupon_name")
    private String couponName;

    /** 券类型（1满减券 2折扣券） */
    @Min(value = 1, message = "券类型取值 1~2")
    @Max(value = 2, message = "券类型取值 1~2")
    @TableField("coupon_type")
    private Integer couponType;

    /** 面额（满减券用） */
    @DecimalMin(value = "0", message = "面额不能为负")
    @TableField("face_value")
    private BigDecimal faceValue;

    /** 折扣率（折扣券用；0.8 即 8 折，与 `biz.discount.approval.rate` 同口径） */
    @DecimalMin(value = "0", message = "折扣率不能为负")
    @DecimalMax(value = "1", message = "折扣率不能大于 1（0.8 即 8 折）")
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 使用门槛（满 X 元可用，**须 ≥ 面额**） */
    @DecimalMin(value = "0", message = "使用门槛不能为负")
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    /** 有效期类型（1固定日期区间 2领取后 N 天） */
    @Min(value = 1, message = "有效期类型取值 1~2")
    @Max(value = 2, message = "有效期类型取值 1~2")
    @TableField("validity_type")
    private Integer validityType;

    /** 生效时间（固定日期型） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("valid_start")
    private LocalDateTime validStart;

    /** 失效时间（固定日期型） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("valid_end")
    private LocalDateTime validEnd;

    /** 领取后有效天数（相对型） */
    @Min(value = 0, message = "有效天数不能为负")
    @TableField("valid_days")
    private Integer validDays;

    /** 发放总量（**服务端维护**，计划值，由新增/修改写入） */
    @Min(value = 0, message = "发放总量不能为负")
    @TableField("total_qty")
    private Integer totalQty;

    /** 已发放数量（**服务端累加**） */
    @TableField("issued_qty")
    private Integer issuedQty;

    /** 已领取数量（**服务端累加**） */
    @TableField("received_qty")
    private Integer receivedQty;

    /** 已核销数量（**服务端累加**） */
    @TableField("used_qty")
    private Integer usedQty;

    /** 每人限领数量 */
    @Min(value = 0, message = "每人限领数量不能为负")
    @TableField("per_limit")
    private Integer perLimit;

    /** 适用范围（1全场 2指定分类 3指定图书） */
    @Min(value = 1, message = "适用范围取值 1~3")
    @Max(value = 3, message = "适用范围取值 1~3")
    @TableField("scope_type")
    private Integer scopeType;

    /** 范围值（分类ID/图书ID列表，逗号分隔） */
    @Size(max = 1000, message = "范围值不能超过 1000 个字符")
    @TableField("scope_value")
    private String scopeValue;

    /**
     * 是否可叠加。
     *
     * <p>⚠️ 全局规则是「**单笔订单最多使用 1 张券**」（§3.2 价格规则 3），
     * 故置 1 **不表示可与其他券叠加**，只表示该券**可与活动价叠加**（契约原文）。
     */
    @Min(value = 0, message = "是否可叠加只能填 0 或 1")
    @Max(value = 1, message = "是否可叠加只能填 0 或 1")
    @TableField("stackable")
    private Integer stackable;

    /** 领券入口配置 */
    @Size(max = 200, message = "领券入口配置不能超过 200 个字符")
    @TableField("entry_config")
    private String entryConfig;

    /** 状态（1草稿 2已发布 3已停用） */
    @TableField("status")
    private Integer status;

    /** 删除标志（0存在 2删除）。**基础数据表**；`READ_ONLY` 防止客户端注入，见 `Promotion#delFlag` */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
