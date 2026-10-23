package com.tbgbook.bookstore.job.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tbgbook.bookstore.analysis.domain.BizTodo;
import com.tbgbook.bookstore.analysis.domain.ReportPushLog;
import com.tbgbook.bookstore.analysis.domain.ReportSubscribe;
import com.tbgbook.bookstore.analysis.mapper.ReportPushLogMapper;
import com.tbgbook.bookstore.analysis.mapper.ReportSubscribeMapper;
import com.tbgbook.bookstore.analysis.service.IMetricDailyService;
import com.tbgbook.bookstore.analysis.service.ITodoService;
import com.tbgbook.bookstore.job.domain.JobRunResult;
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

    private final ITodoService todoService;

    private static final long STORE_ID = 1L;

    @Override
    public JobRunResult run(String jobCode)
    {
        if (jobCode == null)
        {
            return JobRunResult.failed(jobCode, "jobCode 不能为空", LocalDateTime.now());
        }
        return switch (jobCode)
        {
            case "stockSnapshotTask.generate" -> generateStockSnapshot(LocalDate.now());
            case "stockWarningTask.scan" -> scanStockWarnings();
            case "dashboardPrecomputeTask.run" -> precomputeDashboard();
            case "reportSubscribeTask.push" -> pushReportSubscriptions();
            default -> JobRunResult.failed(jobCode, "not implemented", LocalDateTime.now());
        };
    }

    @Transactional(rollbackFor = Exception.class)
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
        stockSnapshotMapper.deleteItemsBySnapshotId(snapshot.getSnapshotId());

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
                BizTodo todo = new BizTodo();
                todo.setStoreId(STORE_ID);
                todo.setTodoType(5);
                todo.setPriority(2);
                todo.setSourceModule("analysis");
                todo.setSourceNo("SUB" + subscribe.getSubscribeId());
                todo.setTargetValue(subscribe.getSubscribeName());
                todo.setContent("报表推送：" + subscribe.getSubscribeName());
                todo.setLinkUrl("/analysis/subscribe");
                todo.setStatus(1);
                todo.setCreateTime(startedAt);
                todoService.save(todo);
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
