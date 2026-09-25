package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 教材取书核销请求（`POST /sales/reserve/{reserveId}/pickup`）。
 *
 * <p>支持**分批取书**：每次只释放本次数量对应的锁定库存，{@code lockedQty} 累加，
 * 累计达到 {@code qty} 时置 {@code status=4 已取完}。
 */
@Data
public class PickupRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 本次取书数量（≤ 剩余数量） */
    @NotNull(message = "本次取书数量不能为空")
    @Min(value = 1, message = "本次取书数量必须大于 0")
    private Integer qty;

    /** 取货码（不传则不校验；传了就必须一致） */
    private String pickupCode;
}
