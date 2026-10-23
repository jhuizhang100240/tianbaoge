package com.tbgbook.bookstore.member.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 储值充值活动对象 biz_stored_activity（页面 A-48，FR-4.4.5）。
 *
 * <p>充 X 送 Y：充值金额命中活动档位时按 {@code giftAmount} 赠送；
 * {@code giftExpireDays=0} 表示赠送金长期有效，否则按天数过期。
 * {@code isRecommend=1} 的活动在小程序充值页优先展示；同一时间建议只启用一个活动。
 *
 * <p>本表**没有 {@code del_flag}**（非基础数据表的运营配置，删除为物理删除，
 * 但重放配置时注意：活动一经产生充值流水就不再被引用校验——流水只记金额与单号，不挂活动 ID）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_stored_activity")
public class StoredActivity extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 活动ID */
    @TableId(value = "activity_id", type = IdType.AUTO)
    private Long activityId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    @Size(max = 100, message = "活动名称不能超过 100 个字符")
    @TableField("activity_name")
    private String activityName;

    /** 充值金额（命中该档位才享受赠送） */
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
    @TableField("recharge_amount")
    private BigDecimal rechargeAmount;

    /** 赠送金额 */
    @NotNull(message = "赠送金额不能为空")
    @DecimalMin(value = "0", message = "赠送金额不能为负")
    @TableField("gift_amount")
    private BigDecimal giftAmount;

    /** 赠送金有效天数（0=长期有效） */
    @NotNull(message = "赠送金有效天数不能为空")
    @Min(value = 0, message = "赠送金有效天数不能为负")
    @TableField("gift_expire_days")
    private Integer giftExpireDays;

    /** 开始时间（可空=不限制） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间（可空=不限制） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 显示顺序 */
    @Min(value = 0, message = "显示顺序不能为负")
    @TableField("order_num")
    private Integer orderNum;

    /** 是否推荐（0否 1是） */
    @Min(value = 0, message = "是否推荐取值必须是 0 或 1")
    @Max(value = 1, message = "是否推荐取值必须是 0 或 1")
    @TableField("is_recommend")
    private Integer isRecommend;

    /** 状态（1启用 2停用） */
    @Min(value = 1, message = "状态取值必须是 1启用 或 2停用")
    @Max(value = 2, message = "状态取值必须是 1启用 或 2停用")
    @TableField("status")
    private Integer status;
}
