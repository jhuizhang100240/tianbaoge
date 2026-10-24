package com.tbgbook.bookstore.job.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.tbgbook.bookstore.analysis.domain.ReportPushLog;
import com.tbgbook.bookstore.analysis.domain.ReportSubscribe;
import com.tbgbook.bookstore.analysis.mapper.ReportPushLogMapper;
import com.tbgbook.bookstore.analysis.mapper.ReportSubscribeMapper;
import com.tbgbook.bookstore.analysis.service.IMetricDailyService;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.index.IndexScanTask;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.OverdueNotifyRequest;
import com.tbgbook.bookstore.borrow.domain.OverdueNotifyResult;
import com.tbgbook.bookstore.borrow.mapper.BorrowRecordMapper;
import com.tbgbook.bookstore.borrow.service.IBorrowRecordService;
import com.tbgbook.bookstore.job.domain.JobRunResult;
import com.tbgbook.bookstore.job.mapper.FlowArchiveMapper;
import com.tbgbook.bookstore.member.domain.MemberMessage;
import com.tbgbook.bookstore.member.domain.MemberTag;
import com.tbgbook.bookstore.member.domain.PointBatch;
import com.tbgbook.bookstore.member.domain.PointLog;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.MemberMessageMapper;
import com.tbgbook.bookstore.member.mapper.MemberTagMapper;
import com.tbgbook.bookstore.member.mapper.PointBatchMapper;
import com.tbgbook.bookstore.member.mapper.PointLogMapper;
import com.tbgbook.bookstore.member.service.IMemberTagService;
import com.tbgbook.system.domain.SysNotice;
import com.tbgbook.system.service.ISysConfigService;
import com.tbgbook.system.service.ISysNoticeService;
import com.tbgbook.bookstore.job.service.IScheduledJobService;
import com.tbgbook.bookstore.stock.domain.StockSnapshot;
import com.tbgbook.bookstore.stock.domain.StockSnapshotItem;
import com.tbgbook.bookstore.stock.domain.WarningScanResult;
import com.tbgbook.bookstore.stock.mapper.StockSnapshotItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockSnapshotMapper;
import com.tbgbook.bookstore.stock.service.IStockWarningService;

import lombok.RequiredArgsConstructor;

/**
 * 业务定时任务入口实现。
 */
@Service
@RequiredArgsConstructor
public class ScheduledJobServiceImpl implements IScheduledJobService
{
    private final StockSnapshotMapper stockSnapshotMapper;

    private final StockSnapshotItemMapper stockSnapshotItemMapper;

    private final IStockWarningService stockWarningService;

    private final IMetricDailyService metricDailyService;

    private final ReportSubscribeMapper reportSubscribeMapper;

    private final ReportPushLogMapper reportPushLogMapper;

    private final PointBatchMapper pointBatchMapper;

    private final PointLogMapper pointLogMapper;

    private final MemberMessageMapper memberMessageMapper;

    private final MemberTagMapper memberTagMapper;

    private final IMemberTagService memberTagService;

    private final MemberMapper memberMapper;

    private final ISysConfigService configService;

    private final TransactionTemplate transactionTemplate;

    private final BorrowRecordMapper borrowRecordMapper;

    private final IBorrowRecordService borrowRecordService;

    private final IndexScanTask indexScanTask;

    private final ISysNoticeService noticeService;

    private final FlowArchiveMapper flowArchiveMapper;

    private static final long STORE_ID = 1L;

    private static final int MAX_ARCHIVE_ROUNDS = 200;

    private static final int CHANGE_EXPIRE = 3;

    private static final int MSG_BORROW_DUE = 2;

    private static final int MSG_BORROW_OVERDUE = 3;

    private static final int MSG_POINTS_EXPIRE = 4;


    private static final int CHANNEL_INBOX = 2;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public JobRunResult run(String jobCode)
    {
        if (jobCode == null)
        {
            return JobRunResult.failed(jobCode, "jobCode 不能为空", LocalDateTime.now());
        }
        return switch (jobCode)
        {
            case "stockSnapshotTask.generate" ->
                transactionTemplate.execute(status -> generateStockSnapshot(LocalDate.now()));
            case "stockWarningTask.scan" -> scanStockWarnings();
            case "dashboardPrecomputeTask.run" -> precomputeDashboard();
            case "reportSubscribeTask.push" -> pushReportSubscriptions();
            case "pointsExpireTask.expireNotify" -> expirePoints();
            case "memberTagTask.recalc" -> recalcRuleTags();
            case "borrowDueTask.dueNotify" -> notifyBorrowDue();
            case "kbIndexTimeoutTask.check" -> checkKbIndexTimeout();
            case "flowArchiveTask.archive" -> archiveFlows();
            default -> JobRunResult.failed(jobCode, "not implemented", LocalDateTime.now());
        };
    }

