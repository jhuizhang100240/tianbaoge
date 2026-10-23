package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 校验券可用性的结果（`GET /sales/coupon/verify` 的 {@code data}）。
 *
 * <p>契约要求 {@code usable 是否可用} / {@code reason 不可用原因}。
 *
 * <p><b>本接口能判的与判不了的分开写清</b>：它只拿到 {@code couponId}/{@code memberId}/{@code amount}
 * 三个入参，故只能判「是否本人持有、是否未使用、是否过期、门槛够不够」。
 * 契约 {@code reason} 里还列了「范围不符」「与特价商品冲突」两类 —— 那两类**需要购物车明细**
 * （要知道买了哪些书、含不含特价），本接口拿不到，故在**试算**（{@code POST /sales/cashier/trial}）
 * 里判。本接口对这两类一律返回可用，并在文档里注明边界，避免前端以为它做了全量校验。
 *
 * @author tbgbook
 */
@Data
public class CouponVerifyResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 是否可用 */
    private Boolean usable;

    /** 不可用原因（可用时为 null） */
    private String reason;
}
