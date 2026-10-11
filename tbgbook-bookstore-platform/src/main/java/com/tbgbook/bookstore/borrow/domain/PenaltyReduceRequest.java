package com.tbgbook.bookstore.borrow.domain;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 罚金减免请求（阶段五步骤④第二批，需店长审批 —— 权限 {@code borrow:penalty:reduce}）。
 *
 * @author tbgbook
 */
@Data
public class PenaltyReduceRequest
{
    /** 减免金额 */
    @NotNull(message = "减免金额不能为空")
    @DecimalMin(value = "0.01", message = "减免金额必须大于 0")
    private BigDecimal reduceAmount;

    /** 减免原因 */
    @NotBlank(message = "减免原因不能为空")
    private String reason;
}
