package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 日结聚合结果（一次 SQL 出全部汇总列）。
 *
 * <p>不落库，只作 {@code biz_daily_settlement} 各金额列的来源。字段名与
 * {@link DailySettlement} 的同名列一致，MyBatis 按列标签直接映射，省一层转换。
 *
 * @author tbgbook
 */
@Data
public class SettlementAggregate implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 订单笔数（同窗口内曾收过款的订单） */
    private Integer orderCount;

    /** 收款总额（订单实付合计） */
    private BigDecimal totalAmount;

    private BigDecimal cashAmount;

    private BigDecimal wechatAmount;

    private BigDecimal alipayAmount;

    private BigDecimal storedAmount;

    /** 校园卡收款（`biz_daily_settlement` 没有这一列，仅供差异说明用） */
    private BigDecimal campusAmount;

    /** 退款总额（支付流水上的退款额合计，含微信原路退回与储值回退） */
    private BigDecimal refundAmount;

    /** 积分抵扣总额 */
    private BigDecimal pointAmount;
}
