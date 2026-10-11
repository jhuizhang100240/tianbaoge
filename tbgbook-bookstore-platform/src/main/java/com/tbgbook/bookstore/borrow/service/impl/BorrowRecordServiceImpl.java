package com.tbgbook.bookstore.borrow.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.BorrowRenew;
import com.tbgbook.bookstore.borrow.domain.BorrowRule;
import com.tbgbook.bookstore.borrow.domain.BorrowStatView;
import com.tbgbook.bookstore.borrow.domain.LendRequest;
import com.tbgbook.bookstore.borrow.domain.LendResult;
import com.tbgbook.bookstore.borrow.domain.OverdueItem;
import com.tbgbook.bookstore.borrow.domain.OverdueNotifyRequest;
import com.tbgbook.bookstore.borrow.domain.OverdueNotifyResult;
import com.tbgbook.bookstore.borrow.domain.OverdueResult;
import com.tbgbook.bookstore.borrow.domain.Penalty;
import com.tbgbook.bookstore.borrow.domain.RenewRequest;
import com.tbgbook.bookstore.borrow.domain.RenewResult;
import com.tbgbook.bookstore.borrow.domain.ReturnRequest;
import com.tbgbook.bookstore.borrow.domain.ReturnResult;
import com.tbgbook.bookstore.borrow.mapper.BorrowRecordMapper;
import com.tbgbook.bookstore.borrow.mapper.BorrowRenewMapper;
import com.tbgbook.bookstore.borrow.mapper.BorrowReserveMapper;
import com.tbgbook.bookstore.borrow.mapper.PenaltyMapper;
import com.tbgbook.bookstore.borrow.service.IBorrowRecordService;
import com.tbgbook.bookstore.borrow.service.IBorrowRuleService;
import com.tbgbook.bookstore.borrow.support.BorrowStockSupport;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.domain.MemberMessage;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.MemberMessageMapper;
import com.tbgbook.bookstore.member.service.IMemberGrowthService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 借阅单 Service 实现（阶段五步骤④，页面 A-53~A-56）。
 *
 * <p><b>自定口径（登记 A42）</b>：
 * <ol>
 *   <li><b>资格校验顺序</b>（§4.5 规则 1）：会员状态正常 → 无逾期未还 → 在借册数 &lt; 等级上限
 *       → 押金已缴（该等级 {@code depositAmount &gt; 0} 时要求会员押金余额 ≥ 应缴额）；</li>
 *   <li><b>借出单号</b>：{@code BR + yyyyMMddHHmmss + 4 位流水}，流水取当前最大 borrow_id + 1
 *       （不用 information_schema 预取，避免 MySQL 8 缓存导致的撞号）；</li>
 *   <li><b>库存联动</b>：借出把书从「可售」搬到「借出」（{@code qtyAvailable-1 / qtyBorrowed+1}），
 *       归还反向搬回；实物总量守恒（§4.5 规则 2）；借出用条件更新防并发超借；</li>
 *   <li><b>逾期口径</b>：逾期天数 = {@code returnDate - dueDate} 的自然日差，**归还当日不计入**；
 *       罚金 = 罚金单价 × 册数 × 逾期天数，单册封顶 = 图书定价 × {@code fineMaxRate}（默认 50%）；</li>
 *   <li><b>续借</b>：{@code renewCount &lt; renew_times} 且图书无他人预约（排队中 / 已到书待取）
 *       且未逾期；延长期 = {@code renew_days}；写 {@code biz_borrow_renew}；</li>
 *   <li><b>按时归还</b>：{@code isOverdue=0} 且图书状况完好 / 轻微破损时奖励成长值 +5
 *       （§4.4 规则 2）；不动积分；</li>
 *   <li><b>严重破损</b>：{@code bookCondition=3} 且 {@code compensate=false} → 422；
 *       {@code compensate=true} 时把单置 4已赔偿（赔偿单与库存转残损由步骤⑤的罚金单生成动作处理）；</li>
 *   <li><b>催还提醒</b>：写入站内信 {@code biz_message}（{@code msgType=3 催还}，
 *       {@code sendStatus=2已发送}），真实的微信订阅消息推送留阶段九；</li>
 *   <li><b>删除保护</b>：借阅单有归还 / 逾期 / 罚金 / 赔偿痕迹一律 422，只能删「在借且无任何关联」的误录单。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class BorrowRecordServiceImpl extends ServiceImpl<BorrowRecordMapper, BorrowRecord>
        implements IBorrowRecordService
{
    /** 借阅单号前缀（需求 §3.2 编码表：借阅单 BR） */
    private static final String BORROW_NO_PREFIX = "BR";

    /** 会员状态：1正常 */
    private static final int MEMBER_NORMAL = 1;

    /** 按时归还成长值奖励（§4.4 规则 2） */
    private static final int ON_TIME_GROWTH_GAIN = 5;

    /** 借阅周期分布档位（与统计接口口径一致） */
    private static final String[] PERIOD_LABELS = { "0-7", "8-14", "15-21", "22-30", "30+" };

    private static final DateTimeFormatter NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final MemberLevelMapper memberLevelMapper;

    private final BookMapper bookMapper;

    private final IBorrowRuleService borrowRuleService;

    private final BorrowStockSupport borrowStockSupport;

    private final BorrowRenewMapper borrowRenewMapper;

    private final BorrowReserveMapper borrowReserveMapper;

    private final PenaltyMapper penaltyMapper;

    private final MemberMessageMapper memberMessageMapper;

    private final IMemberGrowthService memberGrowthService;

    // ==================================================================
    // 借阅单 CRUD
    // ==================================================================

    @Override
    public List<BorrowRecord> selectBorrowRecordList(BorrowRecord record)
    {
        return baseMapper.selectBorrowRecordList(record);
    }

    @Override
    public BorrowRecord selectBorrowRecordById(Long borrowId)
    {
        return referenceGuard.requireExists(baseMapper, borrowId, "借阅单");
    }

    /**
     * {@inheritDoc}
     *
     * <p>后台补录：不联动库存（库存已在别处调整），只做冗余字段回填与单号生成。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addBorrowRecord(BorrowRecord record)
    {
        if (record.getMemberId() == null || record.getBookId() == null)
        {
            throw BizErrors.badRequest("会员ID与图书ID不能为空");
        }
        Member member = referenceGuard.requireExists(memberMapper, record.getMemberId(), "会员");
        Book book = referenceGuard.requireExists(bookMapper, record.getBookId(), "图书");
        record.setBorrowId(null);
        record.setBorrowNo(generateBorrowNo());
        record.setMemberNo(member.getMemberNo());
        record.setMemberName(member.getNickname());
        record.setLevelId(member.getLevelId());
        record.setIsbn(book.getIsbn());
        record.setTitle(book.getTitle());
        if (record.getBarCode() == null)
        {
            record.setBarCode("");
        }
        if (record.getLocationId() == null)
        {
            record.setLocationId(0L);
        }
        if (record.getDepositAmount() == null)
        {
            record.setDepositAmount(BigDecimal.ZERO);
        }
        if (record.getBorrowTime() == null)
        {
            record.setBorrowTime(LocalDateTime.now());
        }
        if (record.getDueTime() == null)
        {
            MemberLevel level = referenceGuard.requireExists(memberLevelMapper,
                    member.getLevelId(), "会员等级");
            record.setDueTime(record.getBorrowTime()
                    .plusDays(level.getBorrowDays() == null ? 30 : level.getBorrowDays()));
        }
        record.setRenewCount(0);
        record.setIsOverdue(0);
        record.setOverdueDays(0);
        record.setFineAmount(BigDecimal.ZERO);
        record.setFinePaid(BigDecimal.ZERO);
        record.setIsCompensated(0);
        record.setStatus(BorrowRecord.STATUS_BORROWING);
        record.setOperatorBy(currentUsername());
        return baseMapper.insert(record);
    }

    /**
     * {@inheritDoc}
     *
     * <p>仅备注与取书库位可改；其余字段服务端从库里拷回。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateBorrowRecord(BorrowRecord record)
    {
        if (record.getBorrowId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的借阅单");
        }
        BorrowRecord existing = referenceGuard.requireExists(baseMapper, record.getBorrowId(), "借阅单");
        record.setBorrowNo(existing.getBorrowNo());
        record.setMemberId(existing.getMemberId());
        record.setMemberNo(existing.getMemberNo());
        record.setMemberName(existing.getMemberName());
        record.setLevelId(existing.getLevelId());
        record.setBookId(existing.getBookId());
        record.setIsbn(existing.getIsbn());
        record.setTitle(existing.getTitle());
        record.setBarCode(existing.getBarCode());
        record.setDepositAmount(existing.getDepositAmount());
        record.setBorrowTime(existing.getBorrowTime());
        record.setDueTime(existing.getDueTime());
        record.setReturnTime(existing.getReturnTime());
        record.setRenewCount(existing.getRenewCount());
        record.setIsOverdue(existing.getIsOverdue());
        record.setOverdueDays(existing.getOverdueDays());
        record.setFineAmount(existing.getFineAmount());
        record.setFinePaid(existing.getFinePaid());
        record.setIsCompensated(existing.getIsCompensated());
        record.setStatus(existing.getStatus());
        record.setOperatorBy(existing.getOperatorBy());
        record.setCreateBy(existing.getCreateBy());
        record.setCreateTime(existing.getCreateTime());
        record.setUpdateBy(currentUsername());
        record.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(record);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeBorrowRecords(Long[] borrowIds)
    {
        List<Long> idList = (borrowIds == null) ? Collections.emptyList() : Arrays.asList(borrowIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "借阅单");
        for (Serializable id : ids)
        {
            BorrowRecord row = referenceGuard.requireExists(baseMapper, id, "借阅单");
            if (!Integer.valueOf(BorrowRecord.STATUS_BORROWING).equals(row.getStatus())
                    || row.getReturnTime() != null
                    || nzd(row.getFineAmount()).signum() > 0
                    || Integer.valueOf(1).equals(row.getIsCompensated()))
            {
                throw BizErrors.ruleViolated("借阅单已有归还 / 逾期 / 罚金 / 赔偿痕迹，需要留痕不能删除"
                        + "（borrowId=" + id + "）");
            }
            long penalties = penaltyMapper.selectCount(new LambdaQueryWrapper<Penalty>()
                    .eq(Penalty::getBorrowId, id));
            if (penalties > 0)
            {
                throw BizErrors.ruleViolated("借阅单已关联罚金 / 赔偿单，不能删除（borrowId=" + id + "）");
            }
            long renews = borrowRenewMapper.selectCount(new LambdaQueryWrapper<BorrowRenew>()
                    .eq(BorrowRenew::getBorrowId, id));
            if (renews > 0)
            {
                throw BizErrors.ruleViolated("借阅单已有续借记录，不能删除（borrowId=" + id + "）");
            }
        }
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 借出 / 归还 / 续借
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LendResult lend(LendRequest request)
    {
        if (request == null || request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        String code = trimToNull(request.getCode());
        if (code == null)
        {
            throw BizErrors.badRequest("图书条码 / ISBN 不能为空");
        }
        Member member = referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");
        MemberLevel level = referenceGuard.requireExists(memberLevelMapper,
                member.getLevelId(), "会员等级");
        BorrowRule rule = borrowRuleService.requireActiveRule();

        // ① 会员状态正常
        if (!Integer.valueOf(MEMBER_NORMAL).equals(member.getStatus()))
        {
            throw BizErrors.ruleViolated("会员当前状态为「"
                    + statusName(member.getStatus()) + "」，不能借阅");
        }
        // ② 无逾期未还
        int overdue = countOverdue(member.getMemberId());
        if (overdue > 0 && !Integer.valueOf(0).equals(rule.getBlockOverdueBorrow()))
        {
            throw BizErrors.ruleViolated("该会员有 " + overdue + " 册逾期未还图书，"
                    + "按规则禁止发起新的借阅；请先归还并结清罚金");
        }
        // ③ 在借册数 < 等级上限
        int borrowing = baseMapper.countBorrowing(member.getMemberId());
        int limit = level.getBorrowQtyLimit() == null ? 0 : level.getBorrowQtyLimit();
        if (borrowing >= limit)
        {
            throw BizErrors.ruleViolated("该会员在借 " + borrowing + " 册，已达等级「"
                    + level.getLevelName() + "」的可借上限 " + limit + " 册");
        }
        // ④ 押金已缴
        BigDecimal required = nzd(level.getDepositAmount());
        BigDecimal paid = nzd(member.getDepositBalance());
        if (required.signum() > 0 && paid.compareTo(required) < 0)
        {
            throw BizErrors.ruleViolated("该等级需缴纳借阅押金 " + required + " 元，当前仅 "
                    + paid + " 元；请先缴押金（POST /borrow/deposit/pay）");
        }

        Book book = findBookByCode(code);
        if (book == null)
        {
            throw BizErrors.notFound("条码「" + code + "」未命中任何图书");
        }
        if (book.getStatus() != null && (book.getStatus() == 2 || book.getStatus() == 4))
        {
            throw BizErrors.ruleViolated("《" + book.getTitle() + "》已"
                    + (book.getStatus() == 2 ? "下架" : "停售") + "，不能借阅");
        }

        LocalDateTime now = LocalDateTime.now();
        String borrowNo = generateBorrowNo();
        Long locationId = borrowStockSupport.lend(book.getBookId(), borrowNo);

        BorrowRecord record = new BorrowRecord();
        record.setBorrowNo(borrowNo);
        record.setMemberId(member.getMemberId());
        record.setMemberNo(member.getMemberNo());
        record.setMemberName(member.getNickname());
        record.setLevelId(member.getLevelId());
        record.setBookId(book.getBookId());
        record.setIsbn(book.getIsbn());
        record.setTitle(book.getTitle());
        record.setBarCode(code.toUpperCase().startsWith("BC") ? code : "");
        record.setLocationId(locationId == null ? 0L : locationId);
        record.setDepositAmount(required);
        record.setBorrowTime(now);
        record.setDueTime(now.plusDays(level.getBorrowDays() == null ? 30 : level.getBorrowDays()));
        record.setRenewCount(0);
        record.setIsOverdue(0);
        record.setOverdueDays(0);
        record.setFineAmount(BigDecimal.ZERO);
        record.setFinePaid(BigDecimal.ZERO);
        record.setIsCompensated(0);
        record.setStatus(BorrowRecord.STATUS_BORROWING);
        record.setOperatorBy(currentUsername());
        record.setRemark(request.getRemark() == null ? "" : request.getRemark());
        record.setCreateBy(currentUsername());
        record.setCreateTime(now);
        record.setUpdateBy(currentUsername());
        record.setUpdateTime(now);
        baseMapper.insert(record);

        LendResult result = new LendResult();
        result.setBorrowId(record.getBorrowId());
        result.setBorrowNo(record.getBorrowNo());
        result.setDueTime(record.getDueTime());
        result.setDepositAmount(record.getDepositAmount());
        result.setLevelName(level.getLevelName());
        result.setBorrowDays(level.getBorrowDays());
        result.setRemainQuota(Math.max(limit - borrowing - 1, 0));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult returnBook(ReturnRequest request)
    {
        if (request == null)
        {
            throw BizErrors.badRequest("还书参数不能为空");
        }
        String code = trimToNull(request.getCode());
        if (code == null)
        {
            throw BizErrors.badRequest("图书条码不能为空");
        }
        BorrowRecord record = baseMapper.selectActiveByCode(code);
        if (record == null && code.toUpperCase().startsWith("BC"))
        {
            record = baseMapper.selectActiveByCode(code.toUpperCase());
        }
        if (record == null)
        {
            throw BizErrors.notFound("条码「" + code + "」未匹配到在借记录");
        }
        int condition = request.getBookCondition() == null
                ? BorrowRecord.CONDITION_GOOD : request.getBookCondition();
        if (condition < 1 || condition > 3)
        {
            throw BizErrors.ruleViolated("图书状况取值必须是 1完好 / 2轻微破损 / 3严重破损");
        }
        boolean compensate = Boolean.TRUE.equals(request.getCompensate());
        if (condition == BorrowRecord.CONDITION_SERIOUS && !compensate)
        {
            throw BizErrors.ruleViolated("判定严重破损时必须选择转赔偿流程"
                    + "（compensate=true；赔偿金额与库存转残损由赔偿单处理）");
        }

        LocalDateTime now = LocalDateTime.now();
        int overdueDays = overdueDays(record.getDueTime(), now);
        boolean overdue = overdueDays > 0;
        Book book = bookMapper.selectById(record.getBookId());
        BigDecimal price = book == null ? BigDecimal.ZERO : nzd(book.getPrice());
        BorrowRule rule = borrowRuleService.requireActiveRule();
        BigDecimal fine = BigDecimal.ZERO;
        String fineDetail = "未逾期，无罚金";
        boolean capped = false;
        if (overdue)
        {
            BigDecimal raw = nzd(rule.getFinePerDay())
                    .multiply(BigDecimal.valueOf(overdueDays))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal cap = price.multiply(nzd(rule.getFineMaxRate()))
                    .setScale(2, RoundingMode.HALF_UP);
            fine = raw;
            if (cap.signum() > 0 && raw.compareTo(cap) > 0)
            {
                fine = cap;
                capped = true;
            }
            fineDetail = rule.getFinePerDay() + " 元/册/天 × " + overdueDays + " 天 = " + raw
                    + " 元" + (capped ? "，封顶为定价的 " + rule.getFineMaxRate()
                    + "（" + cap + " 元）" : "");
        }

        int status = compensate ? BorrowRecord.STATUS_COMPENSATED : BorrowRecord.STATUS_RETURNED;
        BorrowRecord patch = new BorrowRecord();
        patch.setBorrowId(record.getBorrowId());
        patch.setReturnTime(now);
        patch.setIsOverdue(overdue ? 1 : 0);
        patch.setOverdueDays(overdueDays);
        patch.setFineAmount(fine);
        patch.setFinePaid(BigDecimal.ZERO);
        patch.setIsCompensated(compensate ? 1 : 0);
        patch.setStatus(status);
        patch.setRemark(request.getRemark() == null ? record.getRemark() : request.getRemark());
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        baseMapper.updateById(patch);

        // 只有「未转赔偿」才把书搬回可售；转赔偿的书留在借出/残损流程里由赔偿单处理
        if (!compensate)
        {
            borrowStockSupport.giveBack(record.getBookId(), record.getLocationId(),
                    record.getBorrowNo());
        }

        int growthGain = 0;
        if (!overdue && !compensate
                && condition != BorrowRecord.CONDITION_SERIOUS)
        {
            memberGrowthService.addGrowth(record.getMemberId(), 2, ON_TIME_GROWTH_GAIN,
                    record.getBorrowNo(), "按时归还奖励");
            growthGain = ON_TIME_GROWTH_GAIN;
        }

        ReturnResult result = new ReturnResult();
        result.setBorrowNo(record.getBorrowNo());
        result.setMemberName(record.getMemberName());
        result.setTitle(record.getTitle());
        result.setBorrowTime(record.getBorrowTime());
        result.setDueTime(record.getDueTime());
        result.setReturnTime(now);
        result.setIsOverdue(overdue ? 1 : 0);
        result.setOverdueDays(overdueDays);
        result.setFineAmount(fine);
        result.setFinePaid(BigDecimal.ZERO);
        result.setFineDetail(fineDetail);
        result.setGrowthGain(growthGain);
        result.setStatus(status);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenewResult renew(Long borrowId, RenewRequest request)
    {
        BorrowRecord record = referenceGuard.requireExists(baseMapper, borrowId, "借阅单");
        if (record.getReturnTime() != null || Integer.valueOf(BorrowRecord.STATUS_RETURNED)
                .equals(record.getStatus())
                || Integer.valueOf(BorrowRecord.STATUS_COMPENSATED).equals(record.getStatus()))
        {
            throw BizErrors.conflict("借阅单已归还 / 已赔偿，不能续借");
        }
        LocalDateTime now = LocalDateTime.now();
        if (overdueDays(record.getDueTime(), now) > 0)
        {
            throw BizErrors.ruleViolated("借阅单已逾期，不能续借；请先归还并结清罚金");
        }
        BorrowRule rule = borrowRuleService.requireActiveRule();
        int maxTimes = rule.getRenewTimes() == null ? 0 : rule.getRenewTimes();
        int used = record.getRenewCount() == null ? 0 : record.getRenewCount();
        if (used >= maxTimes)
        {
            throw BizErrors.ruleViolated("该单已续借 " + used + " 次，达到上限 " + maxTimes + " 次");
        }
        MemberLevel level = memberLevelMapper.selectById(record.getLevelId());
        if (level != null && Integer.valueOf(0).equals(level.getCanRenew()))
        {
            throw BizErrors.ruleViolated("等级「" + level.getLevelName()
                    + "」不支持续借（biz_member_level.can_renew=0）");
        }
        if (baseMapper.countActiveReserve(record.getBookId()) > 0)
        {
            throw BizErrors.ruleViolated("该图书有他人预约（排队中 / 已到书待取），不可续借");
        }

        int renewDays = rule.getRenewDays() == null ? 14 : rule.getRenewDays();
        LocalDateTime before = record.getDueTime();
        LocalDateTime after = before.plusDays(renewDays);
        BorrowRecord patch = new BorrowRecord();
        patch.setBorrowId(borrowId);
        patch.setDueTime(after);
        patch.setRenewCount(used + 1);
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        baseMapper.updateById(patch);

        BorrowRenew renew = new BorrowRenew();
        renew.setBorrowId(borrowId);
        renew.setBeforeDueTime(before);
        renew.setAfterDueTime(after);
        renew.setRenewDays(renewDays);
        renew.setRenewBy(currentUsername());
        renew.setRenewChannel(request == null || request.getRenewChannel() == null
                ? BorrowRenew.CHANNEL_STAFF : request.getRenewChannel());
        renew.setCreateTime(now);
        borrowRenewMapper.insert(renew);

        RenewResult result = new RenewResult();
        result.setBeforeDueTime(before);
        result.setAfterDueTime(after);
        result.setRenewDays(renewDays);
        result.setRenewCount(used + 1);
        result.setRemainRenewTimes(Math.max(maxTimes - used - 1, 0));
        return result;
    }

    @Override
    public List<BorrowRenew> selectBorrowRenewList(BorrowRenew renew)
    {
        return borrowRenewMapper.selectBorrowRenewList(renew);
    }

    @Override
    public BorrowRenew selectBorrowRenewById(Long renewId)
    {
        return referenceGuard.requireExists(borrowRenewMapper, renewId, "借阅续借记录");
    }

    // ==================================================================
    // 逾期与统计
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public OverdueResult overdueList(Long memberId, Integer minOverdueDays, Integer status)
    {
        LocalDateTime now = LocalDateTime.now();
        List<OverdueItem> rows = baseMapper.selectOverdueList(memberId, minOverdueDays, status, now);
        BorrowRule rule = borrowRuleService.requireActiveRule();
        OverdueResult result = new OverdueResult();
        BigDecimal total = BigDecimal.ZERO;
        int cappedCount = 0;
        for (OverdueItem item : rows)
        {
            item.setPhone(maskPhone(item.getPhone()));
            int days = item.getOverdueDays() == null ? 0 : item.getOverdueDays();
            BigDecimal raw = nzd(rule.getFinePerDay())
                    .multiply(BigDecimal.valueOf(days))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal cap = nzd(item.getCalcBase()).multiply(nzd(rule.getFineMaxRate()))
                    .setScale(2, RoundingMode.HALF_UP);
            boolean capped = cap.signum() > 0 && raw.compareTo(cap) > 0;
            BigDecimal fine = capped ? cap : raw;
            item.setFineAmount(fine);
            item.setFineCapped(capped);
            if (capped)
            {
                cappedCount++;
            }
            total = total.add(fine);
        }
        result.setList(rows);
        OverdueResult.Summary summary = new OverdueResult.Summary();
        summary.setBorrowCount(rows.size());
        summary.setMemberCount((int) rows.stream()
                .map(OverdueItem::getMemberId).distinct().count());
        summary.setFineTotal(total);
        result.setSummary(summary);
        if (cappedCount > 0)
        {
            result.getSummary().setFineTotal(total);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OverdueNotifyResult notifyOverdue(OverdueNotifyRequest request)
    {
        if (request == null || request.getBorrowIds() == null || request.getBorrowIds().isEmpty())
        {
            throw BizErrors.ruleViolated("借阅单ID列表为空");
        }
        LocalDateTime now = LocalDateTime.now();
        OverdueNotifyResult result = new OverdueNotifyResult();
        int sent = 0;
        for (Long borrowId : request.getBorrowIds())
        {
            BorrowRecord record = baseMapper.selectById(borrowId);
            if (record == null)
            {
                fail(result, borrowId, "借阅单不存在");
                continue;
            }
            if (record.getReturnTime() != null)
            {
                fail(result, borrowId, "该借阅单已归还，无需催还");
                continue;
            }
            int days = overdueDays(record.getDueTime(), now);
            if (days <= 0)
            {
                fail(result, borrowId, "该借阅单尚未逾期");
                continue;
            }
            MemberMessage message = new MemberMessage();
            message.setMemberId(record.getMemberId());
            message.setMsgType(3);
            message.setTitle("图书逾期催还");
            message.setContent("《" + record.getTitle() + "》已逾期 " + days
                    + " 天，请尽快归还。应还日期：" + record.getDueTime()
                    + "，逾期罚金按规则累计。");
            message.setLinkUrl("/borrow/record/" + borrowId);
            message.setBizNo(record.getBorrowNo());
            message.setSendChannel(2);
            message.setSendStatus(2);
            message.setFailReason("");
            message.setSendTime(now);
            message.setIsRead(0);
            message.setReadTime(null);
            message.setCreateTime(now);
            memberMessageMapper.insert(message);
            sent++;
        }
        result.setSent(sent);
        result.setFailed(result.getFailDetails().size());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BorrowStatView statistics(LocalDate startDate, LocalDate endDate, String granularity)
    {
        if (startDate == null || endDate == null)
        {
            throw BizErrors.badRequest("startDate 与 endDate 不能为空");
        }
        if (startDate.isAfter(endDate))
        {
            throw BizErrors.ruleViolated("开始日期不能晚于结束日期");
        }
        String grain = (granularity == null || granularity.isBlank())
                ? "day" : granularity.trim().toLowerCase();
        if (!"day".equals(grain) && !"week".equals(grain) && !"month".equals(grain))
        {
            throw BizErrors.ruleViolated("granularity 取值必须是 day / week / month");
        }
        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.plusDays(1).atStartOfDay().minusNanos(1);

        BorrowStatView view = new BorrowStatView();
        for (Map<String, Object> row : baseMapper.selectLendTrend(from, to, grain))
        {
            BorrowStatView.TrendPoint point = new BorrowStatView.TrendPoint();
            point.setLabel(str(row.get("label")));
            point.setLendCount(intOf(row.get("lendCount")));
            point.setReturnCount(intOf(row.get("returnCount")));
            view.getTrend().add(point);
        }
        for (Map<String, Object> row : baseMapper.selectTopBooks(from, to, 10))
        {
            BorrowStatView.TopBook top = new BorrowStatView.TopBook();
            top.setBookId(longOf(row.get("bookId")));
            top.setIsbn(str(row.get("isbn")));
            top.setTitle(str(row.get("title")));
            top.setLendCount(intOf(row.get("lendCount")));
            view.getTopBooks().add(top);
        }
        Map<String, Integer> periodMap = new LinkedHashMap<>();
        for (String label : PERIOD_LABELS)
        {
            periodMap.put(label, 0);
        }
        int periodTotal = 0;
        for (Map<String, Object> row : baseMapper.selectPeriodDistribution(from, to))
        {
            String label = str(row.get("label"));
            int count = intOf(row.get("cnt"));
            if (periodMap.containsKey(label))
            {
                periodMap.put(label, count);
                periodTotal += count;
            }
        }
        for (Map.Entry<String, Integer> entry : periodMap.entrySet())
        {
            view.getPeriodDistribution().add(bucket(entry.getKey(), entry.getValue(), periodTotal));
        }
        Map<String, Object> rate = baseMapper.selectOverdueRate(from, to);
        int all = intOf(rate == null ? null : rate.get("totalCount"));
        int over = intOf(rate == null ? null : rate.get("overdueCount"));
        view.setOverdueRate(all == 0 ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(over).divide(BigDecimal.valueOf(all), 4, RoundingMode.HALF_UP));
        int levelTotal = 0;
        List<Map<String, Object>> levelRows = baseMapper.selectLevelDistribution(from, to);
        for (Map<String, Object> row : levelRows)
        {
            levelTotal += intOf(row.get("lendCount"));
        }
        for (Map<String, Object> row : levelRows)
        {
            BorrowStatView.LevelShare share = new BorrowStatView.LevelShare();
            share.setLevelId(longOf(row.get("levelId")));
            String name = str(row.get("levelName"));
            share.setLevelName(name == null && share.getLevelId() != null
                    ? "等级" + share.getLevelId() : name);
            share.setLendCount(intOf(row.get("lendCount")));
            share.setRatio(levelTotal == 0 ? BigDecimal.ZERO.setScale(4)
                    : BigDecimal.valueOf(share.getLendCount())
                    .divide(BigDecimal.valueOf(levelTotal), 4, RoundingMode.HALF_UP));
            view.getLevelDistribution().add(share);
        }
        return view;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 逾期天数：自然日差，归还当日不计入（dueDate 当天还 = 0） */
    private int overdueDays(LocalDateTime dueTime, LocalDateTime at)
    {
        if (dueTime == null || at == null)
        {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(dueTime.toLocalDate(), at.toLocalDate());
        return days <= 0 ? 0 : (int) days;
    }

    /** 该会员逾期未还册数（status=3 或 在借且已过应还日） */
    private int countOverdue(Long memberId)
    {
        LocalDateTime now = LocalDateTime.now();
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>()
                .eq(BorrowRecord::getMemberId, memberId)
                .isNull(BorrowRecord::getReturnTime)
                .and(w -> w.eq(BorrowRecord::getStatus, BorrowRecord.STATUS_OVERDUE)
                        .or().lt(BorrowRecord::getDueTime, now)));
        return count == null ? 0 : count.intValue();
    }

    /** 扫码定位图书：先按 ISBN，再按书名（与收银扫码同口径） */
    private Book findBookByCode(String code)
    {
        return bookMapper.selectOne(new LambdaQueryWrapper<Book>()
                .eq(Book::getIsbn, code)
                .last("limit 1"));
    }

    /** 借阅单号：BR + yyyyMMddHHmmss + 4 位流水（max(id)+1 末四位） */
    private String generateBorrowNo()
    {
        Long maxId = baseMapper.selectList(new LambdaQueryWrapper<BorrowRecord>()
                .select(BorrowRecord::getBorrowId)
                .orderByDesc(BorrowRecord::getBorrowId)
                .last("limit 1")).stream()
                .map(BorrowRecord::getBorrowId)
                .findFirst().orElse(0L);
        long next = (maxId == null ? 0L : maxId) + 1;
        return BORROW_NO_PREFIX + LocalDateTime.now().format(NO_TIME_FORMAT)
                + String.format("%04d", next % 10000);
    }

    private void fail(OverdueNotifyResult result, Long borrowId, String reason)
    {
        OverdueNotifyResult.FailDetail detail = new OverdueNotifyResult.FailDetail();
        detail.setBorrowId(borrowId);
        detail.setReason(reason);
        result.getFailDetails().add(detail);
    }

    private BorrowStatView.Bucket bucket(String label, int count, int total)
    {
        BorrowStatView.Bucket bucket = new BorrowStatView.Bucket();
        bucket.setLabel(label);
        bucket.setCount(count);
        bucket.setRatio(total == 0 ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(count).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
        return bucket;
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        switch (status)
        {
            case 1:
                return "正常";
            case 2:
                return "已冻结";
            case 3:
                return "黑名单";
            case 4:
                return "已注销";
            default:
                return "未知";
        }
    }

    private BigDecimal nzd(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 手机号脱敏（NFR-S8）：`138****1234`；管理员按平台口径明文豁免（同 A34）。
     */
    private String maskPhone(String phone)
    {
        if (phone == null || phone.length() < 7)
        {
            return phone;
        }
        try
        {
            if (SecurityUtils.isAdmin())
            {
                return phone;
            }
        }
        catch (Exception ignored)
        {
            // 无安全上下文 → 按脱敏处理
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String str(Object value)
    {
        return value == null ? null : value.toString();
    }

    private int intOf(Object value)
    {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private Long longOf(Object value)
    {
        return value == null ? null : ((Number) value).longValue();
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
