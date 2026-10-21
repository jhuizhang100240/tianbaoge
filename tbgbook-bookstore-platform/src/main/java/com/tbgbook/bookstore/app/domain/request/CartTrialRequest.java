package com.tbgbook.bookstore.app.domain.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 购物车试算请求。
 */
@Data
public class CartTrialRequest
{
    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<CartItemRequest> items;

    private Long couponId;

    private Integer usePoints;
}
