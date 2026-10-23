package com.tbgbook.bookstore.borrow.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.BorrowRule;
import com.tbgbook.bookstore.borrow.domain.DepositLog;
import com.tbgbook.bookstore.borrow.domain.Penalty;
import com.tbgbook.bookstore.borrow.domain.PenaltyPayRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyPayResult;
import com.tbgbook.bookstore.borrow.domain.PenaltyReduceRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyReduceResult;
import com.tbgbook.bookstore.borrow.domain.PenaltyRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyResult;
import com.tbgbook.bookstore.borrow.mapper.BorrowRecordMapper;
import com.tbgbook.bookstore.borrow.mapper.DepositLogMapper;
import com.tbgbook.bookstore.borrow.mapper.PenaltyMapper;
import com.tbgbook.bookstore.borrow.service.IBorrowRuleService;
import com.tbgbook.bookstore.borrow.service.IPenaltyService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 罚金与赔偿单 Service 实现（阶段五步骤④第二批，FR-4.5.5 / FR-4.5.7）。
 *
 * <p><b>自定口径（登记 A43）</b>：
 * <ol>
 *   <li><b>金额由服务端算</b>：逾期罚金 = {@code fine_per_day × 逾期天数}，单册封顶 = 定价 ×
 *       {@code fine_max_rate}；丢失 / 严重破损赔偿 = 定价 × {@code compensate_rate}（默认 80%）；
 *       逾期试算**按当前时间与应还时间现算**，不信任借阅单上可能过期的 {@code overdue_days} 快照；</li>
 *   <li><b>押金优先扣除</b>：生成时 {@code deductDeposit=true}（默认）先扣押金（条件更新），
 *       扣多少写多少；扣完仍有余额 → {@code payStatus=2已缴}，否则 {@code 3部分缴纳}，
 *       押金为 0 → {@code 1待缴}；</li>
 *   <li><b>联动押金流水</b>：实际扣除金额 &gt; 0 时写 {@code biz_deposit_log.change_type=3扣罚金}
 *       或 {@code 4转赔偿}，并同步 {@code biz_member.deposit_balance}；</li>
 *   <li><b>缴费</b>：只允许 {@code payStatus in (1,3)} 缴费，金额必须等于待缴额
 *       （{@code amount - deductDeposit}），缴清置 2；</li>
 *   <li><b>减免</b>：仅店长（管理员或持 {@code borrow:penalty:reduce}）可操作，
 *       减免额 ≤ 待缴额，置 {@code payStatus=4已减免} 并记审批人 / 时间；</li>
 *   <li><b>不提供删除</b>：罚金属财务凭证（契约明文）。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class PenaltyServiceImpl extends ServiceImpl<PenaltyMapper, Penalty> implements IPenaltyService
{
    /** 罚金单号前缀（需求 §3.2 编码表：罚金单 PN） */
    private static final String PENALTY_NO_PREFIX = "PN";

    private static final DateTimeFormatter NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final BookMapper bookMapper;

    private final BorrowRecordMapper borrowRecordMapper;

    private final DepositLogMapper depositLogMapper;

    private final IBorrowRuleService borrowRuleService;

    @Override
    public List<Penalty> selectPenaltyList(Penalty penalty)
    {
        return baseMapper.selectPenaltyList(penalty);
    }

    @Override
    public Penalty selectPenaltyById(Long penaltyId)
    {
        return referenceGuard.requireExists(baseMapper, penaltyId, "罚金 / 赔偿单");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PenaltyResult generate(PenaltyRequest request)
    {
        if (request == null || request.getMemberId() == null || request.getBookId() == null)
        {
            throw BizErrors.badRequest("会员ID与图书ID不能为空");
        }
        Integer type = request.getPenaltyType();
        if (type == null || type < Penalty.TYPE_OVERDUE || type > Penalty.TYPE_DAMAGED)
        {
            throw BizErrors.ruleViolated("罚款类型取值必须是 1逾期罚金 / 2丢失赔偿 / 3严重破损赔偿");
        }
        Member member = referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");
        Book book = referenceGuard.requireExists(bookMapper, request.getBookId(), "图书");
        BorrowRule rule = borrowRuleService.requireActiveRule();

        BigDecimal calcBase = request.getCalcBase() == null
                ? nzd(book.getPrice()) : request.getCalcBase();
        BigDecimal amount;
        String detail;
        if (type == Penalty.TYPE_OVERDUE)
        {
            BorrowRecord borrow = null;
            if (request.getBorrowId() != null)
            {
                borrow = referenceGuard.requireExists(borrowRecordMapper, request.getBorrowId(),
                        "借阅单");
            }
            LocalDateTime due;
            if (borrow != null)
            {
                due = borrow.getDueTime();
            }
            else if (request.getBorrowId() != null)
            {
                due = borrowRecordMapper.selectActiveDueTime(member.getMemberId(),
                        request.getBookId());
            }
            else
            {
                due = null;
            }
            int days = overdueDays(due, LocalDateTime.now());
            if (days <= 0)
            {
                throw BizErrors.ruleViolated("该借阅单当前未逾期，无需生成逾期罚金");
            }
            BigDecimal raw = nzd(rule.getFinePerDay())
                    .multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cap = calcBase.multiply(nzd(rule.getFineMaxRate()))
                    .setScale(2, RoundingMode.HALF_UP);
            boolean capped = cap.signum() > 0 && raw.compareTo(cap) > 0;
            amount = capped ? cap : raw;
            detail = rule.getFinePerDay() + " 元/册/天 × " + days + " 天 = " + raw + " 元"
                    + (capped ? "，封顶为定价的 " + rule.getFineMaxRate() + "（" + cap + " 元）" : "");
        }
        else
        {
            BigDecimal rate = nzd(rule.getCompensateRate());
            amount = request.getAmount() == null
                    ? calcBase.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    : request.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0)
            {
                throw BizErrors.ruleViolated("赔偿金额必须大于 0");
            }
            BigDecimal max = calcBase.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            if (max.signum() > 0 && amount.compareTo(max) > 0)
            {
                throw BizErrors.ruleViolated("赔偿金额 " + amount + " 超过规则允许的上限 " + max
                        + "（定价 × 赔偿比例 " + rule.getCompensateRate() + "）");
            }
            detail = "图书定价 " + calcBase + " × 赔偿比例 " + rate + " = " + amount + " 元";
        }

        boolean deduct = request.getDeductDeposit() == null
                || Boolean.TRUE.equals(request.getDeductDeposit());
        BigDecimal deducted = BigDecimal.ZERO;
        if (deduct)
        {
            deducted = deductFromDeposit(member, amount);
        }
        BigDecimal remain = amount.subtract(deducted);
        int payStatus = remain.signum() <= 0 ? Penalty.PAY_PAID
                : (deducted.signum() > 0 ? Penalty.PAY_PARTIAL : Penalty.PAY_PENDING);

        LocalDateTime now = LocalDateTime.now();
        Penalty penalty = new Penalty();
        penalty.setPenaltyNo(generateNo());
        penalty.setMemberId(member.getMemberId());
        penalty.setBorrowId(request.getBorrowId() == null ? 0L : request.getBorrowId());
        penalty.setBookId(book.getBookId());
        penalty.setPenaltyType(type);
        penalty.setAmount(amount);
        penalty.setCalcBase(calcBase);
        penalty.setCalcDetail(detail);
        penalty.setDeductDeposit(deducted);
        penalty.setPayStatus(payStatus);
        penalty.setPayMethod(deducted.signum() > 0 && remain.signum() <= 0 ? 4 : 0);
        penalty.setPayTime(remain.signum() <= 0 ? now : null);
        penalty.setAuditBy("");
        penalty.setCreateBy(currentUsername());
        penalty.setCreateTime(now);
        penalty.setUpdateBy(currentUsername());
        penalty.setUpdateTime(now);
        penalty.setRemark(request.getRemark() == null ? "" : request.getRemark());
        baseMapper.insert(penalty);

        if (deducted.signum() > 0)
        {
            writeDepositLog(member.getMemberId(),
                    type == Penalty.TYPE_OVERDUE ? DepositLog.TYPE_FINE : DepositLog.TYPE_COMPENSATE,
                    deducted.negate(), nzd(member.getDepositBalance()).subtract(deducted),
                    penalty.getPenaltyNo(), now);
        }

        PenaltyResult result = new PenaltyResult();
        result.setPenaltyId(penalty.getPenaltyId());
        result.setPenaltyNo(penalty.getPenaltyNo());
        result.setAmount(amount);
        result.setCalcDetail(detail);
        result.setDeductDeposit(deducted);
        result.setPayStatus(payStatus);
        result.setRemainAmount(remain.max(BigDecimal.ZERO));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PenaltyPayResult pay(Long penaltyId, PenaltyPayRequest request)
    {
        Penalty penalty = referenceGuard.requireExists(baseMapper, penaltyId, "罚金 / 赔偿单");
        if (Integer.valueOf(Penalty.PAY_PAID).equals(penalty.getPayStatus()))
        {
            throw BizErrors.conflict("该单据已缴清，不能重复缴费");
        }
        if (Integer.valueOf(Penalty.PAY_REDUCED).equals(penalty.getPayStatus()))
        {
            throw BizErrors.conflict("该单据已减免，无需缴费");
        }
        if (request == null || request.getPayMethod() == null || request.getPayAmount() == null)
        {
            throw BizErrors.badRequest("缴费方式与金额不能为空");
        }
        BigDecimal remain = remainOf(penalty);
        BigDecimal payAmount = request.getPayAmount().setScale(2, RoundingMode.HALF_UP);
        if (payAmount.compareTo(remain) != 0)
        {
            throw BizErrors.ruleViolated("缴费金额 " + payAmount + " 与待缴金额 " + remain + " 不符");
        }
        LocalDateTime now = LocalDateTime.now();
        Penalty patch = new Penalty();
        patch.setPenaltyId(penaltyId);
        patch.setPayStatus(Penalty.PAY_PAID);
        patch.setPayTime(now);
        patch.setPayMethod(request.getPayMethod());
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        patch.setRemark(mergeRemark(penalty.getRemark(), request.getRemark()));
        baseMapper.updateById(patch);

        PenaltyPayResult result = new PenaltyPayResult();
        result.setPenaltyNo(penalty.getPenaltyNo());
        result.setPayStatus(Penalty.PAY_PAID);
        result.setPayTime(now);
        result.setRemainAmount(BigDecimal.ZERO.setScale(2));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PenaltyReduceResult reduce(Long penaltyId, PenaltyReduceRequest request)
    {
        requireReducePermission();
        Penalty penalty = referenceGuard.requireExists(baseMapper, penaltyId, "罚金 / 赔偿单");
        if (Integer.valueOf(Penalty.PAY_PAID).equals(penalty.getPayStatus()))
        {
            throw BizErrors.conflict("该单据已缴清，不能减免");
        }
        if (request == null || request.getReduceAmount() == null
                || request.getReduceAmount().signum() <= 0)
        {
            throw BizErrors.ruleViolated("减免金额必须大于 0");
        }
        if (request.getReason() == null || request.getReason().isBlank())
        {
            throw BizErrors.ruleViolated("减免原因未填");
        }
        BigDecimal remain = remainOf(penalty);
        BigDecimal reduce = request.getReduceAmount().setScale(2, RoundingMode.HALF_UP);
        if (reduce.compareTo(remain) > 0)
        {
            throw BizErrors.ruleViolated("减免金额 " + reduce + " 超过待缴金额 " + remain);
        }
        LocalDateTime now = LocalDateTime.now();
        Penalty patch = new Penalty();
        patch.setPenaltyId(penaltyId);
        patch.setPayStatus(Penalty.PAY_REDUCED);
        patch.setAuditBy(currentUsername());
        patch.setAuditTime(now);
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        patch.setRemark(mergeRemark(penalty.getRemark(),
                "减免 " + reduce + " 元：" + request.getReason().trim()));
        baseMapper.updateById(patch);

        PenaltyReduceResult result = new PenaltyReduceResult();
        result.setPenaltyNo(penalty.getPenaltyNo());
        result.setPayStatus(Penalty.PAY_REDUCED);
        result.setReduceAmount(reduce);
        result.setAuditBy(patch.getAuditBy());
        result.setAuditTime(now);
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 从押金扣除（条件更新；余额不足时按可用额扣） */
    private BigDecimal deductFromDeposit(Member member, BigDecimal amount)
    {
        BigDecimal balance = nzd(member.getDepositBalance());
        if (balance.signum() <= 0)
        {
            return BigDecimal.ZERO;
        }
        BigDecimal take = balance.min(amount);
        if (memberMapper.deductDeposit(member.getMemberId(), take) == 0)
        {
            // 并发下被抢 → 按 0 扣除，生成待缴账单（契约允许）
            return BigDecimal.ZERO;
        }
        member.setDepositBalance(balance.subtract(take));
        return take;
    }

    private void writeDepositLog(Long memberId, int changeType, BigDecimal change,
            BigDecimal after, String refNo, LocalDateTime now)
    {
        DepositLog log = new DepositLog();
        log.setMemberId(memberId);
        log.setChangeType(changeType);
        log.setAmountChange(change);
        log.setDepositAfter(after);
        log.setPayMethod(0);
        log.setRefNo(refNo);
        log.setCreateBy(currentUsername());
        log.setCreateTime(now);
        log.setRemark(changeType == DepositLog.TYPE_FINE ? "罚金从押金扣除" : "赔偿从押金扣除");
        depositLogMapper.insert(log);
    }

    private BigDecimal remainOf(Penalty penalty)
    {
        return nzd(penalty.getAmount()).subtract(nzd(penalty.getDeductDeposit()))
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** 减免权限：店长（管理员或持 borrow:penalty:reduce） */
    private void requireReducePermission()
    {
        try
        {
            if (SecurityUtils.isAdmin() || SecurityUtils.hasPermi("borrow:penalty:reduce"))
            {
                return;
            }
        }
        catch (Exception ignored)
        {
            // 落到下面统一 403
        }
        throw BizErrors.forbidden("罚金减免需店长审批（borrow:penalty:reduce）");
    }

    private int overdueDays(LocalDateTime due, LocalDateTime at)
    {
        if (due == null || at == null)
        {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(due.toLocalDate(), at.toLocalDate());
        return days <= 0 ? 0 : (int) days;
    }

    private String generateNo()
    {
        Long maxId = baseMapper.selectList(new LambdaQueryWrapper<Penalty>()
                .select(Penalty::getPenaltyId)
                .orderByDesc(Penalty::getPenaltyId)
                .last("limit 1")).stream().map(Penalty::getPenaltyId).findFirst().orElse(0L);
        long next = (maxId == null ? 0L : maxId) + 1;
        return PENALTY_NO_PREFIX + LocalDateTime.now().format(NO_TIME_FORMAT)
                + String.format("%04d", next % 10000);
    }

    private String mergeRemark(String oldOne, String newOne)
    {
        if (newOne == null || newOne.isBlank())
        {
            return oldOne == null ? "" : oldOne;
        }
        return oldOne == null || oldOne.isBlank() ? newOne.trim() : oldOne + "；" + newOne.trim();
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
