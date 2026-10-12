package com.tbgbook.bookstore.usedbook.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;
import com.tbgbook.bookstore.usedbook.domain.Recycle;
import com.tbgbook.bookstore.usedbook.domain.RecycleItem;
import com.tbgbook.bookstore.usedbook.mapper.RecycleItemMapper;
import com.tbgbook.bookstore.usedbook.mapper.RecycleMapper;
import com.tbgbook.bookstore.usedbook.service.IUsedBookGradeService;
import com.tbgbook.bookstore.usedbook.service.IUsedBookRecycleItemService;
import com.tbgbook.bookstore.usedbook.service.IUsedBookRecycleService;

import lombok.RequiredArgsConstructor;

/**
 * 二手书回收明细 Service 实现（阶段六步骤②，页面 A-41）。
 *
 * <p><b>自定口径（登记 A45）</b>：
 * <ol>
 *   <li>明细只在**单据处于 1待验收**时允许增删改；验收后（状态 2/3/4/5）一律 422；</li>
 *   <li>提交时校验 {@code isbn} / {@code title} / {@code submitGradeId}；成色档必须存在且启用（404 / 422）；</li>
 *   <li>{@code basePrice} 提交时可暂缺（允许 0），**验收时**若仍缺失则 422「基准价缺失」；</li>
 *   <li>提交阶段 {@code priceFactor} 取会员提交成色档系数、{@code finalPrice = basePrice × 系数}
 *       （仅作预估展示），验收后再按店员判定成色重算；</li>
 *   <li>每次增删改后重算单据头汇总（品种数 / 总册数 / 预估金额）。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class UsedBookRecycleItemServiceImpl extends ServiceImpl<RecycleItemMapper, RecycleItem>
        implements IUsedBookRecycleItemService
{
    private final ReferenceGuard referenceGuard;

    private final RecycleMapper recycleMapper;

    private final IUsedBookGradeService gradeService;

    private final IUsedBookRecycleService recycleService;

    @Override
    public List<RecycleItem> selectRecycleItemList(RecycleItem item)
    {
        return baseMapper.selectRecycleItemList(item);
    }

    @Override
    public RecycleItem selectRecycleItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "回收明细");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addRecycleItem(RecycleItem item)
    {
        if (item.getRecycleId() == null)
        {
            throw BizErrors.badRequest("回收单ID不能为空");
        }
        Recycle recycle = referenceGuard.requireExists(recycleMapper, item.getRecycleId(), "回收单");
        requirePending(recycle);
        prepare(item, recycle);
        item.setItemId(null);
        item.setIsMismatch(0);
        item.setJudgeGradeId(0L);
        item.setJudgeGradeName("");
        item.setJudgeNote("");
        item.setItemStatus(RecycleItem.STATUS_PENDING);
        item.setRejectReason("");
        item.setUsedBookId(0L);
        item.setCreateTime(LocalDateTime.now());
        int rows = baseMapper.insert(item);
        recycleService.recalcAggregates(item.getRecycleId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRecycleItem(RecycleItem item)
    {
        if (item.getItemId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的回收明细");
        }
        RecycleItem existing = referenceGuard.requireExists(baseMapper, item.getItemId(), "回收明细");
        Recycle recycle = referenceGuard.requireExists(recycleMapper, existing.getRecycleId(), "回收单");
        requirePending(recycle);
        if (!Integer.valueOf(RecycleItem.STATUS_PENDING).equals(existing.getItemStatus()))
        {
            throw BizErrors.ruleViolated("该明细已验收（itemStatus=" + existing.getItemStatus()
                    + "），不能修改");
        }
        item.setRecycleId(existing.getRecycleId());
        if (item.getIsbn() == null)
        {
            item.setIsbn(existing.getIsbn());
        }
        if (item.getTitle() == null)
        {
            item.setTitle(existing.getTitle());
        }
        if (item.getSubmitGradeId() == null)
        {
            item.setSubmitGradeId(existing.getSubmitGradeId());
        }
        if (item.getBasePrice() == null)
        {
            item.setBasePrice(existing.getBasePrice());
        }
        prepare(item, recycle);
        // 判定字段与状态只由验收动作写入
        item.setJudgeGradeId(existing.getJudgeGradeId());
        item.setJudgeGradeName(existing.getJudgeGradeName());
        item.setIsMismatch(existing.getIsMismatch());
        item.setJudgeNote(existing.getJudgeNote());
        item.setItemStatus(existing.getItemStatus());
        item.setRejectReason(existing.getRejectReason());
        item.setUsedBookId(existing.getUsedBookId());
        item.setCreateTime(existing.getCreateTime());
        int rows = baseMapper.updateById(item);
        recycleService.recalcAggregates(item.getRecycleId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeRecycleItems(Long[] itemIds)
    {
        List<Long> idList = (itemIds == null) ? Collections.emptyList() : Arrays.asList(itemIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "回收明细");
        Long recycleId = null;
        for (Serializable id : ids)
        {
            RecycleItem row = referenceGuard.requireExists(baseMapper, id, "回收明细");
            Recycle recycle = referenceGuard.requireExists(recycleMapper, row.getRecycleId(), "回收单");
            requirePending(recycle);
            if (!Integer.valueOf(RecycleItem.STATUS_PENDING).equals(row.getItemStatus()))
            {
                throw BizErrors.ruleViolated("明细已验收，不能删除（itemId=" + id + "）");
            }
            recycleId = row.getRecycleId();
        }
        int rows = baseMapper.deleteByIds(ids);
        if (recycleId != null)
        {
            recycleService.recalcAggregates(recycleId);
        }
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void requirePending(Recycle recycle)
    {
        if (!Integer.valueOf(Recycle.STATUS_PENDING).equals(recycle.getStatus()))
        {
            throw BizErrors.ruleViolated("回收单当前为「" + statusName(recycle.getStatus())
                    + "」，只有「待验收」的回收单可以增删改明细");
        }
    }

    /** 字段规范化 + 成色系数与预估回收价计算 */
    private void prepare(RecycleItem item, Recycle recycle)
    {
        if (item.getIsbn() == null || item.getIsbn().isBlank())
        {
            throw BizErrors.badRequest("ISBN 不能为空");
        }
        if (item.getTitle() == null || item.getTitle().isBlank())
        {
            throw BizErrors.badRequest("书名不能为空");
        }
        if (item.getSubmitGradeId() == null)
        {
            throw BizErrors.badRequest("会员提交的成色等级不能为空");
        }
        ConditionGrade grade = gradeService.requireGrade(item.getSubmitGradeId(), null);
        if ("1".equals(grade.getStatus()))
        {
            throw BizErrors.ruleViolated("成色等级「" + grade.getGradeName()
                    + "」已停用，不能用于回收提交");
        }
        item.setIsbn(item.getIsbn().trim());
        item.setTitle(item.getTitle().trim());
        item.setSubmitGradeName(grade.getGradeName());
        BigDecimal basePrice = item.getBasePrice() == null
                ? BigDecimal.ZERO : item.getBasePrice();
        if (basePrice.signum() < 0)
        {
            throw BizErrors.ruleViolated("基准价不能为负");
        }
        item.setBasePrice(basePrice.setScale(2, RoundingMode.HALF_UP));
        BigDecimal factor = grade.getPriceFactor() == null
                ? BigDecimal.ONE : grade.getPriceFactor();
        item.setPriceFactor(factor);
        item.setFinalPrice(basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP));
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
}
