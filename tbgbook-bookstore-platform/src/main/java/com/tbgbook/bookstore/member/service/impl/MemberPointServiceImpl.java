package com.tbgbook.bookstore.member.service.impl;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.PointAdjustRequest;
import com.tbgbook.bookstore.member.domain.PointBatch;
import com.tbgbook.bookstore.member.domain.PointChangeResult;
import com.tbgbook.bookstore.member.domain.PointExpiringItem;
import com.tbgbook.bookstore.member.domain.PointExpiringResult;
import com.tbgbook.bookstore.member.domain.PointGrantRequest;
import com.tbgbook.bookstore.member.domain.PointLog;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.PointBatchMapper;
import com.tbgbook.bookstore.member.mapper.PointLogMapper;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 会员积分 Service 实现（阶段五步骤②，页面 A-47）。
 *
 * <p><b>批次口径（自定，登记 A35）</b>：
 * <ul>
 *   <li>增加（消费获得 / 人工调整正 / 赠送 / 退货返还）→ 新建一条批次并写一条流水</li>
 *   <li>扣减（抵扣消耗 / 人工调整负）→ 按 {@code expire_time asc} 先到期先消耗；
 *       跨批次时**每个批次一条流水**（batchId 定位），响应里的 batchId/logId 取第一条</li>
 *   <li>总账（{@code biz_member.points}）用**条件更新**（{@code points >= amount}）扣减，
 *       批次用「剩余足够才扣」的条件更新 —— 两步都在同一事务，任一不足即整体回滚</li>
 * </ul>
 *
 * <p><b>有效期</b>：{@code biz.points.valid.months}（默认 24）；到期提醒窗口
 * {@code biz.points.expire.notify.days}（默认 30）。**时间窗边界一律取应用侧时间**（A32）。
 *
 * <p><b>人工调整 / 赠送的流水类型</b>：契约的流水枚举（1消费获得 2抵扣消耗 3到期清零
 * 4人工调整 5退货返还）里没有「活动赠送」——赠送并入 4人工调整，备注写明「积分赠送：事由」。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberPointServiceImpl implements IMemberPointService
{
    /** 批次来源：1消费获得 */
    private static final int SOURCE_CONSUME = 1;

    /** 批次来源：2人工调整 */
    private static final int SOURCE_MANUAL = 2;

    /** 批次来源：3活动赠送 */
    private static final int SOURCE_GIFT = 3;

    /** 批次来源：4退货返还 */
    private static final int SOURCE_RETURN = 4;

    /** 流水类型：1消费获得 */
    private static final int CHANGE_GAIN = 1;

    /** 流水类型：2抵扣消耗 */
    private static final int CHANGE_CONSUME = 2;

    /** 流水类型：4人工调整（含赠送） */
    private static final int CHANGE_MANUAL = 4;

    /** 流水类型：5退货返还 */
    private static final int CHANGE_RETURN = 5;

    /** 批次状态：1有效 */
    private static final int BATCH_ACTIVE = 1;

    private static final String CFG_VALID_MONTHS = "biz.points.valid.months";

    private static final String CFG_EXPIRE_NOTIFY_DAYS = "biz.points.expire.notify.days";

    private static final int DEFAULT_VALID_MONTHS = 24;

    private static final int DEFAULT_NOTIFY_DAYS = 30;

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final PointBatchMapper pointBatchMapper;

    private final PointLogMapper pointLogMapper;

    private final ISysConfigService configService;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<PointBatch> selectPointBatchList(PointBatch batch)
    {
        return pointBatchMapper.selectPointBatchList(batch);
    }

    @Override
    public PointBatch selectPointBatchById(Long batchId)
    {
        return referenceGuard.requireExists(pointBatchMapper, batchId, "积分批次");
    }

    @Override
    public List<PointLog> selectPointLogList(PointLog log)
    {
        return pointLogMapper.selectPointLogList(log);
    }

    @Override
    public PointLog selectPointLogById(Long logId)
    {
        return referenceGuard.requireExists(pointLogMapper, logId, "积分流水");
    }

    // ==================================================================
    // 人工动作
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointChangeResult adjustPoints(PointAdjustRequest request)
    {
        if (request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        Integer change = request.getPointsChange();
        if (change == null || change == 0)
        {
            throw BizErrors.ruleViolated("积分变动值不能为 0");
        }
        if (isBlank(request.getReason()))
        {
            throw BizErrors.ruleViolated("调整原因未填");
        }
        String reason = request.getReason().trim();
        if (change > 0)
        {
            return gainInternal(request.getMemberId(), change, SOURCE_MANUAL, CHANGE_MANUAL,
                    "", reason, null);
        }
        return consumeInternal(request.getMemberId(), -change, CHANGE_MANUAL, "", reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PointChangeResult grantPoints(PointGrantRequest request)
    {
        if (request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        if (request.getPoints() == null || request.getPoints() <= 0)
        {
            throw BizErrors.ruleViolated("赠送积分数必须大于 0");
        }
        if (isBlank(request.getReason()))
        {
            throw BizErrors.ruleViolated("赠送事由未填");
        }
        return gainInternal(request.getMemberId(), request.getPoints(), SOURCE_GIFT, CHANGE_MANUAL,
                "", "积分赠送：" + request.getReason().trim(), request.getExpireMonths());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PointExpiringResult selectExpiring(Integer days, Long memberId)
    {
        int window = (days == null) ? cfgInt(CFG_EXPIRE_NOTIFY_DAYS, DEFAULT_NOTIFY_DAYS) : days;
        if (window <= 0)
        {
            throw BizErrors.ruleViolated("天数阈值必须大于 0");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.plusDays(window);
        List<PointExpiringItem> items = pointBatchMapper.selectExpiringItems(now, cutoff, memberId);
        Set<Long> members = new HashSet<>();
        int total = 0;
        for (PointExpiringItem item : items)
        {
            if (item.getExpireTime() != null)
            {
                item.setDaysLeft(ChronoUnit.DAYS.between(now, item.getExpireTime()));
            }
            if (item.getMemberId() != null)
            {
                members.add(item.getMemberId());
            }
            total += nz(item.getRemainPoints());
        }
        PointExpiringResult result = new PointExpiringResult();
        result.setList(items);
        result.setMemberCount(members.size());
        result.setTotalPoints(total);
        return result;
    }

    // ==================================================================
    // 收银 / 退货链路（收编入口）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductPoints(Long memberId, int points, String refNo, String remark)
    {
        return consumeInternal(memberId, points, CHANGE_CONSUME, refNo, remark).getPointsAfter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int awardPoints(Long memberId, int points, String refNo, String remark)
    {
        return gainInternal(memberId, points, SOURCE_CONSUME, CHANGE_GAIN, refNo, remark, null)
                .getPointsAfter();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int returnPoints(Long memberId, int points, String refNo, String remark)
    {
        return gainInternal(memberId, points, SOURCE_RETURN, CHANGE_RETURN, refNo, remark, null)
                .getPointsAfter();
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 增加积分：新建批次 + 总账累加 + 写流水 */
    private PointChangeResult gainInternal(Long memberId, int points, int sourceType, int changeType,
            String refNo, String remark, Integer expireMonths)
    {
        if (points <= 0)
        {
            throw BizErrors.badRequest("增加积分数必须大于 0");
        }
        referenceGuard.requireExists(memberMapper, memberId, "会员");
        int months = (expireMonths == null) ? cfgInt(CFG_VALID_MONTHS, DEFAULT_VALID_MONTHS) : expireMonths;
        if (months <= 0)
        {
            throw BizErrors.ruleViolated("积分有效期月数必须大于 0");
        }

        LocalDateTime now = LocalDateTime.now();
        PointBatch batch = new PointBatch();
        batch.setMemberId(memberId);
        batch.setSourceType(sourceType);
        batch.setTotalPoints(points);
        batch.setUsedPoints(0);
        batch.setRemainPoints(points);
        batch.setGainTime(now);
        batch.setExpireTime(now.plusMonths(months));
        batch.setStatus(BATCH_ACTIVE);
        batch.setRefNo(refNo == null ? "" : refNo);
        batch.setCreateTime(now);
        pointBatchMapper.insert(batch);

        memberMapper.addPoints(memberId, points);
        int after = nz(memberMapper.selectPoints(memberId));
        PointLog log = buildLog(memberId, changeType, points, after, batch.getBatchId(), refNo, remark);
        pointLogMapper.insert(log);

        PointChangeResult result = new PointChangeResult();
        result.setPointsAfter(after);
        result.setBatchId(batch.getBatchId());
        result.setLogId(log.getLogId());
        return result;
    }

    /** 扣减积分：总账条件更新 + 批次先到期先消耗（跨批次每个批次一条流水） */
    private PointChangeResult consumeInternal(Long memberId, int points, int changeType,
            String refNo, String remark)
    {
        if (points <= 0)
        {
            throw BizErrors.badRequest("扣减积分数必须大于 0");
        }
        referenceGuard.requireExists(memberMapper, memberId, "会员");

        List<PointBatch> batches = pointBatchMapper.selectAvailableBatches(memberId);
        int available = 0;
        for (PointBatch batch : batches)
        {
            available += nz(batch.getRemainPoints());
        }
        if (available < points)
        {
            throw BizErrors.ruleViolated("扣减积分超过会员可用积分：当前可用 " + available + " 分");
        }

        int before = nz(memberMapper.selectPoints(memberId));
        if (memberMapper.deductPoints(memberId, points) == 0)
        {
            throw BizErrors.ruleViolated("扣减积分超过会员可用积分：当前可用 " + before + " 分");
        }

        int remaining = points;
        int cursor = before;
        Long firstBatchId = null;
        Long firstLogId = null;
        for (PointBatch batch : batches)
        {
            if (remaining <= 0)
            {
                break;
            }
            int take = Math.min(nz(batch.getRemainPoints()), remaining);
            if (take <= 0 || pointBatchMapper.consumeFromBatch(batch.getBatchId(), take) == 0)
            {
                continue;
            }
            pointBatchMapper.markBatchUsedUp(batch.getBatchId());
            remaining -= take;
            cursor -= take;
            PointLog log = buildLog(memberId, changeType, -take, cursor, batch.getBatchId(), refNo, remark);
            pointLogMapper.insert(log);
            if (firstBatchId == null)
            {
                firstBatchId = batch.getBatchId();
                firstLogId = log.getLogId();
            }
        }
        if (remaining > 0)
        {
            // 批次被并发抢占导致不足：整体回滚（总账扣减也不能留）
            throw BizErrors.conflict("积分批次刚被其他操作占用，请刷新后重试");
        }

        PointChangeResult result = new PointChangeResult();
        result.setPointsAfter(cursor);
        result.setBatchId(firstBatchId);
        result.setLogId(firstLogId);
        return result;
    }

    private PointLog buildLog(Long memberId, int changeType, int change, int after,
            Long batchId, String refNo, String remark)
    {
        PointLog log = new PointLog();
        log.setMemberId(memberId);
        log.setChangeType(changeType);
        log.setPointsChange(change);
        log.setPointsAfter(after);
        log.setBatchId(batchId == null ? 0L : batchId);
        log.setRefNo(refNo == null ? "" : refNo);
        log.setRemark(remark == null ? "" : remark);
        log.setCreateBy(currentUsername());
        log.setCreateTime(LocalDateTime.now());
        return log;
    }

    /** 读配置（biz.points.*），缺失或非法时用默认值 */
    private int cfgInt(String key, int fallback)
    {
        try
        {
            String raw = configService.selectConfigByKey(key);
            if (raw != null && !raw.isBlank())
            {
                return Integer.parseInt(raw.trim());
            }
        }
        catch (Exception ignored)
        {
            // 配置损坏不应阻断积分变动，落到默认值
        }
        return fallback;
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

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private int nz(Integer value)
    {
        return value == null ? 0 : value;
    }
}