    /**
     * 生成库存快照。
     *
     * <p><b>不要在本方法上加 {@code @Transactional}</b>：它由本类 run() 直接调用，
     * 自调用不会经过 Spring 代理。事务由 run() 里的 TransactionTemplate 显式开启。
     */
    public JobRunResult generateStockSnapshot(LocalDate date)
    {
        LocalDateTime startedAt = LocalDateTime.now();
        List<Map<String, Object>> rows = stockSnapshotItemMapper.selectSnapshotSourceRows();
        Set<Long> bookIds = new HashSet<>();
        int totalQty = 0;
        int available = 0;
        int locked = 0;
        int borrowed = 0;
        int damaged = 0;
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);

        StockSnapshot snapshot = new StockSnapshot();
        snapshot.setStoreId(1L);
        snapshot.setSnapshotDate(date);
        snapshot.setBookKind(0);
        snapshot.setTotalQty(0);
        snapshot.setQtyAvailable(0);
        snapshot.setQtyLocked(0);
        snapshot.setQtyBorrowed(0);
        snapshot.setQtyDamaged(0);
        snapshot.setTotalAmount(BigDecimal.ZERO.setScale(2));
        snapshot.setCreateTime(startedAt);
        stockSnapshotMapper.upsertSnapshot(snapshot);
        Long snapshotId = stockSnapshotMapper.selectIdByStoreAndDate(snapshot.getStoreId(), date);
        if (snapshotId == null)
        {
            throw new IllegalStateException("库存快照主表写入后未能取回 snapshotId，date=" + date);
        }
        snapshot.setSnapshotId(snapshotId);
        stockSnapshotMapper.deleteItemsBySnapshotId(snapshotId);

        for (Map<String, Object> row : rows)
        {
            int qa = intValue(row.get("qtyAvailable"));
            int ql = intValue(row.get("qtyLocked"));
            int qb = intValue(row.get("qtyBorrowed"));
            int qd = intValue(row.get("qtyDamaged"));
            BigDecimal cost = decimal(row.get("costPrice"));
            int physical = qa + ql + qb + qd;
            BigDecimal amount = cost.multiply(BigDecimal.valueOf(physical))
                    .setScale(2, RoundingMode.HALF_UP);

            StockSnapshotItem item = new StockSnapshotItem();
            item.setSnapshotId(snapshot.getSnapshotId());
            item.setBookId(longValue(row.get("bookId")));
            item.setIsbn(string(row.get("isbn")));
            item.setLocationId(longValue(row.get("locationId")));
            item.setQtyAvailable(qa);
            item.setQtyLocked(ql);
            item.setQtyBorrowed(qb);
            item.setQtyDamaged(qd);
            item.setCostPrice(cost);
            item.setAmount(amount);
            stockSnapshotItemMapper.insert(item);

            bookIds.add(item.getBookId());
            totalQty += physical;
            available += qa;
            locked += ql;
            borrowed += qb;
            damaged += qd;
            totalAmount = totalAmount.add(amount);
        }

