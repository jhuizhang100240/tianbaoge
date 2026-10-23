package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 收银台会员识别的结果（`POST /sales/cashier/member-lookup` 的 {@code data}）。
 *
 * <p>契约要求 {@code memberId}/{@code memberNo}/{@code nickname}/{@code phone（脱敏）}/
 * {@code levelId}/{@code levelName}/{@code discountRate}/{@code points}/{@code balanceTotal}/
 * {@code coupons 可用券数组}/{@code borrowStatus 借阅状态}。
 *
 * <p><b>「同时返回借阅资格状态」是收银场景的实际需要</b>（契约原文）：会员有未还或逾期的书，
 * 收银时要一并提示，免得办完卡再被追着问。故 {@code borrowStatus} 里给在借册数与是否有逾期。
 *
 * <p><b>手机号必须脱敏</b>（NFR-S8）：返回 {@code 138****1234} 形态。
 * 收银员只需要核对尾号，不需要完整号码 —— 而完整号码落进前端日志就是泄露。
 *
 * <p><b>可用券只给「此刻真的能用」的</b>：状态未使用、在有效期内。
 * 门槛是否够由试算判（那时才知道商品金额），此处不过滤。
 *
 * @author tbgbook
 */
@Data
public class MemberLookupResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 会员卡号 */
    private String memberNo;

    /** 昵称 */
    private String nickname;

    /** 手机号（**脱敏**，如 138****1234） */
    private String phone;

    /** 等级ID */
    private Long levelId;

    /** 等级名称 */
    private String levelName;

    /** 等级折扣率（1.00 = 无折扣，0.95 = 9.5 折） */
    private BigDecimal discountRate;

    /** 可用积分（100 积分 = 1 元） */
    private Integer points;

    /** 储值余额合计（本金 + 赠送金） */
    private BigDecimal balanceTotal;

    /** 可用券数组（仅未使用且在有效期内的） */
    private List<Coupon> coupons;

    /** 借阅状态（供收银时一并提示未还/逾期） */
    private BorrowStatus borrowStatus;

    /** 可用券（只列收银要用到的几个字段） */
    @Data
    public static class Coupon implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 券ID */
        private Long couponId;

        /** 券码 */
        private String couponCode;

        /** 券名称 */
        private String couponName;

        /** 面额 */
        private BigDecimal faceValue;

        /** 使用门槛 */
        private BigDecimal thresholdAmount;

        /** 失效时间 */
        private String validEnd;
    }

    /** 借阅状态 */
    @Data
    public static class BorrowStatus implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 在借册数 */
        private Integer borrowingCount;

        /** 是否有逾期未还 */
        private Boolean hasOverdue;
    }
}
