package com.tbgbook.bookstore.usedbook.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二手书成色等级对象 biz_condition_grade（页面 A-42 关联配置，FR-4.2）。
 *
 * <p><b>C3 待确认档位，默认三档</b>：九九新 1.00 / 九成新 0.90 / 八成新 0.80。
 * {@code priceFactor} 是**相对基准价的系数**，回收价 = 基准价 × 系数；
 * **可由运营在后台调整，无需改代码**（C3 的可配置化要求）。
 *
 * <p>{@code gradeDesc} 是纠纷防范的关键字段 —— 写清有无笔记、破损等判定标准，
 * 供会员端提前预期（R6）。被回收明细引用的成色档禁止删除。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_condition_grade")
public class ConditionGrade extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 成色等级ID */
    @TableId(value = "grade_id", type = IdType.AUTO)
    private Long gradeId;

    /** 门店ID（单店部署恒为 1） */
    @TableField("store_id")
    private Long storeId;

    /** 成色编码（同店唯一） */
    @NotBlank(message = "成色编码不能为空")
    @Size(max = 20, message = "成色编码不能超过 20 个字符")
    @TableField("grade_code")
    private String gradeCode;

    /** 成色名称（九九新/九成新/八成新） */
    @NotBlank(message = "成色名称不能为空")
    @Size(max = 50, message = "成色名称不能超过 50 个字符")
    @TableField("grade_name")
    private String gradeName;

    /** 定价系数（相对基准价的比例，0~1） */
    @DecimalMin(value = "0.00", message = "定价系数不能为负")
    @DecimalMax(value = "1.00", message = "定价系数不能超过 1")
    @TableField("price_factor")
    private BigDecimal priceFactor;

    /** 成色判定说明（有无笔记、破损等，供会员预期管理，降低纠纷） */
    @Size(max = 500, message = "成色判定说明不能超过 500 个字符")
    @TableField("grade_desc")
    private String gradeDesc;

    /** 显示顺序 */
    @Min(value = 0, message = "显示顺序不能为负")
    @Max(value = 9999, message = "显示顺序超出范围")
    @TableField("order_num")
    private Integer orderNum;

    /** 状态（0正常 1停用） */
    @Pattern(regexp = "^[01]$", message = "状态取值必须是 0（正常）或 1（停用）")
    @TableField("status")
    private String status;

    /** 删除标志（0存在 2删除） */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