        StockSnapshot patch = new StockSnapshot();
        patch.setSnapshotId(snapshot.getSnapshotId());
        patch.setBookKind(bookIds.size());
        patch.setTotalQty(totalQty);
        patch.setQtyAvailable(available);
        patch.setQtyLocked(locked);
        patch.setQtyBorrowed(borrowed);
        patch.setQtyDamaged(damaged);
        patch.setTotalAmount(totalAmount);
        patch.setCreateTime(startedAt);
        stockSnapshotMapper.updateById(patch);
        return JobRunResult.ok("stockSnapshotTask.generate", bookIds.size(),
                "snapshot " + date + " generated", startedAt);
    }

    public JobRunResult scanStockWarnings()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        WarningScanResult result = stockWarningService.scan(null);
        return JobRunResult.ok("stockWarningTask.scan",
                (result.getCreated() == null ? 0 : result.getCreated())
                        + (result.getUpdated() == null ? 0 : result.getUpdated()),
                "scanned=" + result.getScanned() + ", created=" + result.getCreated()
                        + ", updated=" + result.getUpdated(), startedAt);
    }

    public JobRunResult precomputeDashboard()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        metricDailyService.ensureRange(today.minusDays(1), today);
        return JobRunResult.ok("dashboardPrecomputeTask.run", 2,
                "precomputed " + today.minusDays(1) + ".." + today, startedAt);
    }

    public JobRunResult pushReportSubscriptions()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDate today = startedAt.toLocalDate();
        int pushed = 0;
        int notDue = 0;
        int invalid = 0;
        List<ReportSubscribe> subscriptions = reportSubscribeMapper.selectList(
                new LambdaQueryWrapper<ReportSubscribe>()
                        .eq(ReportSubscribe::getStoreId, STORE_ID)
                        .eq(ReportSubscribe::getStatus, 1));
        for (ReportSubscribe subscribe : subscriptions)
        {
            DueTime due = resolveDueTime(subscribe.getPushTime(), today);
            if (!due.valid())
            {
                invalid++;
                continue;
            }
            if (due.at() == null || startedAt.isBefore(due.at()))
            {
                notDue++;
                continue;
            }
            long existing = reportPushLogMapper.selectCount(
                    new LambdaQueryWrapper<ReportPushLog>()
                            .eq(ReportPushLog::getSubscribeId, subscribe.getSubscribeId())
                            .eq(ReportPushLog::getIsTest, 0)
                            .ge(ReportPushLog::getPushTime, today.atStartOfDay())
                            .lt(ReportPushLog::getPushTime, today.plusDays(1).atStartOfDay()));
            if (existing > 0)
            {
                continue;
            }
            int channel = subscribe.getPushChannel() == null ? 1 : subscribe.getPushChannel();
            boolean includeMessage = channel == 1 || channel == 3;
            boolean includeEmail = channel == 2 || channel == 3;
            if (includeMessage)
            {
                // 报表订阅要求「推送至指定角色」：biz_todo 没有角色字段，
                // 用 sys_notice 的 target_role_ids 承载角色定向。
                String noticeTitle = "报表推送：" + subscribe.getSubscribeName();
                if (noticeTitle.length() > 50)
                {
                    noticeTitle = noticeTitle.substring(0, 50);
                }
                SysNotice notice = new SysNotice();
                notice.setNoticeTitle(noticeTitle);
                notice.setNoticeType("1");
                notice.setNoticeContent("报表订阅「" + subscribe.getSubscribeName()
                        + "」已生成，请到数据分析-报表订阅查看。");
                notice.setTargetRoleIds(subscribe.getTargetRoles() == null
                        ? "" : subscribe.getTargetRoles());
                notice.setEffectiveStart(startedAt);
                notice.setEffectiveEnd(today.plusDays(1).atStartOfDay());
                notice.setStatus("0");
                notice.setCreateBy("system");
                notice.setRemark("AUTO:REPORT_SUB:" + subscribe.getSubscribeId() + ":" + today);
                noticeService.insertNotice(notice);
            }
            ReportPushLog log = new ReportPushLog();
            log.setSubscribeId(subscribe.getSubscribeId());
            log.setPushTime(startedAt);
            log.setPushChannel(channel);
            log.setTargetCount(includeMessage ? 1 : 0);
            log.setIsTest(0);
            log.setStatus(includeMessage ? 1 : 2);
            log.setFailReason(includeEmail ? "邮件通道未配置（阶段十启用）" : "");
            log.setCreateTime(startedAt);
            reportPushLogMapper.insert(log);
            pushed++;
        }
        return JobRunResult.ok("reportSubscribeTask.push", pushed,
                "pushed=" + pushed + ", notDue=" + notDue + ", invalidPushTime=" + invalid,
                startedAt);
    }

    /**
     * 积分到期清零 + 到期前提醒。
     *
     * <p>到期清零按批次逐条处理，每条批次独立事务：单条脏数据只计失败数，不影响其余会员。
     * 清零用条件更新（{@code status=1 and remain_points>0 and expire_time<=now}）保证重复触发
     * 或并发触发时只生效一次。提醒以「会员 + 到期日」去重，避免每日重复推送同一条。
     */
    public JobRunResult expirePoints()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        List<PointBatch> dueBatches = pointBatchMapper.selectList(
                new LambdaQueryWrapper<PointBatch>()
                        .eq(PointBatch::getStatus, 1)
                        .gt(PointBatch::getRemainPoints, 0)
                        .le(PointBatch::getExpireTime, startedAt)
                        .orderByAsc(PointBatch::getBatchId));
        int cleared = 0;
        List<String> failures = new ArrayList<>();
        for (PointBatch batch : dueBatches)
        {
            try
            {
                if (expireSingleBatch(batch.getBatchId(), startedAt))
                {
                    cleared++;
                }
            }
            catch (RuntimeException e)
            {
                failures.add("batch=" + batch.getBatchId() + ":" + shortError(e));
            }
        }

        int notifyDays = configInt("biz.points.expire.notify.days", 30);
        LocalDateTime cutoff = startedAt.plusDays(Math.max(notifyDays, 0));
        List<PointBatch> expiring = pointBatchMapper.selectList(
                new LambdaQueryWrapper<PointBatch>()
                        .eq(PointBatch::getStatus, 1)
                        .gt(PointBatch::getRemainPoints, 0)
                        .gt(PointBatch::getExpireTime, startedAt)
                        .le(PointBatch::getExpireTime, cutoff)
                        .orderByAsc(PointBatch::getExpireTime)
                        .orderByAsc(PointBatch::getBatchId));
        Map<String, List<PointBatch>> byMemberDay = new LinkedHashMap<>();
        for (PointBatch batch : expiring)
        {
            String day = batch.getExpireTime().toLocalDate().format(DATE);
            byMemberDay.computeIfAbsent(batch.getMemberId() + "@" + day, k -> new java.util.ArrayList<>())
                    .add(batch);
        }
        int notified = 0;
        for (Map.Entry<String, List<PointBatch>> entry : byMemberDay.entrySet())
        {
            String[] key = entry.getKey().split("@");
            Long memberId = Long.valueOf(key[0]);
            String day = key[1];
            int points = 0;
            for (PointBatch batch : entry.getValue())
            {
                points += nz(batch.getRemainPoints());
            }
            try
            {
                Long existing = memberMessageMapper.selectCount(
                        new LambdaQueryWrapper<MemberMessage>()
                                .eq(MemberMessage::getMemberId, memberId)
                                .eq(MemberMessage::getMsgType, MSG_POINTS_EXPIRE)
                                .eq(MemberMessage::getBizNo, "POINT:" + memberId + ":" + day));
                if (existing > 0)
                {
                    continue;
                }
                MemberMessage message = new MemberMessage();
                message.setStoreId(STORE_ID);
                message.setMemberId(memberId);
                message.setMsgType(MSG_POINTS_EXPIRE);
                message.setTitle("积分即将到期");
                message.setContent("您有 " + points + " 积分将于 " + day + " 到期，请及时使用。");
                message.setLinkUrl("pages/member/point/logs");
                message.setBizNo("POINT:" + memberId + ":" + day);
                message.setSendChannel(CHANNEL_INBOX);
                message.setSendStatus(2);
                message.setFailReason("");
                message.setSendTime(startedAt);
                message.setIsRead(0);
                message.setCreateTime(startedAt);
                memberMessageMapper.insert(message);
                notified++;
            }
            catch (RuntimeException e)
            {
                failures.add("member=" + memberId + ":" + shortError(e));
            }
        }
        if (!failures.isEmpty())
        {
            return JobRunResult.failed("pointsExpireTask.expireNotify",
                    "expiredBatches=" + cleared + ", notified=" + notified
                            + ", failed=" + failures.size() + ", details=" + failureSummary(failures),
                    startedAt);
        }
        return JobRunResult.ok("pointsExpireTask.expireNotify", cleared + notified,
                "expiredBatches=" + cleared + ", notified=" + notified + ", failed=0",
                startedAt);
    }

    /** 单批次清零：独立事务，单条失败不影响其余批次 */
    private boolean expireSingleBatch(Long batchId, LocalDateTime now)
    {
        // 显式 REQUIRES_NEW：即使未来在事务中调用，单批次失败也只回滚该批次
        TransactionTemplate tx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Boolean done = tx.execute(status -> expireSingleBatchInTx(batchId, now));
        return Boolean.TRUE.equals(done);
    }

    private boolean expireSingleBatchInTx(Long batchId, LocalDateTime now)
    {
        PointBatch batch = pointBatchMapper.selectById(batchId);
        if (batch == null || batch.getExpireTime() == null || batch.getExpireTime().isAfter(now))
        {
            return false;
        }
        int points = nz(batch.getRemainPoints());
        if (points <= 0)
        {
            return false;
        }
        // 先锁会员行，再锁/改批次：与消费扣减的加锁顺序一致，避免死锁；
        // 同时保证 after 是当前已提交余额，而不是 REPEATABLE READ 旧快照。
        int after = nz(memberMapper.selectPointsForUpdate(batch.getMemberId()));
        if (after < points)
        {
            throw new IllegalStateException("会员积分总账小于到期批次剩余积分，memberId="
                    + batch.getMemberId() + ", batchId=" + batchId
                    + ", memberPoints=" + after + ", batchPoints=" + points);
        }
        if (pointBatchMapper.expireBatch(batchId, now) == 0)
        {
            return false;
        }
        if (memberMapper.deductPoints(batch.getMemberId(), points) == 0)
        {
            throw new IllegalStateException("积分到期扣减失败，memberId=" + batch.getMemberId()
                    + ", batchId=" + batchId + ", points=" + points);
        }
        PointLog log = new PointLog();
        log.setMemberId(batch.getMemberId());
        log.setChangeType(CHANGE_EXPIRE);
        log.setPointsChange(-points);
        log.setPointsAfter(after - points);
        log.setBatchId(batchId);
        log.setRefNo(batch.getRefNo() == null ? "" : batch.getRefNo());
        log.setCreateBy("system");
        log.setCreateTime(now);
        log.setRemark("积分到期清零：" + points + " 分");
        pointLogMapper.insert(log);
        return true;
    }

    /**
     * 借阅到期提醒 + 逾期催还。
     *
     * <p>到期提醒窗口：应还时间在「现在 ~ 现在 + biz.borrow.due.notify.days 天」，
     * 到期当日无论具体时刻都提醒一次；逾期催还按 biz.borrow.overdue.notify.days 间隔，
     * 依据已有消息时间判断是否到下一次。同一借阅单同一到期日只产生一条到期提醒。
     */
    public JobRunResult notifyBorrowDue()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDate today = startedAt.toLocalDate();
        int dueDays = Math.max(0, configInt("biz.borrow.due.notify.days", 3));
        int overdueDays = Math.max(1, configInt("biz.borrow.overdue.notify.days", 3));
        LocalDateTime dueCutoff = startedAt.plusDays(dueDays);

        List<BorrowRecord> active = borrowRecordMapper.selectList(
                new LambdaQueryWrapper<BorrowRecord>()
                        .in(BorrowRecord::getStatus, BorrowRecord.STATUS_BORROWING,
                                BorrowRecord.STATUS_OVERDUE)
                        .isNull(BorrowRecord::getReturnTime)
                        .isNotNull(BorrowRecord::getDueTime)
                        .le(BorrowRecord::getDueTime, dueCutoff)
                        .orderByAsc(BorrowRecord::getDueTime)
                        .orderByAsc(BorrowRecord::getBorrowId));

        int dueNotified = 0;
        int skipped = 0;
        List<Long> overdueIds = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (BorrowRecord record : active)
        {
            LocalDateTime due = record.getDueTime();
            boolean dueToday = due.toLocalDate().equals(today);
            if (due.isAfter(startedAt) || dueToday)
            {
                String dueDay = due.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
                String borrowPrefix = record.getBorrowNo() == null ? "" : record.getBorrowNo();
                int maxPrefix = 32 - 1 - 8 - 1;
                if (borrowPrefix.length() > maxPrefix)
                {
                    borrowPrefix = borrowPrefix.substring(0, maxPrefix);
                }
                // T=到期当日提醒，A=提前提醒；两者各一次，满足「到期前 N 天 + 到期当日」
                String key = borrowPrefix + "#" + dueDay + (dueToday ? "T" : "A");
                try
                {
                    Long existing = memberMessageMapper.selectCount(
                            new LambdaQueryWrapper<MemberMessage>()
                                    .eq(MemberMessage::getMemberId, record.getMemberId())
                                    .eq(MemberMessage::getMsgType, MSG_BORROW_DUE)
                                    .eq(MemberMessage::getBizNo, key));
                    if (existing > 0)
                    {
                        skipped++;
                        continue;
                    }
                    MemberMessage message = new MemberMessage();
                    message.setStoreId(STORE_ID);
                    message.setMemberId(record.getMemberId());
                    message.setMsgType(MSG_BORROW_DUE);
                    message.setTitle("图书即将到期");
                    message.setContent("《" + record.getTitle() + "》应还日期为 "
                            + due.toLocalDate() + "，请按时归还以免产生逾期罚金。");
                    message.setLinkUrl("pages/borrow/detail?borrowId=" + record.getBorrowId());
                    message.setBizNo(key);
                    message.setSendChannel(CHANNEL_INBOX);
                    message.setSendStatus(2);
                    message.setFailReason("");
                    message.setSendTime(startedAt);
                    message.setIsRead(0);
                    message.setReadTime(null);
                    message.setCreateTime(startedAt);
                    memberMessageMapper.insert(message);
                    dueNotified++;
                }
                catch (RuntimeException e)
                {
                    failures.add("borrow=" + record.getBorrowId() + ":" + shortError(e));
                }
                continue;
            }
            LocalDateTime threshold = startedAt.minusDays(overdueDays);
            Long recent = memberMessageMapper.selectCount(
                    new LambdaQueryWrapper<MemberMessage>()
                            .eq(MemberMessage::getMemberId, record.getMemberId())
                            .eq(MemberMessage::getMsgType, MSG_BORROW_OVERDUE)
                            .eq(MemberMessage::getBizNo, record.getBorrowNo())
                            .ge(MemberMessage::getCreateTime, threshold));
            if (recent > 0)
            {
                skipped++;
                continue;
            }
            overdueIds.add(record.getBorrowId());
        }

        int overdueSent = 0;
        int overdueFailed = 0;
        if (!overdueIds.isEmpty())
        {
            OverdueNotifyRequest request = new OverdueNotifyRequest();
            request.setBorrowIds(overdueIds);
            OverdueNotifyResult result = borrowRecordService.notifyOverdue(request);
            overdueSent = result.getSent() == null ? 0 : result.getSent();
            overdueFailed = result.getFailed() == null ? 0 : result.getFailed();
            if (result.getFailDetails() != null)
            {
                for (OverdueNotifyResult.FailDetail detail : result.getFailDetails())
                {
                    failures.add("borrow=" + detail.getBorrowId() + ":" + detail.getReason());
                }
            }
        }
        if (!failures.isEmpty() || overdueFailed > 0)
        {
            return JobRunResult.failed("borrowDueTask.dueNotify",
                    "dueReminders=" + dueNotified + ", overdueReminders=" + overdueSent
                            + ", skipped=" + skipped + ", failed=" + failures.size()
                            + ", details=" + failureSummary(failures),
                    startedAt);
        }
        return JobRunResult.ok("borrowDueTask.dueNotify", dueNotified + overdueSent,
                "dueReminders=" + dueNotified + ", overdueReminders=" + overdueSent
                        + ", skipped=" + skipped + ", failed=0",
                startedAt);
    }

    /**
     * 知识库索引超时检测：{@code index_status=2} 且 {@code update_time} 超过
     * {@code biz.kb.index.timeout.minutes} 的文档，为每篇文档生成一条定向系统通知。
     * 用固定标题去重，重复触发不重复告警。
     */
    public JobRunResult checkKbIndexTimeout()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        List<KbDoc> stuck = indexScanTask.findTimeoutDocs();
        int timeoutMinutes = configInt("biz.kb.index.timeout.minutes", 5);
        int created = 0;
        List<String> failures = new ArrayList<>();
        for (KbDoc doc : stuck)
        {
            String title = "知识库索引超时 #" + doc.getDocId();
            try
            {
                SysNotice query = new SysNotice();
                query.setNoticeTitle(title);
                boolean exists = noticeService.selectNoticeList(query).stream()
                        .anyMatch(n -> title.equals(n.getNoticeTitle()));
                if (exists)
                {
                    continue;
                }
                SysNotice notice = new SysNotice();
                notice.setNoticeTitle(title);
                notice.setNoticeType("1");
                notice.setNoticeContent("文档「" + doc.getDocName() + "」索引处理超过 "
                        + timeoutMinutes + " 分钟仍未完成，请检查 AI 服务与索引任务。");
                // 只推给店长与知识库运营员，避免全员可见
                notice.setTargetRoleIds("100,103");
                notice.setStatus("0");
                notice.setCreateBy("system");
                notice.setRemark("AUTO:KB_TIMEOUT:" + doc.getDocId());
                noticeService.insertNotice(notice);
                created++;
            }
            catch (RuntimeException e)
            {
                failures.add("doc=" + doc.getDocId() + ":" + shortError(e));
            }
        }
        if (!failures.isEmpty())
        {
            return JobRunResult.failed("kbIndexTimeoutTask.check",
                    "stuck=" + stuck.size() + ", created=" + created
                            + ", failed=" + failures.size()
                            + ", details=" + failureSummary(failures),
                    startedAt);
        }
        return JobRunResult.ok("kbIndexTimeoutTask.check", created,
                "stuck=" + stuck.size() + ", created=" + created + ", failed=0",
                startedAt);
    }

    /**
     * 流水归档：库存流水 / 积分流水 / AI 调用明细。
     *
     * <p>每张表循环「复制一批 → 校验复制行数 == 删除行数 → 删源表」；任何一步不相等立即停止该表，
     * 已归档的数据保留、未归档的源数据不动。保留天数与批量大小读 {@code sys_config}，
     * 不在此硬编码。整个任务串行执行且 `sys_job.concurrent=1`，不与自身并发。
     */
    public JobRunResult archiveFlows()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        int retainDays = Math.max(1, configInt("biz.flow.archive.retain.days", 180));
        int batchSize = Math.min(5000, Math.max(1, configInt("biz.flow.archive.batch.size", 500)));
        LocalDateTime cutoff = startedAt.minusDays(retainDays);

        int stockArchived = 0;
        int pointArchived = 0;
        int aiArchived = 0;
        boolean stockCapped = false;
        boolean pointCapped = false;
        boolean aiCapped = false;
        boolean archiveMismatch = false;
        StringBuilder message = new StringBuilder("cutoff=").append(cutoff);
        try
        {
            for (int i = 0; i < MAX_ARCHIVE_ROUNDS; i++)
            {
                int copied = flowArchiveMapper.copyStockFlows(cutoff, batchSize, startedAt);
                int deleted = flowArchiveMapper.deleteArchivedStockFlows(cutoff, batchSize);
                if (copied == 0 && deleted == 0)
                {
                    break;
                }
                if (copied > 0 && deleted < copied)
                {
                    message.append("; stockMismatch=").append(copied).append("/").append(deleted);
                    archiveMismatch = true;
                    break;
                }
                stockArchived += copied;
                if (deleted > copied)
                {
                    message.append("; stockRecovered=").append(deleted - copied);
                }
                stockCapped = i == MAX_ARCHIVE_ROUNDS - 1;
            }
            for (int i = 0; i < MAX_ARCHIVE_ROUNDS; i++)
            {
                int copied = flowArchiveMapper.copyPointLogs(cutoff, batchSize, startedAt);
                int deleted = flowArchiveMapper.deleteArchivedPointLogs(cutoff, batchSize);
                if (copied == 0 && deleted == 0)
                {
                    break;
                }
                if (copied > 0 && deleted < copied)
                {
                    message.append("; pointMismatch=").append(copied).append("/").append(deleted);
                    archiveMismatch = true;
                    break;
                }
                pointArchived += copied;
                if (deleted > copied)
                {
                    message.append("; pointRecovered=").append(deleted - copied);
                }
                pointCapped = i == MAX_ARCHIVE_ROUNDS - 1;
            }
            for (int i = 0; i < MAX_ARCHIVE_ROUNDS; i++)
            {
                int copied = flowArchiveMapper.copyAiCallLogs(cutoff, batchSize, startedAt);
                int deleted = flowArchiveMapper.deleteArchivedAiCallLogs(cutoff, batchSize);
                if (copied == 0 && deleted == 0)
                {
                    break;
                }
                if (copied > 0 && deleted < copied)
                {
                    message.append("; aiMismatch=").append(copied).append("/").append(deleted);
                    archiveMismatch = true;
                    break;
                }
                aiArchived += copied;
                if (deleted > copied)
                {
                    message.append("; aiRecovered=").append(deleted - copied);
                }
                aiCapped = i == MAX_ARCHIVE_ROUNDS - 1;
            }
        }
        catch (RuntimeException e)
        {
            return JobRunResult.failed("flowArchiveTask.archive",
                    "归档失败：" + e.getMessage() + "（已归档 stock=" + stockArchived
                            + ", point=" + pointArchived + ", ai=" + aiArchived + "）",
                    startedAt);
        }
        if (archiveMismatch)
        {
            return JobRunResult.failed("flowArchiveTask.archive",
                    "归档行数校验失败：" + message, startedAt);
        }
        return JobRunResult.ok("flowArchiveTask.archive",
                stockArchived + pointArchived + aiArchived,
                message + "; stock=" + stockArchived + ", point=" + pointArchived
                        + ", ai=" + aiArchived + ", retainDays=" + retainDays
                        + ", batch=" + batchSize
                        + (stockCapped || pointCapped || aiCapped
                                ? ", capped=true（单次上限 " + MAX_ARCHIVE_ROUNDS + " 批，次日继续）" : ""),
                startedAt);
    }

    /**
     * 规则标签全量重算。单个标签失败只记录，不中断其余标签。
     */
    public JobRunResult recalcRuleTags()
    {
        LocalDateTime startedAt = LocalDateTime.now();
        List<MemberTag> tags = memberTagMapper.selectList(
                new LambdaQueryWrapper<MemberTag>()
                        .eq(MemberTag::getStoreId, STORE_ID)
                        .eq(MemberTag::getIsRule, 1)
                        .eq(MemberTag::getStatus, "0"));
        int recalculated = 0;
        int members = 0;
        List<String> failures = new ArrayList<>();
        for (MemberTag tag : tags)
        {
            try
            {
                var result = memberTagService.recalc(tag.getTagId());
                recalculated++;
                members += result.getMemberCount() == null ? 0 : result.getMemberCount();
            }
            catch (RuntimeException e)
            {
                failures.add("tag=" + tag.getTagId() + ":" + shortError(e));
            }
        }
        if (!failures.isEmpty())
        {
            return JobRunResult.failed("memberTagTask.recalc",
                    "tags=" + tags.size() + ", recalculated=" + recalculated
                            + ", members=" + members + ", failed=" + failures.size()
                            + ", details=" + failureSummary(failures),
                    startedAt);
        }
        return JobRunResult.ok("memberTagTask.recalc", recalculated,
                "tags=" + tags.size() + ", recalculated=" + recalculated
                        + ", members=" + members + ", failed=0",
                startedAt);
    }

    private String shortError(RuntimeException e)
    {
        String message = e.getMessage();
        if (message == null || message.isBlank())
        {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    private String failureSummary(List<String> failures)
    {
        if (failures.isEmpty())
        {
            return "";
        }
        int limit = Math.min(5, failures.size());
        String summary = String.join("; ", failures.subList(0, limit));
        return failures.size() > limit ? summary + "; ..." : summary;
    }

    private int configInt(String key, int fallback)
    {
        try
        {
            String raw = configService.selectConfigByKey(key);
            if (raw != null && !raw.isBlank())
            {
                return Integer.parseInt(raw.trim());
            }
        }
        catch (RuntimeException ignored)
        {
            // 配置损坏不应阻断定时任务，落默认值
        }
        return fallback;
    }

    /**
     * 解析订阅的推送时间，换算成「当天应推时刻」。
     *
     * <p>支持 `每日HH:mm` 与 `每周N HH:mm`（N 为 1-7，周一为 1）。返回 `valid=false`
     * 表示配置非法（跳过并计数）；`valid=true` 且 `at=null` 表示当天不是该订阅的推送日。</p>
     */
    private DueTime resolveDueTime(String pushTime, LocalDate today)
    {
        if (pushTime == null)
        {
            return new DueTime(false, null);
        }
        String value = pushTime.trim();
        boolean weekly = value.startsWith("每周");
        if (!weekly && !value.startsWith("每日"))
        {
            return new DueTime(false, null);
        }
        String rest = value.substring(2).trim();
        int dayOfWeek = 0;
        if (weekly)
        {
            int space = rest.indexOf(' ');
            if (space <= 0)
            {
                return new DueTime(false, null);
            }
            try
            {
                dayOfWeek = Integer.parseInt(rest.substring(0, space).trim());
            }
            catch (NumberFormatException e)
            {
                return new DueTime(false, null);
            }
            if (dayOfWeek < 1 || dayOfWeek > 7)
            {
                return new DueTime(false, null);
            }
            rest = rest.substring(space + 1).trim();
        }
        LocalTime time = parseHourMinute(rest);
        if (time == null)
        {
            return new DueTime(false, null);
        }
        if (weekly && today.getDayOfWeek().getValue() != dayOfWeek)
        {
            return new DueTime(true, null);
        }
        return new DueTime(true, LocalDateTime.of(today, time));
    }

    private LocalTime parseHourMinute(String text)
    {
        if (text.length() != 5 || text.charAt(2) != ':')
        {
            return null;
        }
        try
        {
            int hour = Integer.parseInt(text.substring(0, 2));
            int minute = Integer.parseInt(text.substring(3, 5));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            {
                return null;
            }
            return LocalTime.of(hour, minute);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** 当天应推时刻的解析结果。 */
    private record DueTime(boolean valid, LocalDateTime at)
    {
    }

    private int nz(Integer value)
    {
        return value == null ? 0 : value;
    }

    private int intValue(Object value)
    {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private long longValue(Object value)
    {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal decimal(Object value)
    {
        return value instanceof BigDecimal number ? number
                : value instanceof Number number ? BigDecimal.valueOf(number.doubleValue())
                : BigDecimal.ZERO;
    }

    private String string(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }
}
