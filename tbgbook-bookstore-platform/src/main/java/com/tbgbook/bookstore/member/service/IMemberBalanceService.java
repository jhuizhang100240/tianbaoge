package com.tbgbook.bookstore.member.service;

import java.math.BigDecimal;
import java.util.List;

import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.bookstore.member.domain.RechargeRequest;
import com.tbgbook.bookstore.member.domain.RechargeResult;
import com.tbgbook.bookstore.member.domain.RefundRequest;
import com.tbgbook.bookstore.member.domain.RefundResult;
import com.tbgbook.bookstore.member.domain.StoredActivity;
import com.tbgbook.bookstore.member.domain.StoredValueLog;

/**
 * 会员储值 Service 接口（阶段五步骤②b，页面 A-48）。
 *
 * <p>覆盖三块：储值账户（balance）、储值流水（stored-log）、充值活动（stored-activity），
 * 以及充值动作。消费扣减 / 消费退款返还通过 {@link #deductBalance} / {@link #refundBalance}
 * 供销售链路调用（阶段四遗留的 `MemberWriteMapper` 在本步骤收编后删除）。
 *
 * <p>`/member/balance/refund` 按 **C20 裁决（2026-10-11）** 定义为**后台出账退款**：
 * 从储值账户核销（先核销赠送金、再退本金；`refundPrincipal=false` 时仅退赠送金），
 * 消费退货的「返还至原储值账户」仍走内部 {@link #refundBalance}（按原比例，不受影响）。
 *
 * @author tbgbook
 */
public interface IMemberBalanceService
{
    /**
     * 查询储值账户列表。
     *
     * @param balance 查询条件
     * @return 账户集合
     */
    List<MemberBalance> selectMemberBalanceList(MemberBalance balance);

    /**
     * 查询储值账户详情。
     *
     * @param balanceId 账户ID
     * @return 账户；不存在时抛 404
     */
    MemberBalance selectMemberBalanceById(Long balanceId);

    /**
     * 补建储值账户（正常建档已自动初始化；本接口用于修复缺账户的存量会员）。
     *
     * @param balance 账户（仅 memberId 有效）
     * @return 影响行数
     */
    int addMemberBalance(MemberBalance balance);

    /**
     * 修改储值账户 —— **恒 422**：余额由充值 / 消费 / 退款接口维护，不接受直接修改。
     *
     * @param balance 账户
     * @return 不会返回（抛 422）
     */
    int updateMemberBalance(MemberBalance balance);

    /**
     * 删除储值账户（仅**零余额**账户可删；有余额一律 422）。
     *
     * @param balanceIds 账户主键数组
     * @return 影响行数
     */
    int removeMemberBalances(Long[] balanceIds);

    /**
     * 查询储值流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<StoredValueLog> selectStoredValueLogList(StoredValueLog log);

    /**
     * 查询储值流水详情。
     *
     * @param logId 流水ID
     * @return 流水；不存在时抛 404
     */
    StoredValueLog selectStoredValueLogById(Long logId);

    /**
     * 查询充值活动列表。
     *
     * @param activity 查询条件
     * @return 活动集合
     */
    List<StoredActivity> selectStoredActivityList(StoredActivity activity);

    /**
     * 查询充值活动详情。
     *
     * @param activityId 活动ID
     * @return 活动；不存在时抛 404
     */
    StoredActivity selectStoredActivityById(Long activityId);

    /**
     * 新增充值活动。
     *
     * @param activity 活动
     * @return 影响行数
     */
    int addStoredActivity(StoredActivity activity);

    /**
     * 修改充值活动。
     *
     * @param activity 活动
     * @return 影响行数
     */
    int updateStoredActivity(StoredActivity activity);

    /**
     * 删除充值活动（物理删除；流水不挂活动 ID，不构成引用）。
     *
     * @param activityIds 活动主键数组
     * @return 影响行数
     */
    int removeStoredActivities(Long[] activityIds);

    /**
     * 储值充值：本金入账；命中活动时按档位赠送（赠送金独立记账 + 有效期）。
     *
     * @param request 充值请求
     * @return 充值结果
     */
    RechargeResult recharge(RechargeRequest request);

    /**
     * 储值退款（后台出账核销）：先核销赠送金、再退本金；`refundPrincipal=false` 时只退赠送金。
     *
     * <p>校验：金额 > 0、原因必填、金额 ≤ 可用余额；出账写 5/6 两条流水（**负数**，
     * 与消费退货回存的**正数**按符号区分方向）。实际出款（现金/原路）线下完成。
     *
     * @param request 退款请求
     * @return 退款结果
     */
    RefundResult refund(RefundRequest request);

    /**
     * [销售消费] 储值扣减（条件更新，余额不足抛 422；写 3/4 两条流水并同步会员冗余汇总）。
     *
     * @param principalPart   扣本金部分
     * @param giftPart        扣赠送金部分
     * @param amount          扣减合计
     * @param refNo           关联单号
     * @param principalRemark 本金流水备注
     * @param giftRemark      赠送金流水备注
     */
    void deductBalance(Long memberId, BigDecimal principalPart, BigDecimal giftPart, BigDecimal amount,
            String refNo, String principalRemark, String giftRemark);

    /**
     * [销售退款] 储值回退（本金 / 赠送金按调用方给定份额分别回退，§3.2 价格规则 6 按原比例）。
     *
     * @param refNo           关联退单号
     * @param principalRemark 本金流水备注
     * @param giftRemark      赠送金流水备注
     */
    void refundBalance(Long memberId, BigDecimal principalPart, BigDecimal giftPart,
            String refNo, String principalRemark, String giftRemark);
}
