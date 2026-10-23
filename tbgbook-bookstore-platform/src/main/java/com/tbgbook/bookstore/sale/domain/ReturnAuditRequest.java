package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 退货审批请求（`approve` 与 `reject` 共用）。
 *
 * <p>⚠️ {@code auditRemark} 在两个端点上必填性不同（与报损审批同一处置）：
 * approve 选填、**reject 必填**。一个 DTO 服务两个端点时注解分不清是哪个端点，
 * 故不加 {@code @NotBlank}，由 service 在 reject 里判。
 */
@Data
public class ReturnAuditRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 审批意见（approve 选填；reject 必填，由 service 判定） */
    @Size(max = 500, message = "审批意见不能超过 500 个字符")
    private String auditRemark;
}
