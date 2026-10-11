package com.tbgbook.bookstore.member.domain;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员等级对象 biz_member_level（页面 A-46，FR-4.4.3）。
 *
 * <p><b>借阅额度挂在此表</b>（可借册数 / 借期 / 押金 / 可否续借），与 A-46 页面表格一致。
 * 默认四档（需求 §3.2）：门槛 0/1000/5000/20000，折扣 1.00/0.95/0.90/0.85，
 * 可借 1/2/3/5 册，借期 3/7/14/30 天，押金建议值 50/50/100/0。
 *
 * <p><b>默认等级</b>：{@code isDefault=1} 的等级是注册时自动分配的初始等级，
 * <b>有且仅有一个</b> —— 设置新默认等级时服务端在同一事务内清除其他行的标记；
 * 默认等级禁止删除（删掉会让注册流程没有初始等级可用）。
 *
 * <p><b>删除保护</b>：被 {@code biz_member.level_id} 引用的等级禁止删除（422），
 * 与「分类被图书引用禁止删除」同一模式（{@code docs/db/README.md} §6.1）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_member_level")
public class MemberLevel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 等级ID */
    @TableId(value = "level_id", type = IdType.AUTO)
    private Long levelId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 等级编码（唯一） */
    @NotBlank(message = "等级编码不能为空")
    @Size(max = 30, message = "等级编码不能超过 30 个字符")
    @TableField("level_code")
    private String levelCode;

    /** 等级名称 */
    @NotBlank(message = "等级名称不能为空")
    @Size(max = 50, message = "等级名称不能超过 50 个字符")
    @TableField("level_name")
    private String levelName;

    /** 等级序号（数字越大等级越高） */
    @NotNull(message = "等级序号不能为空")
    @Min(value = 1, message = "等级序号最小为 1")
    @TableField("level_rank")
    private Integer levelRank;

    /** 晋升成长值门槛 */
    @NotNull(message = "晋升成长值门槛不能为空")
    @Min(value = 0, message = "成长值门槛不能为负")
    @TableField("growth_threshold")
    private Integer growthThreshold;

    /** 消费折扣率（1.00=无折扣，0.95=9.5 折） */
    @NotNull(message = "消费折扣率不能为空")
    @DecimalMin(value = "0.01", message = "折扣率必须大于 0")
    @DecimalMax(value = "1.00", message = "折扣率不能大于 1.00")
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 可借册数上限 */
    @NotNull(message = "可借册数不能为空")
    @Min(value = 0, message = "可借册数不能为负")
    @TableField("borrow_qty_limit")
    private Integer borrowQtyLimit;

    /** 借期（天） */
    @NotNull(message = "借期不能为空")
    @Min(value = 0, message = "借期不能为负")
    @TableField("borrow_days")
    private Integer borrowDays;

    /** 借阅押金（0=免押金） */
    @NotNull(message = "借阅押金不能为空")
    @DecimalMin(value = "0", message = "押金不能为负")
    @TableField("deposit_amount")
    private BigDecimal depositAmount;

    /** 是否可续借（0否 1是） */
    @Min(value = 0, message = "是否可续借取值必须是 0 或 1")
    @Max(value = 1, message = "是否可续借取值必须是 0 或 1")
    @TableField("can_renew")
    private Integer canRenew;

    /** 是否默认等级（0否 1是；全表有且仅有一个） */
    @Min(value = 0, message = "是否默认等级取值必须是 0 或 1")
    @Max(value = 1, message = "是否默认等级取值必须是 0 或 1")
    @TableField("is_default")
    private Integer isDefault;

    /** 状态（0正常 1停用） */
    @Pattern(regexp = "^[01]$", message = "状态取值必须是 0（正常）或 1（停用）")
    @TableField("status")
    private String status;

    /** 删除标志（0存在 2删除）。**基础数据表**；`READ_ONLY` 防止客户端注入，见 `Promotion#delFlag` */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
