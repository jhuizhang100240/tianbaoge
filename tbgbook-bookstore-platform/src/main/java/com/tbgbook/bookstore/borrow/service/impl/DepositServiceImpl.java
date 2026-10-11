package com.tbgbook.bookstore.borrow.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.DepositLog;
import com.tbgbook.bookstore.borrow.domain.DepositPayRequest;
import com.tbgbook.bookstore.borrow.domain.DepositRefundRequest;
import com.tbgbook.bookstore.borrow.domain.DepositResult;
import com.tbgbook.bookstore.borrow.domain.Penalty;
import com.tbgbook.bookstore.borrow.mapper.BorrowRecordMapper;
import com.tbgbook.bookstore.borrow.mapper.DepositLogMapper;
import com.tbgbook.bookstore.borrow.mapper.PenaltyMapper;
import com.tbgbook.bookstore.borrow.service.IDepositService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 借阅押金 Service 实现（阶段五步骤④第二批）。
 *
 * <p><b>自定口径（登记 A43）</b>：
 * <ol>
 *   <li><b>缴纳</b>：金额 &gt; 0；同步累加 {@code biz_member.deposit_balance} 并写
 *       {@code biz_deposit_log.change_type=1缴纳}；响应回该等级应缴额与是否已足额；</li>
 *   <li><b>退还</b>：三道闸门 —— 无在借（{@code status in (1,3) 且未归还}）、无未结清罚金
 *       （{@code pay_status in (1,3)}）、退还额 ≤ 押金余额；写 {@code change_type=2退还}；</li>
 *   <li><b>只增不删</b>：押金流水没有 update / delete 端点（与 7 张流水表同口径）。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class DepositServiceImpl extends ServiceImpl<DepositLogMapper, DepositLog>
        implements IDepositService
{
    /** 押金支付方式枚举（1现金 2微信 3支付宝 4储值余额） */
    private static final int PAY_METHOD_MIN = 1;

    private static final int PAY_METHOD_MAX = 4;

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final MemberLevelMapper memberLevelMapper;

    private final BorrowRecordMapper borrowRecordMapper;

    private final PenaltyMapper penaltyMapper;

    @Override
    public List<DepositLog> selectDepositLogList(DepositLog log)
    {
        return baseMapper.selectDepositLogList(log);
    }

    @Override
    public DepositLog selectDepositLogById(Long logId)
    {
        return referenceGuard.requireExists(baseMapper, logId, "押金流水");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepositResult pay(DepositPayRequest request)
    {
        if (request == null || request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        BigDecimal amount = request.getAmount() == null
                ? BigDecimal.ZERO : request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0)
        {
            throw BizErrors.ruleViolated("缴纳金额必须大于 0");
        }
        requirePayMethod(request.getPayMethod());
        Member member = referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");
        LocalDateTime now = LocalDateTime.now();
        if (memberMapper.addDeposit(member.getMemberId(), amount) == 0)
        {
            throw BizErrors.serverError("押金余额更新失败，请重试");
        }
        BigDecimal after = nzd(member.getDepositBalance()).add(amount);

        DepositLog log = new DepositLog();
        log.setMemberId(member.getMemberId());
        log.setChangeType(DepositLog.TYPE_PAY);
        log.setAmountChange(amount);
        log.setDepositAfter(after);
        log.setPayMethod(request.getPayMethod());
        log.setRefNo(request.getRefNo() == null ? "" : request.getRefNo().trim());
        log.setCreateBy(currentUsername());
        log.setCreateTime(now);
        log.setRemark(request.getRemark() == null ? "缴纳借阅押金" : request.getRemark().trim());
        baseMapper.insert(log);

        MemberLevel level = memberLevelMapper.selectById(member.getLevelId());
        BigDecimal required = level == null ? BigDecimal.ZERO : nzd(level.getDepositAmount());
        DepositResult result = new DepositResult();
        result.setDepositAfter(after);
        result.setLevelName(level == null ? member.getLevelName() : level.getLevelName());
        result.setDepositRequired(required);
        result.setPaid(after.compareTo(required) >= 0);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepositResult refund(DepositRefundRequest request)
    {
        if (request == null || request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        BigDecimal amount = request.getAmount() == null
                ? BigDecimal.ZERO : request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0)
        {
            throw BizErrors.ruleViolated("退还金额必须大于 0");
        }
        if (request.getReason() == null || request.getReason().isBlank())
        {
            throw BizErrors.ruleViolated("退还原因未填");
        }
        requirePayMethod(request.getPayMethod());
        Member member = referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");

        long borrowing = borrowRecordMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>()
                .eq(BorrowRecord::getMemberId, member.getMemberId())
                .in(BorrowRecord::getStatus, BorrowRecord.STATUS_BORROWING, BorrowRecord.STATUS_OVERDUE)
                .isNull(BorrowRecord::getReturnTime));
        if (borrowing > 0)
        {
            throw BizErrors.ruleViolated("会员仍有 " + borrowing + " 册在借图书，不能退还押金");
        }
        long unpaid = penaltyMapper.selectCount(new LambdaQueryWrapper<Penalty>()
                .eq(Penalty::getMemberId, member.getMemberId())
                .in(Penalty::getPayStatus, Penalty.PAY_PENDING, Penalty.PAY_PARTIAL));
        if (unpaid > 0)
        {
            throw BizErrors.ruleViolated("会员存在 " + unpaid + " 张未结清罚金 / 赔偿单，不能退还押金");
        }
        BigDecimal balance = nzd(member.getDepositBalance());
        if (amount.compareTo(balance) > 0)
        {
            throw BizErrors.ruleViolated("退还金额 " + amount + " 超过押金余额 " + balance);
        }

        LocalDateTime now = LocalDateTime.now();
        if (memberMapper.addDeposit(member.getMemberId(), amount.negate()) == 0)
        {
            throw BizErrors.concurrentModified("押金余额");
        }
        BigDecimal after = balance.subtract(amount);

        DepositLog log = new DepositLog();
        log.setMemberId(member.getMemberId());
        log.setChangeType(DepositLog.TYPE_REFUND);
        log.setAmountChange(amount.negate());
        log.setDepositAfter(after);
        log.setPayMethod(request.getPayMethod());
        log.setRefNo("");
        log.setCreateBy(currentUsername());
        log.setCreateTime(now);
        log.setRemark("退还借阅押金：" + request.getReason().trim());
        baseMapper.insert(log);

        MemberLevel level = memberLevelMapper.selectById(member.getLevelId());
        BigDecimal required = level == null ? BigDecimal.ZERO : nzd(level.getDepositAmount());
        DepositResult result = new DepositResult();
        result.setDepositAfter(after);
        result.setLevelName(level == null ? member.getLevelName() : level.getLevelName());
        result.setDepositRequired(required);
        result.setPaid(after.compareTo(required) >= 0);
        result.setRefundTime(now);
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void requirePayMethod(Integer payMethod)
    {
        if (payMethod == null || payMethod < PAY_METHOD_MIN || payMethod > PAY_METHOD_MAX)
        {
            throw BizErrors.ruleViolated("支付方式取值必须是 1现金 / 2微信 / 3支付宝 / 4储值余额");
        }
    }

    private BigDecimal nzd(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
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
}
