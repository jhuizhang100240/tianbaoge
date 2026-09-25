package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Data;

/**
 * 执行优惠券发放批次的请求体（`POST /sales/coupon-batch/{batchId}/execute`）。
 *
 * <p>只有一个选填的 {@code grantType}（发放方式）—— 不传就沿用批次上已有的值。
 * 整个请求体也可省略。
 *
 * @author tbgbook
 */
@Data
public class CouponBatchExecuteRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 发放方式（1定向发放 2领券中心 3活动赠送），不传则沿用批次上的值 */
    @Min(value = 1, message = "发放方式取值 1~3")
    @Max(value = 3, message = "发放方式取值 1~3")
    private Integer grantType;
}
