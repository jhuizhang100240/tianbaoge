package com.tbgbook.bookstore.usedbook.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.bookstore.member.service.IMemberGrowthService;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.bookstore.usedbook.domain.AcceptRequest;
import com.tbgbook.bookstore.usedbook.domain.AcceptResult;
import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;
import com.tbgbook.bookstore.usedbook.domain.Recycle;
import com.tbgbook.bookstore.usedbook.domain.RecycleItem;
import com.tbgbook.bookstore.usedbook.domain.RejectRequest;
import com.tbgbook.bookstore.usedbook.domain.SettleRequest;
import com.tbgbook.bookstore.usedbook.domain.SettleResult;
import com.tbgbook.bookstore.usedbook.mapper.RecycleItemMapper;
import com.tbgbook.bookstore.usedbook.mapper.RecycleMapper;
import com.tbgbook.bookstore.usedbook.service.IUsedBookGradeService;
import com.tbgbook.bookstore.usedbook.service.IUsedBookRecycleService;
import com.tbgbook.bookstore.usedbook.service.IUsedBookStockService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 二手书回收单 Service 实现（阶段六步骤②，页面 A-41）。
 *
 * <p><b>自定口径（登记 A45）</b>：
 * <ol>
 *   <li><b>回收单号</b> {@code RC + yyyyMMddHHmmss + 4 位流水}（流水取当前最大 recycle_id + 1，
 *       避免 {@code information_schema} 缓存问题，同 A42/A44）；</li>
 *   <li><b>提交</b>：会员必须存在（404），昵称快照；状态置 1待验收、写 submitTime；
 *       {@code items} 非空时与单据头同一事务落库；</li>
 *   <li><b>修改</b>：仅 1待验收 可改；传 {@code items} 时**整体替换**明细（先删后插）；</li>
 *   <li><b>删除</b>：仅 1待验收 / 4已拒绝 / 5已取消 **且未生成任何二手书副本**的可删，
 *       明细随头级联删除；</li>
 *   <li><b>验收</b>：仅 1待验收 可验收（否则 409「回收单已验收」）；逐行校验
 *       {@code judgeGradeId} 存在、{@code itemStatus ∈ (2,3)}；<b>判定成色 ≠ 提交成色 → 置
 *       isMismatch=1 且 judgeNote 必填（422）</b>；{@code itemStatus=3} 必填 rejectReason；
 *       合格行要求 {@code basePrice > 0}（否则 422「基准价缺失」），按判定成色系数重算
 *       {@code finalPrice} 并生成二手书副本（回填 usedBookId）；头部状态置 2验收中、
 *       写 acceptTime/acceptBy、结算方式、最终金额与不一致标记；</li>
 *   <li><b>拒收</b>：仅 1待验收 可整单拒收（否则 409），置 4已拒绝 + 原因；</li>
 *   <li><b>结算</b>：仅 2验收中 可结算（否则 409）；金额必须等于最终回收价合计（422）；
 *       现金仅登记；**储值账户**走 {@code IMemberBalanceService#creditRecycleSettlement}
 *       （本金入账 + 流水 type=1）；**积分**按 1 元 = 100 积分走
 *       {@code IMemberPointService#awardPoints}；储值 / 积分两种方式额外写成长值流水
 *       （1 元 = 1 成长值，changeType=2 人工调整）；结算后置 3已完成 + settleTime。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class UsedBookRecycleServiceImpl extends ServiceImpl<RecycleMapper, Recycle>
        implements IUsedBookRecycleService
{
    /** 回收单号前缀（需求 §3.2 编码表：回收单 RC） */
    private static final String RECYCLE_NO_PREFIX = "RC";

    /** 单店部署固定门店ID */
    private static final long DEFAULT_STORE_ID = 1L;

    /** 积分折算：1 元 = 100 积分 */
    private static final BigDecimal POINTS_PER_YUAN = new BigDecimal("100");

    private static final DateTimeFormatter NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final RecycleItemMapper recycleItemMapper;

    private final IUsedBookGradeService gradeService;

    private final IUsedBookStockService usedBookStockService;

    private final IMemberBalanceService memberBalanceService;

    private final IMemberPointService memberPointService;

    private final IMemberGrowthService memberGrowthService;

    @Override
    public List<Recycle> selectRecycleList(Recycle recycle)
    {
        return baseMapper.selectRecycleList(recycle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Recycle selectRecycleById(Long recycleId)
    {
        Recycle recycle = referenceGuard.requireExists(baseMapper, recycleId, "回收单");
        recycle.setItems(selectItems(recycleId));
        return recycle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addRecycle(Recycle recycle)
    {
        if (recycle.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        Member member = referenceGuard.requireExists(memberMapper, recycle.getMemberId(), "会员");
        LocalDateTime now = LocalDateTime.now();
        recycle.setRecycleId(null);
        recycle.setStoreId(DEFAULT_STORE_ID);
        recycle.setRecycleNo(generateNo());
        recycle.setMemberName(member.getNickname());
        recycle.setBookKind(0);
        recycle.setTotalQty(0);
        recycle.setEstimatedAmount(BigDecimal.ZERO);
        recycle.setFinalAmount(BigDecimal.ZERO);
        recycle.setHasMismatch(0);
        recycle.setAppointmentSlot(recycle.getAppointmentSlot() == null
                ? "" : recycle.getAppointmentSlot().trim());
        recycle.setSettleType(recycle.getSettleType() == null
                ? Recycle.SETTLE_CASH : recycle.getSettleType());
        recycle.setSettleAmount(BigDecimal.ZERO);
        recycle.setSettleTime(null);
        recycle.setStatus(Recycle.STATUS_PENDING);
        recycle.setRejectReason("");
        recycle.setSubmitTime(now);
        recycle.setAcceptTime(null);
        recycle.setAcceptBy("");
        recycle.setRemark(recycle.getRemark() == null ? "" : recycle.getRemark());
        recycle.setCreateBy(currentUsername());
        recycle.setCreateTime(now);
        recycle.setUpdateBy(currentUsername());
        recycle.setUpdateTime(now);
        int rows = baseMapper.insert(recycle);
        if (recycle.getItems() != null && !recycle.getItems().isEmpty())
        {
            for (RecycleItem item : recycle.getItems())
            {
                insertItem(recycle, item, now);
            }
            recalcAggregates(recycle.getRecycleId());
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRecycle(Recycle recycle)
    {
        if (recycle.getRecycleId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的回收单");
        }
        Recycle existing = referenceGuard.requireExists(baseMapper, recycle.getRecycleId(), "回收单");
        if (!Integer.valueOf(Recycle.STATUS_PENDING).equals(existing.getStatus()))
        {
            throw BizErrors.ruleViolated("回收单当前为「" + statusName(existing.getStatus())
                    + "」，只有「待验收」的回收单可以修改");
        }
        LocalDateTime now = LocalDateTime.now();
        if (recycle.getItems() != null)
        {
            recycleItemMapper.delete(new LambdaQueryWrapper<RecycleItem>()
                    .eq(RecycleItem::getRecycleId, recycle.getRecycleId()));
            for (RecycleItem item : recycle.getItems())
            {
                insertItem(existing, item, now);
            }
        }
        // 头字段服务端接管
        recycle.setStoreId(existing.getStoreId());
        recycle.setRecycleNo(existing.getRecycleNo());
        recycle.setMemberId(existing.getMemberId());
        recycle.setMemberName(existing.getMemberName());
        recycle.setBookKind(existing.getBookKind());
        recycle.setTotalQty(existing.getTotalQty());
        recycle.setEstimatedAmount(existing.getEstimatedAmount());
        recycle.setFinalAmount(existing.getFinalAmount());
        recycle.setHasMismatch(existing.getHasMismatch());
        recycle.setSettleAmount(existing.getSettleAmount());
        recycle.setSettleTime(existing.getSettleTime());
        recycle.setStatus(existing.getStatus());
        recycle.setRejectReason(existing.getRejectReason());
        recycle.setSubmitTime(existing.getSubmitTime());
        recycle.setAcceptTime(existing.getAcceptTime());
        recycle.setAcceptBy(existing.getAcceptBy());
        if (recycle.getSettleType() == null)
        {
            recycle.setSettleType(existing.getSettleType());
        }
        if (recycle.getAppointmentSlot() == null)
        {
            recycle.setAppointmentSlot(existing.getAppointmentSlot());
        }
        if (recycle.getAppointmentDate() == null)
        {
            recycle.setAppointmentDate(existing.getAppointmentDate());
        }
        if (recycle.getRemark() == null)
        {
            recycle.setRemark(existing.getRemark());
        }
        recycle.setCreateBy(existing.getCreateBy());
        recycle.setCreateTime(existing.getCreateTime());
        recycle.setUpdateBy(currentUsername());
        recycle.setUpdateTime(now);
        int rows = baseMapper.updateById(recycle);
        recalcAggregates(recycle.getRecycleId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeRecycles(Long[] recycleIds)
    {
        List<Long> idList = (recycleIds == null) ? Collections.emptyList() : Arrays.asList(recycleIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "回收单");
        List<RecycleItem> allItems = new ArrayList<>();
        for (Serializable id : ids)
        {
            Recycle row = referenceGuard.requireExists(baseMapper, id, "回收单");
            Integer status = row.getStatus();
            if (!Integer.valueOf(Recycle.STATUS_PENDING).equals(status)
                    && !Integer.valueOf(Recycle.STATUS_REJECTED).equals(status)
                    && !Integer.valueOf(Recycle.STATUS_CANCELLED).equals(status))
            {
                throw BizErrors.ruleViolated("回收单当前为「" + statusName(status)
                        + "」，需要留痕不能删除（recycleId=" + id + "）");
            }
            allItems.addAll(selectItems(row.getRecycleId()));
        }
        for (RecycleItem item : allItems)
        {
            if (item.getUsedBookId() != null && item.getUsedBookId() > 0)
            {
                throw BizErrors.ruleViolated("该回收单已生成二手书副本，需要留痕不能删除；"
                        + "如需下架请在二手书库存中操作");
            }
        }
        for (Serializable id : ids)
        {
            recycleItemMapper.delete(new LambdaQueryWrapper<RecycleItem>()
                    .eq(RecycleItem::getRecycleId, id));
        }
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcceptResult accept(Long recycleId, AcceptRequest request)
    {
        Recycle recycle = referenceGuard.requireExists(baseMapper, recycleId, "回收单");
        if (!Integer.valueOf(Recycle.STATUS_PENDING).equals(recycle.getStatus()))
        {
            throw BizErrors.conflict("回收单当前为「" + statusName(recycle.getStatus())
                    + "」，不能重复验收");
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
        {
            throw BizErrors.ruleViolated("验收明细不能为空");
        }
        if (request.getSettleType() == null || request.getSettleType() < Recycle.SETTLE_CASH
                || request.getSettleType() > Recycle.SETTLE_POINTS)
        {
            throw BizErrors.ruleViolated("结算方式取值必须是 1现金 / 2储值账户 / 3积分");
        }
        List<RecycleItem> items = selectItems(recycleId);
        if (items.isEmpty())
        {
            throw BizErrors.ruleViolated("该回收单没有明细，不能验收");
        }
        LocalDateTime now = LocalDateTime.now();
        AcceptResult result = new AcceptResult();
        for (AcceptRequest.Item line : request.getItems())
        {
            if (line.getItemId() == null)
            {
                throw BizErrors.badRequest("验收明细缺少 itemId");
            }
            RecycleItem item = items.stream()
                    .filter(row -> line.getItemId().equals(row.getItemId()))
                    .findFirst().orElse(null);
            if (item == null)
            {
                throw BizErrors.notFound("回收明细", line.getItemId());
            }
            if (line.getItemStatus() == null
                    || (line.getItemStatus() != RecycleItem.STATUS_ACCEPTED
                    && line.getItemStatus() != RecycleItem.STATUS_REJECTED))
            {
                throw BizErrors.ruleViolated("验收状态取值必须是 2合格已收 / 3拒收（itemId="
                        + line.getItemId() + "）");
            }
            ConditionGrade judge = gradeService.requireGrade(line.getJudgeGradeId(), null);
            boolean mismatch = !judge.getGradeId().equals(item.getSubmitGradeId());
            if (mismatch && (line.getJudgeNote() == null || line.getJudgeNote().isBlank()))
            {
                throw BizErrors.ruleViolated("成色判定与会员提交不一致时必须填写判定说明（itemId="
                        + line.getItemId() + "：" + item.getSubmitGradeName() + " → "
                        + judge.getGradeName() + "）");
            }
            if (line.getItemStatus() == RecycleItem.STATUS_REJECTED
                    && (line.getRejectReason() == null || line.getRejectReason().isBlank()))
            {
                throw BizErrors.ruleViolated("拒收必须填写拒收原因（itemId=" + line.getItemId() + "）");
            }

            RecycleItem patch = new RecycleItem();
            patch.setItemId(item.getItemId());
            patch.setJudgeGradeId(judge.getGradeId());
            patch.setJudgeGradeName(judge.getGradeName());
            patch.setIsMismatch(mismatch ? 1 : 0);
            patch.setJudgeNote(line.getJudgeNote() == null ? "" : line.getJudgeNote().trim());
            patch.setItemStatus(line.getItemStatus());
            if (line.getItemStatus() == RecycleItem.STATUS_REJECTED)
            {
                patch.setPriceFactor(BigDecimal.ZERO);
                patch.setFinalPrice(BigDecimal.ZERO);
                patch.setRejectReason(line.getRejectReason().trim());
                patch.setUsedBookId(0L);
                recycleItemMapper.updateById(patch);
                continue;
            }

            if (nz(item.getBasePrice()).signum() <= 0)
            {
                throw BizErrors.ruleViolated("基准价缺失：图书「" + item.getTitle()
                        + "」未建档或未填基准价，不能按成色系数计算回收价（itemId="
                        + item.getItemId() + "）");
            }
            BigDecimal factor = judge.getPriceFactor() == null
                    ? BigDecimal.ONE : judge.getPriceFactor();
            BigDecimal finalPrice = item.getBasePrice().multiply(factor)
                    .setScale(2, RoundingMode.HALF_UP);
            // 生成副本时需要用到「店员判定成色」（快照旧值仍是提交阶段留空的 0）
            item.setJudgeGradeId(judge.getGradeId());
            item.setJudgeGradeName(judge.getGradeName());
            Long usedBookId = usedBookStockService.createFromRecycle(item, recycleId,
                    item.getItemId(), finalPrice);
            patch.setPriceFactor(factor);
            patch.setFinalPrice(finalPrice);
            patch.setRejectReason("");
            patch.setUsedBookId(usedBookId);
            recycleItemMapper.updateById(patch);
            result.getGeneratedUsedBookIds().add(usedBookId);
            if (mismatch)
            {
                AcceptResult.MismatchItem m = new AcceptResult.MismatchItem();
                m.setItemId(item.getItemId());
                m.setIsbn(item.getIsbn());
                m.setTitle(item.getTitle());
                m.setSubmitGradeName(item.getSubmitGradeName());
                m.setJudgeGradeName(judge.getGradeName());
                m.setJudgeNote(patch.getJudgeNote());
                result.getMismatchItems().add(m);
            }
        }

        recalcAggregates(recycleId);
        Recycle after = baseMapper.selectById(recycleId);
        Recycle head = new Recycle();
        head.setRecycleId(recycleId);
        head.setStatus(Recycle.STATUS_ACCEPTED);
        head.setAcceptTime(now);
        head.setAcceptBy(currentUsername());
        head.setSettleType(request.getSettleType());
        head.setUpdateBy(currentUsername());
        head.setUpdateTime(now);
        baseMapper.updateById(head);

        result.setFinalAmount(nz(after.getFinalAmount()));
        result.setHasMismatch(nzInt(after.getHasMismatch()));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(Long recycleId, RejectRequest request)
    {
        Recycle recycle = referenceGuard.requireExists(baseMapper, recycleId, "回收单");
        if (!Integer.valueOf(Recycle.STATUS_PENDING).equals(recycle.getStatus()))
        {
            throw BizErrors.conflict("回收单当前为「" + statusName(recycle.getStatus())
                    + "」，不能拒收");
        }
        if (request == null || request.getRejectReason() == null
                || request.getRejectReason().isBlank())
        {
            throw BizErrors.ruleViolated("拒收原因未填");
        }
        LocalDateTime now = LocalDateTime.now();
        Recycle patch = new Recycle();
        patch.setRecycleId(recycleId);
        patch.setStatus(Recycle.STATUS_REJECTED);
        patch.setRejectReason(request.getRejectReason().trim());
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        return baseMapper.updateById(patch);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SettleResult settle(Long recycleId, SettleRequest request)
    {
        Recycle recycle = referenceGuard.requireExists(baseMapper, recycleId, "回收单");
        if (!Integer.valueOf(Recycle.STATUS_ACCEPTED).equals(recycle.getStatus()))
        {
            throw BizErrors.conflict("回收单当前为「" + statusName(recycle.getStatus())
                    + "」，未验收或已结算，不能结算");
        }
        Integer settleType = request == null || request.getSettleType() == null
                ? recycle.getSettleType() : request.getSettleType();
        if (settleType == null || settleType < Recycle.SETTLE_CASH
                || settleType > Recycle.SETTLE_POINTS)
        {
            throw BizErrors.ruleViolated("结算方式取值必须是 1现金 / 2储值账户 / 3积分");
        }
        BigDecimal finalAmount = nz(recycle.getFinalAmount());
        BigDecimal amount = request == null || request.getSettleAmount() == null
                ? finalAmount : request.getSettleAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(finalAmount) != 0)
        {
            throw BizErrors.ruleViolated("结算金额 " + amount + " 与最终回收价合计 "
                    + finalAmount + " 不符");
        }

        String linkageNote = null;
        if (settleType == Recycle.SETTLE_BALANCE)
        {
            memberBalanceService.creditRecycleSettlement(recycle.getMemberId(), amount,
                    recycle.getRecycleNo(), "二手书回收结算入账");
            linkageNote = "已入储值账户本金 " + amount + " 元";
        }
        else if (settleType == Recycle.SETTLE_POINTS)
        {
            int points = amount.multiply(POINTS_PER_YUAN).setScale(0, RoundingMode.DOWN).intValue();
            if (points > 0)
            {
                memberPointService.awardPoints(recycle.getMemberId(), points,
                        recycle.getRecycleNo(), "二手书回收结算");
            }
            linkageNote = "已赠送积分 " + points + " 分（1 元 = 100 积分）";
        }
        if (settleType != Recycle.SETTLE_CASH && amount.signum() > 0)
        {
            memberGrowthService.addGrowth(recycle.getMemberId(), 2,
                    amount.setScale(0, RoundingMode.DOWN).intValue(),
                    recycle.getRecycleNo(), "二手书回收结算");
        }

        LocalDateTime now = LocalDateTime.now();
        Recycle patch = new Recycle();
        patch.setRecycleId(recycleId);
        patch.setStatus(Recycle.STATUS_DONE);
        patch.setSettleType(settleType);
        patch.setSettleAmount(amount);
        patch.setSettleTime(now);
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        baseMapper.updateById(patch);

        SettleResult result = new SettleResult();
        result.setRecycleId(recycleId);
        result.setRecycleNo(recycle.getRecycleNo());
        result.setSettleType(settleType);
        result.setSettleAmount(amount);
        result.setSettleTime(now);
        result.setStatus(Recycle.STATUS_DONE);
        result.setLinkageNote(linkageNote);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recalcAggregates(Long recycleId)
    {
        List<RecycleItem> items = selectItems(recycleId);
        int bookKind = items.size();
        int totalQty = items.size();
        BigDecimal estimated = BigDecimal.ZERO;
        BigDecimal finalAmount = BigDecimal.ZERO;
        int mismatch = 0;
        for (RecycleItem item : items)
        {
            estimated = estimated.add(nz(item.getFinalPrice()));
            if (Integer.valueOf(RecycleItem.STATUS_ACCEPTED).equals(item.getItemStatus()))
            {
                finalAmount = finalAmount.add(nz(item.getFinalPrice()));
            }
            if (Integer.valueOf(1).equals(item.getIsMismatch()))
            {
                mismatch = 1;
            }
        }
        Recycle patch = new Recycle();
        patch.setRecycleId(recycleId);
        patch.setBookKind(bookKind);
        patch.setTotalQty(totalQty);
        patch.setEstimatedAmount(estimated);
        patch.setFinalAmount(finalAmount);
        patch.setHasMismatch(mismatch);
        patch.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(patch);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 明细插入（提交阶段：判定字段留空，预估价 = 基准价 × 提交成色系数） */
    private void insertItem(Recycle recycle, RecycleItem item, LocalDateTime now)
    {
        if (item.getIsbn() == null || item.getIsbn().isBlank())
        {
            throw BizErrors.badRequest("明细 ISBN 不能为空");
        }
        if (item.getTitle() == null || item.getTitle().isBlank())
        {
            throw BizErrors.badRequest("明细书名不能为空");
        }
        if (item.getSubmitGradeId() == null)
        {
            throw BizErrors.badRequest("明细缺少会员提交的成色等级");
        }
        ConditionGrade grade = gradeService.requireGrade(item.getSubmitGradeId(), null);
        if ("1".equals(grade.getStatus()))
        {
            throw BizErrors.ruleViolated("成色等级「" + grade.getGradeName()
                    + "」已停用，不能用于回收提交");
        }
        BigDecimal basePrice = nz(item.getBasePrice());
        if (basePrice.signum() < 0)
        {
            throw BizErrors.ruleViolated("基准价不能为负");
        }
        BigDecimal factor = grade.getPriceFactor() == null ? BigDecimal.ONE : grade.getPriceFactor();
        item.setItemId(null);
        item.setRecycleId(recycle.getRecycleId());
        item.setIsbn(item.getIsbn().trim());
        item.setTitle(item.getTitle().trim());
        item.setSubmitGradeName(grade.getGradeName());
        item.setJudgeGradeId(0L);
        item.setJudgeGradeName("");
        item.setIsMismatch(0);
        item.setJudgeNote("");
        item.setBasePrice(basePrice.setScale(2, RoundingMode.HALF_UP));
        item.setPriceFactor(factor);
        item.setFinalPrice(basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP));
        item.setItemStatus(RecycleItem.STATUS_PENDING);
        item.setRejectReason("");
        item.setUsedBookId(0L);
        item.setCreateTime(now);
        recycleItemMapper.insert(item);
    }

    private List<RecycleItem> selectItems(Long recycleId)
    {
        return recycleItemMapper.selectList(new LambdaQueryWrapper<RecycleItem>()
                .eq(RecycleItem::getRecycleId, recycleId)
                .orderByAsc(RecycleItem::getItemId));
    }

    /** 回收单号：RC + yyyyMMddHHmmss + 4 位流水 */
    private String generateNo()
    {
        Long maxId = baseMapper.selectList(new LambdaQueryWrapper<Recycle>()
                .select(Recycle::getRecycleId)
                .orderByDesc(Recycle::getRecycleId)
                .last("limit 1")).stream().map(Recycle::getRecycleId).findFirst().orElse(0L);
        long next = (maxId == null ? 0L : maxId) + 1;
        return RECYCLE_NO_PREFIX + LocalDateTime.now().format(NO_TIME_FORMAT)
                + String.format("%04d", next % 10000);
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
                return "待验收";
            case 2:
                return "验收中";
            case 3:
                return "已完成";
            case 4:
                return "已拒绝";
            case 5:
                return "已取消";
            default:
                return "未知";
        }
    }

    private BigDecimal nz(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int nzInt(Integer value)
    {
        return value == null ? 0 : value;
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
