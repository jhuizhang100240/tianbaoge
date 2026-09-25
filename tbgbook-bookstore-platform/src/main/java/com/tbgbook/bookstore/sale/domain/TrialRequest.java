package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

/**
 * 优惠试算请求（`POST /sales/cashier/trial`）。
 *
 * <p>⚠️ 契约把「积分抵扣超过 `biz.points.deduct.ratio`」「券不满足门槛或已过期」归到 **422 业务规则**，
 * 故 {@code usePoints}/{@code couponId} 不标范围注解，由 service 判。
 */
@Data
public class TrialRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID，0=散客 */
    private Long memberId;

    /** 使用的券ID（**单笔订单最多 1 张**，§3.2 价格规则 3） */
    private Long couponId;

    /** 使用积分数（100 积分 = 1 元） */
    private Integer usePoints;

    /** 商品明细（正价书给 bookId，二手书给 usedBookId） */
    @NotEmpty(message = "商品明细不能为空")
    private List<Line> items;

    /** 一行商品 */
    @Data
    public static class Line implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 图书ID（正价书） */
        private Long bookId;

        /** 二手书副本ID（二手书） */
        private Long usedBookId;

        /** 数量（二手书恒为 1） */
        private Integer qty;
    }
}
