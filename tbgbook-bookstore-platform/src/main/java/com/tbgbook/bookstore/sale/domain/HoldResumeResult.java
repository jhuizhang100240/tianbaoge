package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 取单结果（`POST /sales/hold/{holdId}/resume` 的 {@code data}）。
 *
 * <p>契约要求 {@code cartData} / {@code memberId} / {@code changes 变动提示数组}。
 *
 * <p><b>{@code changes} 是取单这一步的核心价值</b>：挂单**不锁库存**，
 * 挂单期间商品可能被买走、活动可能结束 —— 取单时必须重新校验并**把变动列出来**，
 * 由收银员当面跟顾客确认。变动的行照样返回，不静默修正。
 */
@Data
public class HoldResumeResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 挂单ID */
    private Long holdId;

    /** 挂单号 */
    private String holdNo;

    /** 购物车快照（原样返回供前端还原） */
    private Map<String, Object> cartData;

    /** 会员ID（0=散客） */
    private Long memberId;

    /** 变动提示（空数组而非 null）。如「《xx》库存不足」「《yy》价格已调整」 */
    private List<String> changes;
}
