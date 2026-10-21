package com.tbgbook.bookstore.app.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.service.IAppBorrowService;
import com.tbgbook.bookstore.app.mapper.AppTradeMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.BorrowReserve;
import com.tbgbook.bookstore.borrow.domain.BorrowRule;
import com.tbgbook.bookstore.borrow.domain.LevelQuotaView;
import com.tbgbook.bookstore.borrow.domain.Penalty;
import com.tbgbook.bookstore.borrow.domain.PenaltyPayRequest;
import com.tbgbook.bookstore.borrow.domain.RenewRequest;
import com.tbgbook.bookstore.borrow.domain.RenewResult;
import com.tbgbook.bookstore.borrow.service.IBorrowRecordService;
import com.tbgbook.bookstore.borrow.service.IBorrowRuleService;
import com.tbgbook.bookstore.borrow.service.IPenaltyService;
import com.tbgbook.bookstore.borrow.service.IReserveService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;

import lombok.RequiredArgsConstructor;

/**
 * 会员端借阅 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AppBorrowServiceImpl implements IAppBorrowService
{
    private final IBorrowRecordService borrowRecordService;

    private final IBorrowRuleService borrowRuleService;

    private final IReserveService reserveService;

    private final IPenaltyService penaltyService;

    private final IMemberService memberService;

    private final BookMapper bookMapper;

    private final AppTradeMapper appTradeMapper;

    @Override
    public Map<String, Object> center()
    {
        Long memberId = AppContext.requireMemberId();
        Member member = memberService.selectMemberById(memberId);
        BorrowRecord query = new BorrowRecord();
        query.setMemberId(memberId);
        List<BorrowRecord> records = borrowRecordService.selectBorrowRecordList(query);
        List<BorrowRecord> borrowing = records.stream()
                .filter(r -> Integer.valueOf(1).equals(r.getStatus()) || Integer.valueOf(3).equals(r.getStatus()))
                .sorted(Comparator.comparing(BorrowRecord::getDueTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<BorrowReserve> reserves = reserveService.selectReserveList(reserveQuery(memberId, null));
        long overdue = records.stream().filter(r -> Integer.valueOf(3).equals(r.getStatus())).count();
        LevelQuotaView quotas = borrowRuleService.levelQuota(member.getLevelId());
        int quota = quotas.getLevels().stream()
                .filter(l -> member.getLevelId().equals(l.getLevelId()))
                .map(LevelQuotaView.LevelQuota::getBorrowQtyLimit).filter(Objects::nonNull)
                .findFirst().orElse(0);
        BigDecimal fineUnpaid = unpaidPenalty(memberId);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("currentBorrowed", borrowing.size());
        summary.put("quotaLimit", quota);
        summary.put("renewableCount", borrowing.stream().filter(r -> canRenew(r, memberId, quotas.getGlobalRule()).get("canRenew").equals(true)).count());
        summary.put("overdueCount", overdue);
        summary.put("depositBalance", member.getDepositBalance());
        summary.put("fineUnpaid", fineUnpaid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("borrowing", borrowing.stream().map(this::borrowView).toList());
        data.put("reserves", reserves.stream().filter(r -> r.getStatus() != null && r.getStatus() <= 2).toList());
        data.put("history", records.stream()
                .sorted(Comparator.comparing(BorrowRecord::getBorrowTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5).map(this::borrowView).toList());
        data.put("nextDueReminder", borrowing.stream().filter(r -> r.getDueTime() != null)
                .findFirst().map(BorrowRecord::getDueTime).orElse(null));
        return data;
    }

    @Override
    public Map<String, Object> list(Integer status, LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize)
    {
        Long memberId = AppContext.requireMemberId();
        BorrowRecord query = new BorrowRecord();
        query.setMemberId(memberId);
        query.setStatus(status);
        if (startDate != null)
        {
            query.getParams().put("beginTime", startDate.atStartOfDay());
        }
        if (endDate != null)
        {
            query.getParams().put("endTime", endDate.plusDays(1).atStartOfDay());
        }
        List<BorrowRecord> records = borrowRecordService.selectBorrowRecordList(query);
        int size = Math.max(1, Math.min(pageSize, 100));
        int from = Math.min((Math.max(1, pageNum) - 1) * size, records.size());
        int to = Math.min(from + size, records.size());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", records.subList(from, to).stream().map(this::borrowView).toList());
        data.put("total", records.size());
        return data;
    }

    @Override
    public Map<String, Object> detail(Long borrowId)
    {
        Long memberId = AppContext.requireMemberId();
        BorrowRecord record = requireOwnedBorrow(borrowId, memberId);
        LevelQuotaView quotas = borrowRuleService.levelQuota(record.getLevelId());
        Map<String, Object> renew = canRenew(record, memberId, quotas.getGlobalRule());
        List<Penalty> penalties = penaltyService.selectPenaltyList(penaltyQuery(memberId, borrowId));
        Map<String, Object> data = new LinkedHashMap<>(borrowView(record));
        data.put("canRenew", renew.get("canRenew"));
        data.put("renewBlockReason", renew.get("reason"));
        data.put("renewLogs", borrowRecordService.selectBorrowRenewList(
                new com.tbgbook.bookstore.borrow.domain.BorrowRenew() {{ setBorrowId(borrowId); }}));
        data.put("penalties", penalties);
        data.put("depositAmount", record.getDepositAmount());
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> renew(Long borrowId)
    {
        Long memberId = AppContext.requireMemberId();
        requireOwnedBorrow(borrowId, memberId);
        RenewRequest request = new RenewRequest();
        request.setRenewChannel(2);
        RenewResult result = borrowRecordService.renew(borrowId, request);
        return Map.of("beforeDueTime", result.getBeforeDueTime(),
                "afterDueTime", result.getAfterDueTime(),
                "renewDays", result.getRenewDays(),
                "renewCount", result.getRenewCount(),
                "remainRenewTimes", result.getRemainRenewTimes());
    }

    @Override
    public Map<String, Object> rule()
    {
        Long memberId = AppContext.requireMemberId();
        Member member = memberService.selectMemberById(memberId);
        LevelQuotaView view = borrowRuleService.levelQuota(null);
        List<Map<String, Object>> levels = new ArrayList<>();
        for (LevelQuotaView.LevelQuota level : view.getLevels())
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("levelId", level.getLevelId());
            item.put("levelName", level.getLevelName());
            item.put("discountRate", level.getDiscountRate());
            item.put("borrowQtyLimit", level.getBorrowQtyLimit());
            item.put("borrowDays", level.getBorrowDays());
            item.put("depositAmount", level.getDepositAmount());
            item.put("canRenew", level.getCanRenew());
            item.put("isCurrent", member.getLevelId().equals(level.getLevelId()));
            levels.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("levels", levels);
        data.put("globalRule", view.getGlobalRule());
        data.put("ruleTexts", List.of(
                "逾期罚金按天计算，单册封顶为定价 × 封顶比例。",
                "续借次数、借期与押金以会员等级和全局规则为准。"));
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reserve(Long bookId)
    {
        Long memberId = AppContext.requireMemberId();
        Book book = bookMapper.selectById(bookId);
        if (book == null || !"0".equals(book.getDelFlag()) || !Integer.valueOf(1).equals(book.getPublishStatus()))
        {
            throw BizErrors.ruleViolated("该图书已下架或停售");
        }
        Map<String, Object> stock = appTradeMapper.selectStockForLock(bookId);
        long available = stock == null ? 0L : Long.parseLong(String.valueOf(stock.get("qtyAvailable")));
        if (available > 0)
        {
            throw BizErrors.ruleViolated("该图书当前有可售库存，无需预约");
        }
        BorrowReserve reserve = new BorrowReserve();
        reserve.setStoreId(1L);
        reserve.setMemberId(memberId);
        reserve.setBookId(bookId);
        reserveService.addReserve(reserve);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reserveId", reserve.getReserveId());
        data.put("reserveNo", reserve.getReserveNo());
        data.put("queueNo", reserve.getQueueNo());
        data.put("status", reserve.getStatus());
        data.put("reserveTime", reserve.getReserveTime());
        data.put("estimatedAvailableTime", null);
        return data;
    }

    @Override
    public Map<String, Object> reserveList(Integer status)
    {
        Long memberId = AppContext.requireMemberId();
        List<BorrowReserve> list = reserveService.selectReserveList(reserveQuery(memberId, status));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return data;
    }

    @Override
    public Map<String, Object> cancelReserve(Long reserveId, String reason)
    {
        Long memberId = AppContext.requireMemberId();
        BorrowReserve reserve = reserveService.selectReserveById(reserveId);
        if (reserve == null)
        {
            throw BizErrors.notFound("借阅预约", reserveId);
        }
        if (!memberId.equals(reserve.getMemberId()))
        {
            throw BizErrors.forbidden("预约不属于当前会员");
        }
        reserveService.cancelReserve(reserveId, reason);
        return Map.of("reserveId", reserveId, "status", 4);
    }

    @Override
    public Map<String, Object> payPenalty(Long penaltyId, Integer payMethod, BigDecimal payAmount)
    {
        Long memberId = AppContext.requireMemberId();
        Penalty penalty = penaltyService.selectPenaltyById(penaltyId);
        if (penalty == null)
        {
            throw BizErrors.notFound("罚金单", penaltyId);
        }
        if (!memberId.equals(penalty.getMemberId()))
        {
            throw BizErrors.forbidden("罚金单不属于当前会员");
        }
        PenaltyPayRequest request = new PenaltyPayRequest();
        request.setPayMethod(payMethod);
        request.setPayAmount(payAmount);
        return Map.of("result", penaltyService.pay(penaltyId, request));
    }

    private BorrowRecord requireOwnedBorrow(Long borrowId, Long memberId)
    {
        BorrowRecord record = borrowRecordService.selectBorrowRecordById(borrowId);
        if (record == null)
        {
            throw BizErrors.notFound("借阅单", borrowId);
        }
        if (!memberId.equals(record.getMemberId()))
        {
            throw BizErrors.forbidden("借阅单不属于当前会员");
        }
        return record;
    }

    private Map<String, Object> canRenew(BorrowRecord record, Long memberId, BorrowRule rule)
    {
        String reason = null;
        if (!Integer.valueOf(1).equals(record.getStatus()))
        {
            reason = "当前状态不可续借";
        }
        else if (Integer.valueOf(1).equals(record.getIsOverdue())
                || (record.getDueTime() != null && LocalDateTime.now().isAfter(record.getDueTime())))
        {
            reason = "借阅单已逾期";
        }
        else if (rule != null && rule.getRenewTimes() != null
                && record.getRenewCount() != null && record.getRenewCount() >= rule.getRenewTimes())
        {
            reason = "已达最大续借次数";
        }
        else
        {
            List<BorrowReserve> reserves = reserveService.selectReserveList(
                    reserveQueryByBook(record.getBookId()));
            boolean others = reserves.stream().anyMatch(r -> !memberId.equals(r.getMemberId())
                    && (Integer.valueOf(1).equals(r.getStatus()) || Integer.valueOf(2).equals(r.getStatus())));
            if (others)
            {
                reason = "该图书有他人预约";
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canRenew", reason == null);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> borrowView(BorrowRecord record)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("borrowId", record.getBorrowId());
        item.put("borrowNo", record.getBorrowNo());
        item.put("isbn", record.getIsbn());
        item.put("title", record.getTitle());
        item.put("barCode", record.getBarCode());
        item.put("borrowTime", record.getBorrowTime());
        item.put("dueTime", record.getDueTime());
        item.put("returnTime", record.getReturnTime());
        item.put("status", record.getStatus());
        item.put("renewCount", record.getRenewCount());
        item.put("overdueDays", record.getOverdueDays());
        item.put("fineAmount", record.getFineAmount());
        item.put("daysLeft", record.getDueTime() == null ? null
                : ChronoUnit.DAYS.between(LocalDateTime.now(), record.getDueTime()));
        item.put("isOverdue", record.getIsOverdue());
        return item;
    }

    private BigDecimal unpaidPenalty(Long memberId)
    {
        List<Penalty> penalties = penaltyService.selectPenaltyList(penaltyQuery(memberId, null));
        return penalties.stream()
                .filter(p -> Integer.valueOf(1).equals(p.getPayStatus()) || Integer.valueOf(3).equals(p.getPayStatus()))
                .map(p -> p.getAmount().subtract(p.getDeductDeposit() == null
                        ? BigDecimal.ZERO : p.getDeductDeposit()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BorrowRecord borrowQuery(Long memberId, Integer status)
    {
        BorrowRecord query = new BorrowRecord();
        query.setMemberId(memberId);
        query.setStatus(status);
        return query;
    }

    private BorrowReserve reserveQuery(Long memberId, Integer status)
    {
        BorrowReserve query = new BorrowReserve();
        query.setMemberId(memberId);
        query.setStatus(status);
        return query;
    }

    private BorrowReserve reserveQueryByBook(Long bookId)
    {
        BorrowReserve query = new BorrowReserve();
        query.setBookId(bookId);
        return query;
    }

    private Penalty penaltyQuery(Long memberId, Long borrowId)
    {
        Penalty query = new Penalty();
        query.setMemberId(memberId);
        query.setBorrowId(borrowId);
        return query;
    }
}
