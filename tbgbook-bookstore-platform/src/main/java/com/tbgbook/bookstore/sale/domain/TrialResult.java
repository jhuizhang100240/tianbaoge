package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 优惠试算结果（`POST /sales/cashier/trial` 的 {@code data}）。
 *
 * <p><b>四类优惠分项返回**而不是只给一个合计**</b>（契约要求「前端逐项展示便于核对」）——
 * 收银现场顾客会问「为什么便宜了 12 块」，分项才答得上来。
 * 计算顺序固定：**单品折扣 → 会员等级折扣 → 优惠券 → 积分抵扣**（§4.3 规则 2）。
 */
@Data
public class TrialResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 商品合计（原价小计） */
    private BigDecimal totalAmount;

    /** ① 单品折扣（活动价带来的差价） */
    private BigDecimal singleDiscount;

    /** ② 会员等级折扣 */
    private BigDecimal memberDiscount;

    /** ③ 优惠券抵扣 */
    private BigDecimal couponAmount;

    /** ④ 积分抵扣 */
    private BigDecimal pointAmount;

    /** 优惠合计 */
    private BigDecimal discountTotal;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 使用积分数 */
    private Integer pointUsed;

    /** 本单获得积分（消费 1 元 = 1 积分） */
    private Integer pointGain;

    /** 是否触发折扣审批（折扣低于 0.7 或折扣额超 30%） */
    private Integer needApproval;

    /** 触发原因（空数组而非 null） */
    private List<String> approvalReasons;

    /** 是否含特价商品（**特价不参与优惠券叠加**，§3.2 价格规则 3） */
    private Integer hasSpecialPrice;

    /** 逐行分摊明细（§4.3 规则 5：优惠按金额比例分摊到行，毛利核算口径） */
    private List<Line> items;

    /** 一行商品的分摊结果 */
    @Data
    public static class Line implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private Long bookId;
        private Long usedBookId;
        private String isbn;
        private String title;
        private Integer bookType;
        private Integer priceType;
        private BigDecimal originPrice;
        private BigDecimal unitPrice;
        private Integer qty;
        private BigDecimal amount;
        private BigDecimal singleDiscount;
        private BigDecimal memberDiscount;
        private BigDecimal couponShare;
        private BigDecimal pointShare;
        private BigDecimal shareAmount;
    }
}
