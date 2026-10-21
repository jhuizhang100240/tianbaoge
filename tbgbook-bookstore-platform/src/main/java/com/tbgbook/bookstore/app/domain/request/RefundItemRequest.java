package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 退货明细行。
 */
@Data
public class RefundItemRequest
{
    @NotNull(message = "原订单明细ID不能为空")
    private Long orderItemId;

    @NotNull(message = "退货数量不能为空")
    @Min(value = 1, message = "退货数量至少为 1")
    private Integer returnQty;

    private Integer bookCondition;
}
