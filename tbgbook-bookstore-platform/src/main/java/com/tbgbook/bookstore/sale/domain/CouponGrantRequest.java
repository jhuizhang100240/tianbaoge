package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 人工补发优惠券的请求体（`POST /sales/coupon/grant`）。
 *
 * <p><b>敏感操作</b>（契约原文）—— 人工给指定会员补发券，强制记录操作日志
 * （角色权限要点 4）。故 {@code reason} 是**必填**：补发必须说清为什么
 * （补偿客诉 / 活动漏发 / 系统故障），否则事后无从审计。
 *
 * <p>这里三个字段都用了必填注解（{@code @NotNull} / {@code @NotEmpty} / {@code @NotBlank}）——
 * 与「共用实体不加注解」的处置不同：本 DTO **只服务这一个端点**，
 * 注解不会误伤别的操作，故能用注解就不必在 service 里手写判空。
 *
 * @author tbgbook
 */
@Data
public class CouponGrantRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 券模板ID（必填） */
    @NotNull(message = "券模板ID不能为空")
    private Long templateId;

    /** 会员ID数组（必填，至少一个） */
    @NotEmpty(message = "补发会员不能为空")
    private List<Long> memberIds;

    /** 补发原因（**必填**，写入操作日志） */
    @NotBlank(message = "补发原因不能为空")
    @Size(max = 200, message = "补发原因不能超过 200 个字符")
    private String reason;
}
