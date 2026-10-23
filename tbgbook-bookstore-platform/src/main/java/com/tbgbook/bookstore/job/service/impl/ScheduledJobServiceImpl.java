package com.tbgbook.bookstore.job.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
