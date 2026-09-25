package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 会员简要信息（收银台识别用；**不落库**）。
 *
 * <p>⚠️ 跨域读会员域的表（`biz_member` / `biz_member_level` / `biz_member_balance`，属阶段五）——
 * 只读。按已确认口径直接读表（与库存预警扫描读 `biz_stock_flow` 同一处置）。
 */
@Data
public class MemberBrief implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long memberId;
    private String memberNo;
    private String phone;
    private String nickname;
    private Long levelId;
    private String levelName;

    /** 等级折扣率（1.00 = 无折扣） */
    private BigDecimal discountRate;

    /** 可用积分 */
    private Integer points;

    /** 储值余额合计（本金 + 赠送金） */
    private BigDecimal balanceTotal;

    /** 会员状态（1正常 2冻结 3黑名单 4已注销） */
    private Integer status;
}
