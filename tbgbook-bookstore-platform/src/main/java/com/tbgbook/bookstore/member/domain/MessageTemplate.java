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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板对象 biz_message_template（页面 A-51）。
 *
 * <p>{@code wxTemplateId} 需与微信公众平台申请到的模板一致；
 * {@code contentTpl} 含占位符（如 `{昵称}`），发送时按业务数据填充 ——
 * 支持的占位符白名单见 {@code MemberMessageServiceImpl}（自定口径 A40），
 * **保存与发送时都会校验**（不支持 → 422）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_message_template")
public class MessageTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 模板编码（唯一） */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码不能超过 50 个字符")
    @TableField("template_code")
    private String templateCode;

    /** 模板名称 */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称不能超过 100 个字符")
    @TableField("template_name")
    private String templateName;

    /** 消息类型（1到货通知 2借阅到期 3催还 4积分到期 5活动通知 6系统消息 7人工回复） */
    @NotNull(message = "消息类型不能为空")
    @Min(value = 1, message = "消息类型取值必须是 1~7")
    @Max(value = 7, message = "消息类型取值必须是 1~7")
    @TableField("msg_type")
    private Integer msgType;

    /** 微信订阅消息模板ID */
    @Size(max = 100, message = "微信模板ID不能超过 100 个字符")
    @TableField("wx_template_id")
    private String wxTemplateId;

    /** 内容模板（含占位符） */
    @Size(max = 1000, message = "内容模板不能超过 1000 个字符")
    @TableField("content_tpl")
    private String contentTpl;

    /** 状态（0正常 1停用） */
    @Pattern(regexp = "^[01]$", message = "状态取值必须是 0（正常）或 1（停用）")
    @TableField("status")
    private String status;

    /** 删除标志（0存在 2删除）。**基础数据表**；`READ_ONLY` 防止客户端注入，见 `Promotion#delFlag` */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
