package com.tbgbook.bookstore.sale.support;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.domain.Promotion;
import com.tbgbook.bookstore.sale.mapper.PromotionMapper;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * 按行定价：解析「活动价 > 会员价 > 定价」（§3.2 价格规则 1）。
 *
 * <p>被 {@code scan}（扫码加商品）、{@code trial}（优惠试算）、{@code resume}（取单重校验）
 * 三处共用 —— 定价口径一旦分叉，同一本书在扫码时和结算时会是两个价。
 *
 * <p><b>只处理能落到「单价」上的活动类型</b>：{@code 1折扣}（按 `discountRate` 打折）、
 * {@code 4会员专享价} / {@code 5限时特价}（用明细的 `activityPrice`）。
 * {@code 2满减} / {@code 3满赠} **需要订单级门槛**（满多少才减），不是单价概念，本期**不参与按行定价**
 * —— 已登记 `plan/待确认项.md`（满减/满赠的订单级实现留待后续）。
 *
 * <p><b>命中多个活动时按优先级取一、不相加</b>（契约业务约束）：Mapper 已按 `priority` 倒序，
 * 这里只取第一条能算出价的活动。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class PricingSupport
{
    private final PromotionMapper promotionMapper;

    /**
     * 解析某本书对某会员等级的成交价。
     *
     * @param book          图书（需带 price / memberPrice / categoryId）
     * @param memberLevelId 会员等级ID；**null 表示散客**（不享会员价，等级限定活动也不适用）
     * @return 定价明细（原价、成交单价、两类折扣的单位金额、价格来源）
     */
    public PriceInfo resolve(Book book, Long memberLevelId)
    {
        PriceInfo info = new PriceInfo();
        BigDecimal origin = nz(book.getPrice());
        info.setOriginPrice(origin);

        // ① 单品折扣：找此刻生效、覆盖本书、优先级最高的活动
        BigDecimal afterActivity = origin;
        for (Promotion p : promotionMapper.selectActiveForBook(
                book.getBookId(), book.getCategoryId(), LocalDateTime.now()))
        {
            if (!levelMatches(p.getMemberLevelIds(), memberLevelId))
            {
                continue;
            }
            BigDecimal act = activityUnitPrice(p, origin);
            if (act != null && act.compareTo(afterActivity) < 0)
            {
                afterActivity = act;
                // 特价商品**不参与优惠券叠加**（§3.2 价格规则 3），试算据此拒绝用券
                info.setSpecialPrice(Objects.equals(p.getIsSpecialPrice(), 1));
                break; // 按优先级取一，不相加
            }
        }
        info.setSingleDiscount(origin.subtract(afterActivity));

        // ② 会员等级折扣：会员价（散客不适用）
        BigDecimal afterMember = afterActivity;
        if (memberLevelId != null && book.getMemberPrice() != null
                && book.getMemberPrice().signum() > 0
                && book.getMemberPrice().compareTo(afterMember) < 0)
        {
            afterMember = book.getMemberPrice();
        }
        info.setMemberDiscount(afterActivity.subtract(afterMember));

        info.setUnitPrice(afterMember);
        // 价格来源取「实际起作用的那一级」
        info.setPriceType(info.getSingleDiscount().signum() > 0 ? OrderItem.PRICE_TYPE_ACTIVITY
                : (info.getMemberDiscount().signum() > 0 ? OrderItem.PRICE_TYPE_MEMBER
                        : OrderItem.PRICE_TYPE_LIST));
        return info;
    }

    /**
     * 该活动对本书的单价；算不出单价时返回 null。
     *
     * <p>折扣型 = 原价 × `discountRate`；专享价/限时特价 = 明细里的 `activityPrice`。
     */
    private BigDecimal activityUnitPrice(Promotion p, BigDecimal origin)
    {
        if (Objects.equals(p.getPromotionType(), Promotion.TYPE_DISCOUNT)
                && p.getDiscountRate() != null && p.getDiscountRate().signum() > 0
                && p.getDiscountRate().compareTo(BigDecimal.ONE) < 0)
        {
            return origin.multiply(p.getDiscountRate()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if ((Objects.equals(p.getPromotionType(), Promotion.TYPE_MEMBER_PRICE)
                || Objects.equals(p.getPromotionType(), Promotion.TYPE_FLASH_PRICE))
                && p.getActivityPrice() != null && p.getActivityPrice().signum() > 0)
        {
            return p.getActivityPrice();
        }
        return null;
    }

    /** 活动的适用等级为空 = 全等级；否则要求会员等级在列表里（散客不满足） */
    private boolean levelMatches(String memberLevelIds, Long memberLevelId)
    {
        if (memberLevelIds == null || memberLevelIds.isBlank())
        {
            return true;
        }
        if (memberLevelId == null)
        {
            return false;
        }
        for (String part : memberLevelIds.split(","))
        {
            if (part.trim().equals(String.valueOf(memberLevelId)))
            {
                return true;
            }
        }
        return false;
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    /** 单行的定价结果 */
    @Data
    public static class PriceInfo
    {
        /** 原价（定价） */
        private BigDecimal originPrice;

        /** 成交单价 */
        private BigDecimal unitPrice;

        /** 单位单品折扣（定价 − 活动价） */
        private BigDecimal singleDiscount;

        /** 单位会员折扣（活动价 − 会员价） */
        private BigDecimal memberDiscount;

        /** 价格来源（1定价 2会员价 3活动价） */
        private Integer priceType;

        /** 命中的活动是否为**特价**活动（特价商品不参与优惠券叠加） */
        private boolean specialPrice;
    }
}
