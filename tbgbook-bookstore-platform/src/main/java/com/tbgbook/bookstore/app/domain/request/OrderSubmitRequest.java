package com.tbgbook.bookstore.app.domain.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 会员端提交订单请求。
 */
@Data
public class OrderSubmitRequest
{
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    @NotEmpty(message = "商品明细不能为空")
    @Valid
    private List<CartItemRequest> items;

    private Long couponId;

    private Integer usePoints;

    private String remark;

    private String contactName;

    private String contactPhone;
}
