package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 报损审批的请求体（`POST /inventory/loss/{applyId}/approve` 与 `.../reject` 共用）。
 *
 * <p>⚠️ <b>{@code auditRemark} 在两个端点上必填性不同</b>，契约如此：
 * <ul>
 *   <li>{@code /approve} —— 选填（同意意见可以不写）</li>
 *   <li>{@code /reject} —— <b>必填</b>（驳回必须给理由）</li>
 * </ul>
 * 一个 DTO 服务两个端点时，注解分不清「这次是哪个端点」——
 * 在字段上标 {@code @NotBlank} 会让 approve 也被逼着填意见。故此处**不加 {@code @NotBlank}**，
 * 由 {@code LossApplyServiceImpl#reject} 显式判定（与 {@code StockInItem} 上
 * 「必填性按操作判定」是同一条经验）。
 *
 * @author tbgbook
 */
@Data
public class LossAuditRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 审批意见（approve 选填；reject 必填，由 service 判定） */
    @Size(max = 500, message = "审批意见不能超过 500 个字符")
    private String auditRemark;
}
