package com.tbgbook.bookstore.member.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.bookstore.member.domain.RechargeRequest;
import com.tbgbook.bookstore.member.domain.RechargeResult;
import com.tbgbook.bookstore.member.domain.RefundRequest;
import com.tbgbook.bookstore.member.domain.RefundResult;
import com.tbgbook.bookstore.member.domain.StoredActivity;
import com.tbgbook.bookstore.member.domain.StoredValueLog;
import com.tbgbook.bookstore.member.mapper.MemberBalanceMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.StoredActivityMapper;
import com.tbgbook.bookstore.member.mapper.StoredValueLogMapper;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 会员储值 Service 实现（阶段五步骤②b，页面 A-48）。
 *
 * <p><b>账户 CRUD 口径（自定，登记 A36）</b>：正常建档已自动初始化账户；
 * `POST /member/balance` 只作**补建**（一会员一账户，重复 409）；
 * `PUT` **恒 422**（余额由充值 / 消费 / 退款接口维护，不接受直接修改）；
 * `DELETE` 仅**零余额**账户可删（有余额 422），删除为物理删除（本表无 `del_flag`）。
 *
 * <p><b>充值</b>：本金按金额入账；命中活动时校验档位与有效期，赠送金独立记账
 * （`amount_type=2`，有效期由活动 `giftExpireDays` 决定，0=长期）。
 * <b>消费扣减 / 退款回退</b>：条件更新 + 冗余汇总同步 + 分桶写流水，
 * 供收银 / 退货链路调用（本步骤完成收编，`MemberWriteMapper` 随之删除）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberBalanceServiceImpl extends ServiceImpl<MemberBalanceMapper, MemberBalance>
        implements IMemberBalanceService
{
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final StoredValueLogMapper storedValueLogMapper;

    private final StoredActivityMapper storedActivityMapper;

    // ==================================================================
    // 账户
    // ==================================================================

    @Override
    public List<MemberBalance> selectMemberBalanceList(MemberBalance balance)
    {
        return baseMapper.selectMemberBalanceList(balance);
    }

    @Override
    public MemberBalance selectMemberBalanceById(Long balanceId)
    {
        return referenceGuard.requireExists(baseMapper, balanceId, "会员储值账户");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMemberBalance(MemberBalance balance)
    {
        if (balance.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        referenceGuard.requireExists(memberMapper, balance.getMemberId(), "会员");
        if (count(new LambdaQueryWrapper<MemberBalance>()
                .eq(MemberBalance::getMemberId, balance.getMemberId())) > 0)
        {
            throw BizErrors.duplicate("会员储值账户", "会员ID", balance.getMemberId());
        }
        LocalDateTime now = LocalDateTime.now();
        balance.setBalanceId(null);
        balance.setPrincipalBalance(BigDecimal.ZERO);
        balance.setGiftBalance(BigDecimal.ZERO);
        balance.setTotalBalance(BigDecimal.ZERO);
        balance.setVersion(0);
        balance.setCreateTime(now);
        balance.setUpdateTime(now);
        return baseMapper.insert(balance);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int updateMemberBalance(MemberBalance balance)
    {
        if (balance.getBalanceId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的储值账户");
        }
        referenceGuard.requireExists(baseMapper, balance.getBalanceId(), "会员储值账户");
        throw BizErrors.ruleViolated("储值账户余额由充值 / 消费 / 退款接口维护，不支持直接修改");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMemberBalances(Long[] balanceIds)
    {
        List<Long> idList = (balanceIds == null) ? Collections.emptyList() : Arrays.asList(balanceIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "会员储值账户");
        for (Serializable id : ids)
        {
            MemberBalance row = referenceGuard.requireExists(baseMapper, id, "会员储值账户");
            if (nz(row.getPrincipalBalance()).signum() > 0 || nz(row.getGiftBalance()).signum() > 0)
            {
                throw BizErrors.ruleViolated("账户仍有余额（本金 " + row.getPrincipalBalance()
                        + " + 赠送金 " + row.getGiftBalance() + "），不能删除");
            }
        }
        // 本表无 @TableLogic —— 物理删除；仅零余额账户会走到这里
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 流水
    // ==================================================================

    @Override
    public List<StoredValueLog> selectStoredValueLogList(StoredValueLog log)
    {
        return storedValueLogMapper.selectStoredValueLogList(log);
    }

    @Override
    public StoredValueLog selectStoredValueLogById(Long logId)
    {
        return referenceGuard.requireExists(storedValueLogMapper, logId, "储值流水");
    }

    // ==================================================================
    // 充值活动
    // ==================================================================

    @Override
    public List<StoredActivity> selectStoredActivityList(StoredActivity activity)
    {
        return storedActivityMapper.selectStoredActivityList(activity);
    }

    @Override
    public StoredActivity selectStoredActivityById(Long activityId)
    {
        return referenceGuard.requireExists(storedActivityMapper, activityId, "储值充值活动");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStoredActivity(StoredActivity activity)
    {
        activity.setActivityId(null);
        if (activity.getStatus() == null)
        {
            activity.setStatus(1);
        }
        if (activity.getIsRecommend() == null)
        {
            activity.setIsRecommend(0);
        }
        if (activity.getOrderNum() == null)
        {
            activity.setOrderNum(0);
        }
        return storedActivityMapper.insert(activity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStoredActivity(StoredActivity activity)
    {
        if (activity.getActivityId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的充值活动");
        }
        referenceGuard.requireExists(storedActivityMapper, activity.getActivityId(), "储值充值活动");
        return storedActivityMapper.updateById(activity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStoredActivities(Long[] activityIds)
    {
        List<Long> idList = (activityIds == null) ? Collections.emptyList() : Arrays.asList(activityIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "储值充值活动");
        for (Serializable id : ids)
        {
            referenceGuard.requireExists(storedActivityMapper, id, "储值充值活动");
        }
        return storedActivityMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 充值
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeResult recharge(RechargeRequest request)
    {
        if (request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        BigDecimal amount = request.getRechargeAmount();
        if (amount == null || amount.signum() <= 0)
        {
            throw BizErrors.ruleViolated("充值金额必须大于 0");
        }
        Integer payMethod = request.getPayMethod();
        if (payMethod == null || payMethod < 1 || payMethod > 3)
        {
            throw BizErrors.ruleViolated("支付方式不正确（1现金 2微信 3支付宝）");
        }
        referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");
        MemberBalance balance = selectByMemberId(request.getMemberId());
        if (balance == null)
        {
            throw BizErrors.notFound("会员储值账户不存在，请先为该会员补建账户");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal gift = BigDecimal.ZERO;
        LocalDateTime giftExpireTime = null;
        if (request.getActivityId() != null)
        {
            StoredActivity activity = storedActivityMapper.selectById(request.getActivityId());
            if (activity == null || !Integer.valueOf(1).equals(activity.getStatus()))
            {
                throw BizErrors.notFound("充值活动不存在或已停用");
            }
            if (activity.getStartTime() != null && now.isBefore(activity.getStartTime()))
            {
                throw BizErrors.ruleViolated("充值活动尚未开始");
            }
            if (activity.getEndTime() != null && now.isAfter(activity.getEndTime()))
            {
                throw BizErrors.ruleViolated("充值活动已结束");
            }
            if (amount.compareTo(nz(activity.getRechargeAmount())) != 0)
            {
                throw BizErrors.ruleViolated("充值金额不在活动档位内（本活动档位 "
                        + activity.getRechargeAmount() + " 元）");
            }
            gift = nz(activity.getGiftAmount());
            if (gift.signum() > 0 && nzi(activity.getGiftExpireDays()) > 0)
            {
                giftExpireTime = now.plusDays(nzi(activity.getGiftExpireDays()));
            }
        }

        if (baseMapper.addBalance(request.getMemberId(), amount, gift) <= 0)
        {
            throw BizErrors.notFound("会员储值账户不存在，请先为该会员补建账户");
        }
        baseMapper.syncBalanceSummary(request.getMemberId());
        MemberBalance after = selectByMemberId(request.getMemberId());

        String payName = (payMethod == 1) ? "现金" : (payMethod == 2) ? "微信" : "支付宝";
        insertLog(request.getMemberId(), 1, amount, after, request.getRefNo(), "储值充值（" + payName + "）");
        if (gift.signum() > 0)
        {
            String giftRemark = (giftExpireTime == null)
                    ? "充值活动赠送（长期有效）"
                    : "充值活动赠送（有效期至 " + TIME_FMT.format(giftExpireTime) + "）";
            insertLog(request.getMemberId(), 2, gift, after, request.getRefNo(), giftRemark);
        }

        RechargeResult result = new RechargeResult();
        result.setPrincipalAfter(nz(after.getPrincipalBalance()));
        result.setGiftAfter(nz(after.getGiftBalance()));
        result.setTotalAfter(nz(after.getTotalBalance()));
        result.setGiftAmount(gift);
        result.setGiftExpireTime(giftExpireTime);
        return result;
    }

    // ==================================================================
    // 销售链路（收编入口）
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p>C20 裁决（2026-10-11）：后台出账退款 —— 先核销赠送金、再退本金；
     * `refundPrincipal=false` 时只允许核销赠送金。实际出款（现金/原路）在线下完成，
     * 系统只核销账户并留痕（单机无商户号，线上退款留阶段九）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundResult refund(RefundRequest request)
    {
        if (request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        BigDecimal amount = request.getRefundAmount();
        if (amount == null || amount.signum() <= 0)
        {
            throw BizErrors.ruleViolated("退款金额必须大于 0");
        }
        if (request.getReason() == null || request.getReason().isBlank())
        {
            throw BizErrors.ruleViolated("退款原因未填");
        }
        String reason = request.getReason().trim();
        if (reason.length() > 200)
        {
            throw BizErrors.ruleViolated("退款原因不能超过 200 个字符");
        }
        boolean allowPrincipal = (request.getRefundPrincipal() == null)
                || request.getRefundPrincipal();

        referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");
        MemberBalance balance = selectByMemberId(request.getMemberId());
        if (balance == null)
        {
            throw BizErrors.notFound("会员储值账户不存在（会员ID=" + request.getMemberId() + "）");
        }
        BigDecimal total = nz(balance.getTotalBalance());
        if (amount.compareTo(total) > 0)
        {
            throw BizErrors.ruleViolated("退款金额超过可用余额（当前可用 " + total + " 元）");
        }

        BigDecimal giftBalance = nz(balance.getGiftBalance());
        BigDecimal giftPart = (giftBalance.compareTo(amount) <= 0) ? giftBalance : amount;
        BigDecimal principalPart = amount.subtract(giftPart);
        if (principalPart.signum() > 0 && !allowPrincipal)
        {
            throw BizErrors.ruleViolated("赠送金不足且本金退款未开启：本次最多可退 " + giftPart + " 元");
        }

        if (baseMapper.deductBalance(request.getMemberId(), principalPart, giftPart, amount) <= 0)
        {
            throw BizErrors.ruleViolated("退款金额超过可用余额（余额刚被其他操作变动，请刷新后重试）");
        }
        baseMapper.syncBalanceSummary(request.getMemberId());
        MemberBalance after = selectByMemberId(request.getMemberId());

        // 出账：类型 5/6 记负数（消费退货的「回存」记正数，按符号区分方向）
        if (principalPart.signum() > 0)
        {
            insertLog(request.getMemberId(), 5, principalPart.negate(), after, request.getRefNo(), reason);
        }
        if (giftPart.signum() > 0)
        {
            insertLog(request.getMemberId(), 6, giftPart.negate(), after, request.getRefNo(), reason);
        }

        RefundResult result = new RefundResult();
        result.setPrincipalAfter(nz(after.getPrincipalBalance()));
        result.setGiftAfter(nz(after.getGiftBalance()));
        result.setTotalAfter(nz(after.getTotalBalance()));
        result.setRefundGift(giftPart);
        result.setRefundPrincipal(principalPart);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(Long memberId, BigDecimal principalPart, BigDecimal giftPart,
            BigDecimal amount, String refNo, String principalRemark, String giftRemark)
    {
        if (memberId == null || amount == null || amount.signum() <= 0)
        {
            throw BizErrors.badRequest("储值扣减参数不完整");
        }
        if (baseMapper.deductBalance(memberId, nz(principalPart), nz(giftPart), amount) <= 0)
        {
            throw BizErrors.ruleViolated("储值余额不足：本笔需支付 " + amount + " 元");
        }
        baseMapper.syncBalanceSummary(memberId);
        MemberBalance after = selectByMemberId(memberId);
        if (nz(principalPart).signum() > 0)
        {
            insertLog(memberId, 3, nz(principalPart).negate(), after, refNo, principalRemark);
        }
        if (nz(giftPart).signum() > 0)
        {
            insertLog(memberId, 4, nz(giftPart).negate(), after, refNo, giftRemark);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundBalance(Long memberId, BigDecimal principalPart, BigDecimal giftPart,
            String refNo, String principalRemark, String giftRemark)
    {
        if (memberId == null)
        {
            throw BizErrors.badRequest("储值退还参数不完整");
        }
        if (baseMapper.refundBalance(memberId, nz(principalPart), nz(giftPart)) <= 0)
        {
            throw BizErrors.notFound("会员储值账户不存在（会员ID=" + memberId + "）");
        }
        baseMapper.syncBalanceSummary(memberId);
        MemberBalance after = selectByMemberId(memberId);
        if (nz(principalPart).signum() > 0)
        {
            insertLog(memberId, 5, nz(principalPart), after, refNo, principalRemark);
        }
        if (nz(giftPart).signum() > 0)
        {
            insertLog(memberId, 6, nz(giftPart), after, refNo, giftRemark);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>阶段六步骤②新增（A45）：二手书回收结算入储值账户 —— 本金入账，
     * 流水记 {@code amount_type=1 充值}，并同步会员冗余汇总。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void creditRecycleSettlement(Long memberId, BigDecimal amount, String refNo,
            String remark)
    {
        if (memberId == null || amount == null || amount.signum() <= 0)
        {
            throw BizErrors.badRequest("回收结算入账参数不完整");
        }
        referenceGuard.requireExists(memberMapper, memberId, "会员");
        if (baseMapper.addBalance(memberId, amount, BigDecimal.ZERO) <= 0)
        {
            throw BizErrors.notFound("会员储值账户不存在，请先为该会员补建账户（会员ID=" + memberId + "）");
        }
        baseMapper.syncBalanceSummary(memberId);
        MemberBalance after = selectByMemberId(memberId);
        insertLog(memberId, 1, amount, after, refNo, remark);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private MemberBalance selectByMemberId(Long memberId)
    {
        List<MemberBalance> rows = baseMapper.selectList(
                new LambdaQueryWrapper<MemberBalance>().eq(MemberBalance::getMemberId, memberId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void insertLog(Long memberId, int amountType, BigDecimal change, MemberBalance after,
            String refNo, String remark)
    {
        StoredValueLog log = new StoredValueLog();
        log.setMemberId(memberId);
        log.setAmountType(amountType);
        log.setChangeAmount(change);
        log.setPrincipalAfter(nz(after == null ? null : after.getPrincipalBalance()));
        log.setGiftAfter(nz(after == null ? null : after.getGiftBalance()));
        log.setRefNo(refNo == null ? "" : refNo);
        log.setRemark(remark == null ? "" : remark);
        log.setCreateBy(currentUsername());
        log.setCreateTime(LocalDateTime.now());
        storedValueLogMapper.insert(log);
    }

    private String currentUsername()
    {
        try
        {
            String name = SecurityUtils.getUsername();
            return name == null ? "" : name;
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private BigDecimal nz(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int nzi(Integer value)
    {
        return value == null ? 0 : value;
    }
}
