package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 收银台挂单请求（`POST /sales/cashier/hold`）。
 *
 * <p>与 `/sales/hold` 的 CRUD 分开：这条是**收银台现场动作**（权限 `sales:hold:list`），
 * 只收购物车快照；挂单号/挂单人/时间由服务端写。
 */
@Data
public class CashierHoldRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID，0=散客 */
    private Long memberId;

    /** 购物车快照（原样保存，供取单还原） */
    @NotNull(message = "购物车不能为空")
    private Map<String, Object> cartData;

    /** 商品件数（不传由服务端按 cartData 汇总） */
    private Integer totalQty;

    /** 挂单金额（不传由服务端按 cartData 汇总） */
    private BigDecimal totalAmount;
}
