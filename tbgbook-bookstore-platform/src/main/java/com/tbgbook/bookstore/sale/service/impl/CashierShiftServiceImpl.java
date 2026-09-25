package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.tbgbook.bookstore.sale.domain.SettlementAggregate;
import com.tbgbook.bookstore.sale.domain.ShiftCloseRequest;
import com.tbgbook.bookstore.sale.domain.ShiftCloseResult;
import com.tbgbook.bookstore.sale.domain.ShiftOpenRequest;
import com.tbgbook.bookstore.sale.mapper.CashierShiftMapper;
import com.tbgbook.bookstore.sale.service.ICashierShiftService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 收银员班次 Service 实现（阶段四步骤⑥）。
 *
 * <p><b>开班的两条前置</b>（契约 409）：① 同一收银员同一日同一班次不可重复开班；
 * ② 不允许同时开着两个班（存在未交班的历史班次就先交班）。
 *
 * <p><b>交班时的统计口径</b>：班次与订单没有外键 —— 实测确认 biz_order
 * 没有任何班次列。故本班次的订单只能用「openTime ~ 交班时刻」这个时间窗圈定，
 * 班次跨零点时窗口自然跟着跨（这正是不能按日期圈的原因）。
 *
 * <p><b>现金差异</b>：cashDiff = 实点现金 − 窗口内现金收款。它只是展示，
 * 真正入账的差异在日结单上（生成日结时会按同一口径再算一次）。
 *
 * <p><b>URL 是 /system/shift</b>（平台侧、权限串 system:shift:*），
 * 但业务语义属销售域，故实现落在这里 —— 已登记 A31。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class CashierShiftServiceImpl extends ServiceImpl<CashierShiftMapper, CashierShift>
        implements ICashierShiftService
{
    private static final int SCALE = 2;

    private final ReferenceGuard referenceGuard;

    @Override
    public List<CashierShift> selectCashierShiftList(CashierShift shift)
    {
        return baseMapper.selectCashierShiftList(shift);
    }

    @Override
    public CashierShift selectCashierShiftById(Long shiftId)
    {
        return referenceGuard.requireExists(baseMapper, shiftId, "收银班次");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CashierShift open(ShiftOpenRequest request)
    {
        Long userId = currentUserId();
        String userName = SecurityUtils.getUsername();
        LocalDate today = LocalDate.now();

        if (baseMapper.selectByUserDateCode(userId, today, request.getShiftCode().trim()) != null)
        {
            throw BizErrors.conflict("你今天已经开过「" + request.getShiftName() + "」班了");
        }
        List<CashierShift> openOnes = baseMapper.selectOpenByUser(userId);
        if (!openOnes.isEmpty())
        {
            CashierShift old = openOnes.get(0);
            throw BizErrors.conflict("你还有一班没交（" + old.getShiftDate() + " "
                    + old.getShiftName() + "，开班于 " + old.getOpenTime() + "），请先交班");
        }

        CashierShift shift = new CashierShift();
        shift.setShiftDate(today);
        shift.setShiftCode(request.getShiftCode().trim());
        shift.setShiftName(request.getShiftName().trim());
        shift.setUserId(userId);
        shift.setUserName(userName);
        shift.setNickName(nickName());
        shift.setOpenAmount(nz(request.getOpenAmount()));
        shift.setCloseAmount(BigDecimal.ZERO);
        shift.setOrderCount(0);
        shift.setTotalAmount(BigDecimal.ZERO);
        shift.setSettleId(0L);
        shift.setStatus(CashierShift.STATUS_OPEN);
        shift.setOpenTime(LocalDateTime.now());
        referenceGuard.requireAffected(baseMapper.insert(shift), "收银班次");
        return baseMapper.selectById(shift.getShiftId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftCloseResult close(ShiftCloseRequest request)
    {
        CashierShift shift = referenceGuard.requireExists(baseMapper, request.getShiftId(), "收银班次");
        if (Objects.equals(shift.getStatus(), CashierShift.STATUS_CLOSED))
        {
            throw BizErrors.conflict("班次「" + shift.getShiftName() + "」已交班，不能重复交班");
        }

        LocalDateTime closeTime = LocalDateTime.now();
        SettlementAggregate agg = baseMapper.aggregateShift(shift.getOpenTime(), closeTime);
        BigDecimal cash = (agg == null) ? BigDecimal.ZERO : nz(agg.getCashAmount());
        BigDecimal closeAmount = nz(request.getCloseAmount());
        BigDecimal cashDiff = closeAmount.subtract(cash).setScale(SCALE, RoundingMode.HALF_UP);

        CashierShift patch = new CashierShift();
        patch.setShiftId(shift.getShiftId());
        patch.setStatus(CashierShift.STATUS_CLOSED);
        patch.setCloseAmount(closeAmount);
        patch.setOrderCount((agg == null) ? 0 : nzi(agg.getOrderCount()));
        patch.setTotalAmount((agg == null) ? BigDecimal.ZERO : nz(agg.getTotalAmount()));
        patch.setCloseTime(closeTime);
        referenceGuard.requireAffected(baseMapper.updateById(patch), "收银班次");

        ShiftCloseResult result = new ShiftCloseResult();
        result.setShiftId(shift.getShiftId());
        result.setShiftCode(shift.getShiftCode());
        result.setShiftName(shift.getShiftName());
        result.setOrderCount(patch.getOrderCount());
        result.setTotalAmount(patch.getTotalAmount());
        result.setCashAmount(cash);
        result.setCloseAmount(closeAmount);
        result.setCashDiff(cashDiff);
        result.setStatus(CashierShift.STATUS_CLOSED);
        result.setCloseTime(closeTime);
        return result;
    }

    @Override
    public CashierShift current()
    {
        List<CashierShift> openOnes = baseMapper.selectOpenByUser(currentUserId());
        return openOnes.isEmpty() ? null : openOnes.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeCashierShifts(Long[] shiftIds)
    {
        List<Long> idList = (shiftIds == null) ? Collections.emptyList() : Arrays.asList(shiftIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "收银班次");
        for (Serializable id : ids)
        {
            CashierShift one = referenceGuard.requireExists(baseMapper, id, "收银班次");
            if (Objects.equals(one.getStatus(), CashierShift.STATUS_OPEN))
            {
                throw BizErrors.ruleViolated("班次「" + one.getShiftName()
                        + "」还在进行中，不能删除（先交班）");
            }
            // 已被日结单引用的班次是账目凭证的一部分，删了会造成「日结单上的班次查不到」
            if (nzl(one.getSettleId()) > 0)
            {
                throw BizErrors.referenced("收银班次", "日结单", 1,
                        "班次「" + one.getShiftName() + "」已被日结单引用（settle_id="
                                + one.getSettleId() + "），不能删除");
            }
        }
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private Long currentUserId()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw BizErrors.unauthorized("未登录，无法开班或交班");
        }
        return userId;
    }

    /** 姓名：拿不到就退回账号（班次上的 nick_name 是冗余，不强求） */
    private String nickName()
    {
        try
        {
            String nick = SecurityUtils.getLoginUser().getUser().getNickName();
            return (nick == null || nick.isBlank()) ? SecurityUtils.getUsername() : nick;
        }
        catch (Exception e)
        {
            return SecurityUtils.getUsername();
        }
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
}
