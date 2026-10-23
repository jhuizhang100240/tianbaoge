package com.tbgbook.bookstore.member.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员标签定义对象 biz_member_tag（页面 A-50，FR-4.4.7）。
 *
 * <p>支持**手动标签**（{@code isRule=0}，走 {@code POST /member/tag/rel/mark}）与
 * **规则标签**（{@code isRule=1}，{@code tagRule} 存规则表达式，由「会员标签重算」维护关联，
 * 手工增删关联一律 422）。
 *
 * <p><b>规则表达式口径（自定，登记 A38）</b>：{@code 指标 运算符 数值}，
 * 例 {@code consume30d>=3}；指标白名单与语义见 {@code member.support.TagRule}。
 * 保存与重算时都会校验语法（422）。
 *
 * <p><b>删除保护</b>：被 {@code biz_member_tag_rel} 引用的标签禁止删除（422，建议改停用）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_member_tag")
public class MemberTag extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 标签ID */
    @TableId(value = "tag_id", type = IdType.AUTO)
    private Long tagId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 标签名称（同店唯一） */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 50, message = "标签名称不能超过 50 个字符")
    @TableField("tag_name")
    private String tagName;

    /** 标签类型（1消费偏好 2借阅偏好 3活跃度 4自定义） */
    @Min(value = 1, message = "标签类型取值必须是 1~4")
    @Max(value = 4, message = "标签类型取值必须是 1~4")
    @TableField("tag_type")
    private Integer tagType;

    /** 是否规则标签（0手动 1规则自动） */
    @Min(value = 0, message = "是否规则标签取值必须是 0 或 1")
    @Max(value = 1, message = "是否规则标签取值必须是 0 或 1")
    @TableField("is_rule")
    private Integer isRule;

    /** 规则表达式（规则标签用，如 consume30d>=3） */
    @Size(max = 500, message = "规则表达式不能超过 500 个字符")
    @TableField("tag_rule")
    private String tagRule;

    /** 标签颜色 */
    @Size(max = 20, message = "标签颜色不能超过 20 个字符")
    @TableField("color")
    private String color;

    /** 覆盖会员数（冗余；由打标/解标/重算维护，不接受直接写入） */
    @TableField("member_count")
    private Integer memberCount;

    /** 状态（0正常 1停用） */
    @Pattern(regexp = "^[01]$", message = "状态取值必须是 0（正常）或 1（停用）")
    @TableField("status")
    private String status;

    /** 删除标志（0存在 2删除）。**基础数据表**；`READ_ONLY` 防止客户端注入，见 `Promotion#delFlag` */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
