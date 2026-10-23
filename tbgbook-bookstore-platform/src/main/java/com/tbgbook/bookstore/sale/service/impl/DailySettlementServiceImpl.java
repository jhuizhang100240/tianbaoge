package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.CashierShift;
import com.tbgbook.bookstore.sale.domain.DailySettlement;
import com.tbgbook.bookstore.sale.domain.SettlementAggregate;
import com.tbgbook.bookstore.sale.domain.SettlementGenerateRequest;
import com.tbgbook.bookstore.sale.domain.SettlementLockResult;
import com.tbgbook.bookstore.sale.mapper.CashierShiftMapper;
import com.tbgbook.bookstore.sale.mapper.DailySettlementMapper;
import com.tbgbook.bookstore.sale.service.IDailySettlementService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 日结对账 Service 实现（阶段四步骤⑥）。
 *
 * <p><b>两个「时间窗」口径都写在这里（本次自定，见 A31）</b>：
 * <ol>
 *   <li><b>窗口</b>：给了 shiftId 且班次存在 → 用班次的实际开交班时间
 *       （openTime ~ closeTime，未交班时用 now() 封口）；否则退化为
 *       「settleDate 当天 00:00:00 ~ 23:59:59」。班次跨零点时按日期圈会漏单，
 *       按班次实际起止才准</li>
 *   <li><b>差异</b>：diffAmount 只在「给了 shiftId 且该班次已交班」时计算，
 *       取「交班实点现金 − 系统现金收款」；其余情况记 0。差异说明要的是现金实点
 *       与账面的差，而实点数只有交班时才有。不给班次就不算差异，而不是「差异为 0」
 *       —— 免得让「当天全店」这种口径假装对过账</li>
 * </ol>
 *
 * <p><b>挂单与聚合的关系</b>：generate 先把窗口内的订单挂上日结单号
 * （biz_order.settle_id），再按这个外键聚合 —— 而不是按时间窗重新捞一遍。
 * 差别在红冲：红冲单的 create_time 是「现在」，按时间窗重捞会落进别的窗口，
 * 让原窗口的账面凭空少一块；挂单号则始终聚合同一批订单。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class DailySettlementServiceImpl extends ServiceImpl<DailySettlementMapper, DailySettlement>
        implements IDailySettlementService
{
    /** 日结单号前缀（需求未定义，本次自定）：JS + yyyyMMdd + 6 位流水 */
    private static final String SETTLE_NO_PREFIX = "JS";

    private static final int SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final CashierShiftMapper shiftMapper;

    @Override
    public List<DailySettlement> selectDailySettlementList(DailySettlement settlement)
    {
        return baseMapper.selectDailySettlementList(settlement);
    }

    @Override
    public DailySettlement selectDailySettlementById(Long settleId)
    {
        return referenceGuard.requireExists(baseMapper, settleId, "日结单");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addDailySettlement(DailySettlement settlement)
    {
        if (settlement.getSettleDate() == null)
        {
            throw BizErrors.badRequest("对账日期不能为空");
        }
        String shiftName = nzStr(settlement.getShift());
        DailySettlement existing = findExisting(settlement.getSettleDate(), shiftName);
        if (existing != null)
        {
            throw BizErrors.duplicate("日结单", "settle_date+shift",
                    settlement.getSettleDate() + " " + shiftName);
        }

        DailySettlement settle = new DailySettlement();
        settle.setSettleDate(settlement.getSettleDate());
        settle.setShift(shiftName);
        settle.setStatus(DailySettlement.STATUS_PENDING);
        settle.setDiffReason(settlement.getDiffReason());
        settle.setRemark(settlement.getRemark());
        // 金额列一律 0：它们由 generate 聚合，不接受传入
        settle.setOrderCount(0);
        settle.setTotalAmount(BigDecimal.ZERO);
        settle.setCashAmount(BigDecimal.ZERO);
        settle.setWechatAmount(BigDecimal.ZERO);
        settle.setAlipayAmount(BigDecimal.ZERO);
        settle.setStoredAmount(BigDecimal.ZERO);
        settle.setRefundAmount(BigDecimal.ZERO);
        settle.setPointAmount(BigDecimal.ZERO);
        settle.setDiffAmount(BigDecimal.ZERO);
        Long predictedId = baseMapper.selectNextSettleId();
        settle.setSettleNo(buildSettleNo(predictedId == null ? 1L : predictedId));
        int rows = baseMapper.insert(settle);
        correctNo(settle);
        return rows;
    }

    /**
     * {@inheritDoc}
     *
     * <p>重复生成覆盖未锁定的单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailySettlement generate(SettlementGenerateRequest request)
    {
        LocalDate date = request.getSettleDate();
        CashierShift shift = (request.getShiftId() == null) ? null
                : referenceGuard.requireExistsIfPresent(shiftMapper, request.getShiftId(), "收银班次");
        String shiftName = (request.getShift() != null && !request.getShift().isBlank())
                ? request.getShift().trim()
                : (shift == null ? null : shift.getShiftName());

        DailySettlement existing = findExisting(date, shiftName);
        if (existing != null && Objects.equals(existing.getStatus(), DailySettlement.STATUS_LOCKED))
        {
            throw BizErrors.conflict("「" + date + " " + nzStr(shiftName) + "」的日结单已锁定，不能重新生成");
        }

        // 窗口边界按**应用侧**时间算（不能用库的 now()：库是 UTC、应用是 +08，差 8 小时
        // 会让窗口恒为空，见 docs/db/10_timezone_plus0800.sql）。上界放宽 1 秒防同秒漏单。
        LocalDateTime begin = windowBegin(date, shift);
        LocalDateTime end = windowEnd(date, shift);

        boolean isNew = (existing == null);
        DailySettlement settle = isNew ? new DailySettlement() : existing;
        if (isNew)
        {
            settle.setSettleDate(date);
            settle.setShift(nzStr(shiftName));
            settle.setStatus(DailySettlement.STATUS_PENDING);
            Long predictedId = baseMapper.selectNextSettleId();
            settle.setSettleNo(buildSettleNo(predictedId == null ? 1L : predictedId));
        }
        settle.setSettleBy(SecurityUtils.getUsername());
        settle.setSettleTime(LocalDateTime.now());
        if (isNew)
        {
            baseMapper.insert(settle);
            correctNo(settle);
        }

        // 先把窗口内订单挂上本单，再按外键聚合（红冲单不会串窗口）
        baseMapper.attachOrders(settle.getSettleId(), 1L, begin, end);
        SettlementAggregate agg = baseMapper.aggregate(settle.getSettleId());
        if (agg == null || nzi(agg.getOrderCount()) == 0)
        {
            throw BizErrors.ruleViolated("「" + date + " " + nzStr(shiftName)
                    + "」这个时间段内没有收款记录，无需对账");
        }

        BigDecimal diff = cashDiff(shift, agg);
        DailySettlement patch = new DailySettlement();
        patch.setSettleId(settle.getSettleId());
        patch.setOrderCount(nzi(agg.getOrderCount()));
        patch.setTotalAmount(nz(agg.getTotalAmount()));
        patch.setCashAmount(nz(agg.getCashAmount()));
        patch.setWechatAmount(nz(agg.getWechatAmount()));
        patch.setAlipayAmount(nz(agg.getAlipayAmount()));
        patch.setStoredAmount(nz(agg.getStoredAmount()));
        patch.setRefundAmount(nz(agg.getRefundAmount()));
        patch.setPointAmount(nz(agg.getPointAmount()));
        patch.setDiffAmount(diff);
        if (diff.signum() != 0 && nzStr(settle.getDiffReason()).isBlank())
        {
            patch.setDiffReason(buildDiffReason(agg, shift, diff));
        }
        baseMapper.updateById(patch);

        DailySettlement result = baseMapper.selectById(settle.getSettleId());
        if (shift != null && nzl(shift.getSettleId()) == 0)
        {
            CashierShift shiftPatch = new CashierShift();
            shiftPatch.setShiftId(shift.getShiftId());
            shiftPatch.setSettleId(result.getSettleId());
            shiftMapper.updateById(shiftPatch);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettlementLockResult lock(Long settleId, String diffReason)
    {
        DailySettlement settle = referenceGuard.requireExists(baseMapper, settleId, "日结单");
        if (Objects.equals(settle.getStatus(), DailySettlement.STATUS_LOCKED))
        {
            throw BizErrors.conflict("日结单「" + settle.getSettleNo() + "」已锁定");
        }
        boolean hasDiff = nz(settle.getDiffAmount()).signum() != 0;
        boolean noReason = (diffReason == null || diffReason.isBlank())
                && nzStr(settle.getDiffReason()).isBlank();
        if (hasDiff && noReason)
        {
            throw BizErrors.ruleViolated("本次对账差异为 " + nz(settle.getDiffAmount())
                    + " 元，锁定前必须填写差异说明");
        }

        int locked = baseMapper.lockOrders(settle.getSettleId());
        DailySettlement patch = new DailySettlement();
        patch.setSettleId(settleId);
        patch.setStatus(DailySettlement.STATUS_LOCKED);
        patch.setSettleBy(SecurityUtils.getUsername());
        patch.setSettleTime(LocalDateTime.now());
        if (diffReason != null && !diffReason.isBlank())
        {
            patch.setDiffReason(diffReason);
        }
        referenceGuard.requireAffected(baseMapper.updateById(patch), "日结单");

        SettlementLockResult result = new SettlementLockResult();
        result.setSettleId(settleId);
        result.setSettleNo(settle.getSettleNo());
        result.setStatus(DailySettlement.STATUS_LOCKED);
        result.setDiffAmount(nz(settle.getDiffAmount()));
        result.setDiffReason((patch.getDiffReason() == null) ? settle.getDiffReason() : patch.getDiffReason());
        result.setLockedOrderCount(locked);
        result.setSettleTime(patch.getSettleTime());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只接受备注与差异说明：金额列是聚合出来的，改它等于改账。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDailySettlement(DailySettlement settlement)
    {
        if (settlement.getSettleId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的日结单");
        }
        DailySettlement current = referenceGuard.requireExists(baseMapper, settlement.getSettleId(), "日结单");
        if (!Objects.equals(current.getStatus(), DailySettlement.STATUS_PENDING))
        {
            throw BizErrors.ruleViolated("日结单「" + current.getSettleNo() + "」当前为「"
                    + statusName(current.getStatus()) + "」，只有「1待对账」的可以修改");
        }
        DailySettlement patch = new DailySettlement();
        patch.setSettleId(current.getSettleId());
        patch.setDiffReason(settlement.getDiffReason());
        patch.setRemark(settlement.getRemark());
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "日结单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeDailySettlements(Long[] settleIds)
    {
        List<Long> idList = (settleIds == null) ? Collections.emptyList() : Arrays.asList(settleIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "日结单");
        for (Serializable id : ids)
        {
            DailySettlement one = referenceGuard.requireExists(baseMapper, id, "日结单");
            if (Objects.equals(one.getStatus(), DailySettlement.STATUS_LOCKED))
            {
                throw BizErrors.ruleViolated("日结单「" + one.getSettleNo()
                        + "」已锁定，不能删除（锁定是账目封存）");
            }
        }
        // 删单要把订单上的日结单号摘掉，否则那些订单永远挂在一张不存在的单上
        baseMapper.detachOrders(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 按「日期 + 班次名」找既有单。
     *
     * <p>⚠️ <b>必须在内存里再比一次班次名</b>：XML 的 {@code selectDailySettlementList} 在
     * {@code shift} 为空时会**跳过该条件**（{@code <if test="shift != null and shift != ''">}），
     * 于是「不带班次」的查询会把该日期下**所有班次**的单都捞回来 ——
     * 若只用返回的第一行，就会把「早班」的单错认成「全店」的单，
     * 进而覆盖掉带班次的那张（本会话自测逮到：全店日结把早班日结的 diffAmount 冲成了 0）。
     */
    private DailySettlement findExisting(LocalDate date, String shiftName)
    {
        DailySettlement query = new DailySettlement();
        query.setSettleDate(date);
        query.setShift(nzStr(shiftName));
        List<DailySettlement> list = baseMapper.selectDailySettlementList(query);
        for (DailySettlement one : list)
        {
            if (Objects.equals(one.getSettleDate(), date)
                    && nzStr(one.getShift()).equals(nzStr(shiftName)))
            {
                return one;
            }
        }
        return null;
    }

    /**
     * 窗口起：班次已开班用实际开班时间，否则当天 00:00。
     *
     * <p>⚠️ 用**应用侧**时间（与 JDBC 的 +08 写入同源），不能用库的 {@code now()}。
     */
    private LocalDateTime windowBegin(LocalDate date, CashierShift shift)
    {
        if (shift != null && shift.getOpenTime() != null)
        {
            return shift.getOpenTime();
        }
        return date.atStartOfDay();
    }

    /** 窗口止：班次已交班用交班时间，未交班用当刻，否则当天 23:59:59；一律**放宽 1 秒**防同秒漏单 */
    private LocalDateTime windowEnd(LocalDate date, CashierShift shift)
    {
        if (shift != null)
        {
            if (shift.getCloseTime() != null)
            {
                return shift.getCloseTime().plusSeconds(1);
            }
            if (shift.getOpenTime() != null)
            {
                return LocalDateTime.now().plusSeconds(1);
            }
        }
        return date.atTime(LocalTime.MAX);
    }

    /** 现金差异 = 交班实点 − 系统现金收款；班次未交班或没给班次时记 0（不算差异） */
    private BigDecimal cashDiff(CashierShift shift, SettlementAggregate agg)
    {
        if (shift == null || shift.getCloseTime() == null || shift.getCloseAmount() == null)
        {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return shift.getCloseAmount().subtract(nz(agg.getCashAmount())).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private String buildDiffReason(SettlementAggregate agg, CashierShift shift, BigDecimal diff)
    {
        String direction = diff.signum() > 0 ? "长款" : "短款";
        return "交班实点现金 " + nz(shift.getCloseAmount()) + " 元，系统现金收款 "
                + nz(agg.getCashAmount()) + " 元，" + direction + " " + diff.abs() + " 元";
    }

    private void correctNo(DailySettlement settle)
    {
        String actual = buildSettleNo(settle.getSettleId());
        if (!actual.equals(settle.getSettleNo()))
        {
            DailySettlement patch = new DailySettlement();
            patch.setSettleId(settle.getSettleId());
            patch.setSettleNo(actual);
            baseMapper.updateById(patch);
            settle.setSettleNo(actual);
        }
    }

    private String buildSettleNo(Long settleId)
    {
        return SETTLE_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", settleId);
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case DailySettlement.STATUS_PENDING -> "1待对账";
            case DailySettlement.STATUS_RECONCILED -> "2已对账";
            case DailySettlement.STATUS_LOCKED -> "3已锁定";
            default -> "未知";
        };
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }

    private long nzl(Long v)
    {
        return (v == null) ? 0L : v;
    }

    private String nzStr(String v)
    {
        return (v == null) ? "" : v;
    }
}
