package com.tbgbook.bookstore.app.domain.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会员端领券请求。
 */
@Data
public class AppCouponClaimRequest
{
    @NotNull(message = "优惠券模板ID不能为空")
    private Long templateId;
}
