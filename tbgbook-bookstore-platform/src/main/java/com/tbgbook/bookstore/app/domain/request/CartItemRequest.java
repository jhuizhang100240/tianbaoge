package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员端购物车明细行。
 */
@Data
public class CartItemRequest
{
    private Long bookId;

    private Long usedBookId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    private Integer qty;
}
