package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 促销活动启停的请求体（`POST /sales/promotion/{promotionId}/status`）。
 *
 * <p>⚠️ <b>不加范围校验注解</b>：契约把「状态流转非法」归到 **422 业务规则**
 * （如「从已结束直接到进行中」）而不是 400 参数格式，且要给出带语义的文案；
 * 由 {@code PromotionServiceImpl#changeStatus} 判定。必填（{@code @NotNull}）保留
 * —— 它缺失时确实是 400 参数缺失。
 *
 * @author tbgbook
 */
@Data
public class PromotionStatusRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 目标状态（1草稿 2进行中 3已结束 4已停用）—— 流转合法性由 service 判定并返回 422 */
    @NotNull(message = "目标状态不能为空")
    private Integer status;
}
