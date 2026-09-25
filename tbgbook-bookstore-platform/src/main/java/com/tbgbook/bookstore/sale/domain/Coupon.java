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
 * 会员优惠券实例对象 biz_coupon（页面 A-38 券发放 / 会员持券）。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time}，
 * 没有 {@code create_by} / {@code update_*} / {@code remark} / {@code del_flag}
 * —— 券的「操作人」体现为发放批次（{@code grantBatchId}）或补发记录（操作日志）。
 *
 * <p><b>面额与门槛是发放时的快照</b>：模板后续改名改值不影响已发出的券
 * （契约业务约束）。故 {@code faceValue} / {@code thresholdAmount} /
 * {@code validStart} / {@code validEnd} / {@code couponName} **全部由服务端从模板抄写**，
 * 客户端传了不认。
 *
 * <p><b>核销不开放手工接口</b>（契约明写）：{@code status} 的 1未使用→2已使用 由
 * **收银结算内部**完成并写 {@code useOrderId}/{@code useTime}；本资源的
 * {@code POST}/{@code PUT} 只用于**后台补录**（面额门槛仍从模板快照）。
 * {@code 3已过期} 由定时任务或查询时**惰性判定**（读到已过期但状态仍是 1 时顺手置 3）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_coupon")
public class Coupon implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 状态：未使用 */
    public static final int STATUS_UNUSED = 1;

    /** 状态：已使用 */
    public static final int STATUS_USED = 2;

    /** 状态：已过期 */
    public static final int STATUS_EXPIRED = 3;

    /** 券ID */
    @TableId(value = "coupon_id", type = IdType.AUTO)
    private Long couponId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 券码（服务端生成且唯一） */
    @TableField("coupon_code")
    private String couponCode;

    /**
     * 模板ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：本实体 POST/PUT 共用，而 PUT 允许只改备注类字段
     * （{@code templateId} 沿用原行）。必填性由 service 按**操作**判定。
     */
    @TableField("template_id")
    private Long templateId;

    /** 券名称（冗余，**发放时从模板快照**——模板改名不影响已发券） */
    @TableField("coupon_name")
    private String couponName;

    /** 持有会员ID。必填性同 {@code templateId}（由 service 按操作判定） */
    @TableField("member_id")
    private Long memberId;

    /** 面额（**发放时快照**，服务端从模板抄写） */
    @TableField("face_value")
    private BigDecimal faceValue;

    /** 门槛（**发放时快照**，服务端从模板抄写） */
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    /** 生效时间（**服务端按模板的有效期模式算**：固定型抄模板，相对型 = 领取时间 + validDays） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("valid_start")
    private LocalDateTime validStart;

    /** 失效时间（同上） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("valid_end")
    private LocalDateTime validEnd;

    /** 状态（1未使用 2已使用 3已过期）。**服务端维护**：核销与过期判定都不走本资源的写接口 */
    @TableField("status")
    private Integer status;

    /** 核销订单ID（收银结算写入） */
    @TableField("use_order_id")
    private Long useOrderId;

    /** 核销时间（收银结算写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("use_time")
    private LocalDateTime useTime;

    /** 发放批次ID（0 = 非批次发放，如人工补发） */
    @TableField("grant_batch_id")
    private Long grantBatchId;

    /** 领取时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("receive_time")
    private LocalDateTime receiveTime;

    /** 创建时间（⚠️ 不继承 BaseEntity，必须自己声明 @JsonFormat） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>⚠️ <b>必须补</b>：列表的通用日期区间筛选走 {@code params[beginTime]} /
     * {@code params[endTime]}（契约的列表筛选参数里就有），由 XML 读取 ——
     * 不补会让列表接口 500（{@code There is no getter for property named 'params'}）。
     * <b>本轮实测踩到</b>：`Coupon` 漏了这个字段，列表一调就报 MyBatis ReflectionException，
     * 而同批的 {@code CouponTemplate}（继承 BaseEntity）与 {@code CouponGrantBatch}（自己补了）
     * 都正常 —— 差别只在有没有这个 map。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
