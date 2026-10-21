package com.tbgbook.bookstore.app.domain.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 会员端退货申请。
 */
@Data
public class RefundApplyRequest
{
    @NotBlank(message = "退货原因不能为空")
    private String reason;

    @NotEmpty(message = "退货明细不能为空")
    @Valid
    private List<RefundItemRequest> items;

    private List<String> proofImages;
}
