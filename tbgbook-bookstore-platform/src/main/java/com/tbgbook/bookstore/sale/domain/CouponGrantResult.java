package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 人工补发优惠券的结果（`POST /sales/coupon/grant` 的 {@code data}）。
 *
 * <p>契约要求 {@code granted 成功数} / {@code failed 失败数} /
 * {@code failDetails 失败明细（含原因，如已达每人限领）}。
 *
 * <p>⚠️ <b>「部分失败不整体回滚」</b>（契约错误码原文）—— 给 10 个人补发、
 * 其中 2 人已达限领，应当是「成功 8、失败 2 并列出原因」，而不是整批 422 失败。
 * 故本接口在**同一事务**内逐人尝试，把失败的人记进 {@code failDetails} 后照常提交。
 * 这条与「单据动作要么全成要么全败」相反，是因为补发是**幂等的逐人操作**，
 * 让一个人失败连带 8 个人拿不到券，对运营是不可接受的。
 *
 * @author tbgbook
 */
@Data
public class CouponGrantResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 成功发放数量 */
    private Integer granted;

    /** 失败数量 */
    private Integer failed;

    /** 失败明细（为空数组而不是 null，前端不必判空） */
    private List<FailDetail> failDetails;

    /** 单个会员的失败原因 */
    @Data
    public static class FailDetail implements Serializable
    {
        private static final long serialVersionUID = 1L;

        /** 会员ID */
        private Long memberId;

        /** 失败原因（如「已达每人限领 1 张」） */
        private String reason;
    }
}
