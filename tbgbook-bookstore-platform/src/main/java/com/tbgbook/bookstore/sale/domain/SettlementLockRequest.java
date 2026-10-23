package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 锁定日结请求（`POST /sales/settlement/{settleId}/lock`）。
 *
 * <p>⚠️ {@code diffReason} 在 {@code diffAmount != 0} 时**必填**
 * （契约错误码：422 存在对账差异但未填差异说明）—— 由 service 判，注解不知道差额。
 */
@Data
public class SettlementLockRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    @Size(max = 500, message = "差异说明不能超过 500 个字符")
    private String diffReason;
}
