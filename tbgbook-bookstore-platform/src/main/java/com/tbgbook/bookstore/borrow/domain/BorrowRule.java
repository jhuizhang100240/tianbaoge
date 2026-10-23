package com.tbgbook.bookstore.borrow.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 借阅全局规则对象 biz_borrow_rule（页面 A-57，FR-4.5.1）。
 *
 * <p><b>单行配置</b>：{@code uk_store_id} 保证一门店一条；等级相关额度（可借册数 / 借期 /
 * 押金 / 是否可续借）在 {@code biz_member_level}，**不在此表**（设计决策）。
 * {@code PUT} 按 storeId 更新已有行而非新增。
 *
 * <p>默认值来自需求 §3.2：罚金 0.2 元/册/天、单册封顶定价的 50%、丢失/严重破损赔偿
 * 定价的 80%、最多续借 1 次、每次延长 14 天、预约到馆保留 3 天、到期前 3 天提醒、
 * 逾期后每 3 天催还、有逾期禁止新借阅。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_borrow_rule")
public class BorrowRule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 规则ID */
    @TableId(value = "rule_id", type = IdType.AUTO)
    private Long ruleId;

    /** 门店ID（单店恒为 1，唯一） */
    @TableField("store_id")
    private Long storeId;

    /** 逾期罚金（元/册/天） */
    @DecimalMin(value = "0.00", message = "逾期罚金不能为负")
    @DecimalMax(value = "999999.99", message = "逾期罚金超出范围")
    @TableField("fine_per_day")
    private BigDecimal finePerDay;

    /** 单册罚金封顶比例（定价的百分比，0~1） */
    @DecimalMin(value = "0.00", message = "罚金封顶比例不能为负")
    @DecimalMax(value = "1.00", message = "罚金封顶比例不能超过 1")
    @TableField("fine_max_rate")
    private BigDecimal fineMaxRate;

    /** 丢失/严重破损赔偿比例（定价的百分比，0~1） */
    @DecimalMin(value = "0.00", message = "赔偿比例不能为负")
    @DecimalMax(value = "1.00", message = "赔偿比例不能超过 1")
    @TableField("compensate_rate")
    private BigDecimal compensateRate;

    /** 最多续借次数 */
    @Min(value = 0, message = "续借次数不能为负")
    @Max(value = 99, message = "续借次数不能超过 99")
    @TableField("renew_times")
    private Integer renewTimes;

    /** 每次续借延长天数 */
    @Min(value = 1, message = "续借天数至少 1 天")
    @Max(value = 365, message = "续借天数不能超过 365 天")
    @TableField("renew_days")
    private Integer renewDays;

    /** 预约到馆后保留天数 */
    @Min(value = 1, message = "预约保留天数至少 1 天")
    @Max(value = 365, message = "预约保留天数不能超过 365 天")
    @TableField("reserve_hold_days")
    private Integer reserveHoldDays;

    /** 到期前提醒天数 */
    @Min(value = 0, message = "提醒天数不能为负")
    @Max(value = 365, message = "提醒天数不能超过 365 天")
    @TableField("remind_days_before")
    private Integer remindDaysBefore;

    /** 逾期后催还提醒间隔天数 */
    @Min(value = 1, message = "催还间隔至少 1 天")
    @Max(value = 365, message = "催还间隔不能超过 365 天")
    @TableField("overdue_remind_interval")
    private Integer overdueRemindInterval;

    /** 有逾期时是否禁止新借阅（0否 1是） */
    @Min(value = 0, message = "取值必须是 0 或 1")
    @Max(value = 1, message = "取值必须是 0 或 1")
    @TableField("block_overdue_borrow")
    private Integer blockOverdueBorrow;

    /** 状态（0正常 1停用） */
    @Pattern(regexp = "^[01]$", message = "状态取值必须是 0（正常）或 1（停用）")
    @TableField("status")
    private String status;
}
