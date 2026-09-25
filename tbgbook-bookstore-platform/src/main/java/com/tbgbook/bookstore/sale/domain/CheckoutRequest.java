package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

/**
 * 收银结算请求（`POST /sales/cashier/checkout`）。
 *
 * <p>⚠️ {@code requestId} 是**幂等键**（NFR-P2 约束 4）：同 requestId 重复提交返回首次结果。
 * 它**不是** `@RepeatSubmit` 那种短时间窗防重复点击 —— 后者不落库、不返回首次结果，不能替代。
 */
@Data
public class CheckoutRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 幂等键（客户端生成的 UUID） */
    @NotBlank(message = "requestId 不能为空")
    private String requestId;

    /** 会员ID，0=散客 */
    private Long memberId;

    /** 使用的券ID（单笔最多 1 张） */
    private Long couponId;

    /** 使用积分数（100 积分 = 1 元） */
    private Integer usePoints;

    /** 商品明细 */
    @NotEmpty(message = "商品明细不能为空")
    private List<Item> items;

    /** 组合支付数组（FR-4.3.4）；金额之和必须等于应付金额 */
    @NotEmpty(message = "支付方式不能为空")
    private List<Pay> payments;

    /** 来源挂单ID（挂单转入时传） */
    private Long holdId;

    /** 折扣审批人（触发折扣审批时必填） */
    private String approvalBy;

    /** 一行商品 */
    @Data
    public static class Item implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private Long bookId;
        private Long usedBookId;
        private Integer qty;
    }

    /** 一笔支付 */
    @Data
    public static class Pay implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private Integer payMethod;
        private BigDecimal payAmount;
        private String tradeNo;
    }
}
