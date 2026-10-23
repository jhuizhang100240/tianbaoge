package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.CouponBatchExecuteResult;
import com.tbgbook.bookstore.sale.domain.CouponGrantBatch;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.mapper.CouponGrantBatchMapper;
import com.tbgbook.bookstore.sale.mapper.CouponTemplateMapper;
import com.tbgbook.bookstore.sale.service.ICouponGrantBatchService;
import com.tbgbook.bookstore.sale.service.ICouponService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 优惠券发放批次 Service 实现（阶段四步骤②）。
 *
 * <p><b>圈选人群的三种方式</b>（{@code targetType}）：
 * <ol>
 *   <li>{@code 1按标签} —— {@code targetValue} 是标签ID列表，查 {@code biz_member_tag_rel}</li>
 *   <li>{@code 2按等级} —— {@code targetValue} 是等级ID列表，查 {@code biz_member}（只要正常状态）</li>
 *   <li>{@code 3手工导入名单} —— {@code targetValue} 直接就是会员ID列表</li>
 * </ol>
 * ⚠️ 前两种是**跨域读会员域的表**（属阶段五）—— 只读不写。圈选人群本就是发放批次的职责，
 * 表与种子数据已就绪，故直接读（与库存预警扫描读 {@code biz_stock_flow} 同一处置）。
 *
 * <p><b>执行是同步的</b>：契约写「发放是异步的」，但单店几千张券的插入是毫秒级，
 * 引入线程池的代价（异步线程拿不到登录上下文 → {@code grantBy} 会丢）大于收益。
 * 响应结构与契约一致，前端轮询 {@code actualQty} 照常可用。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class CouponGrantBatchServiceImpl extends ServiceImpl<CouponGrantBatchMapper, CouponGrantBatch>
        implements ICouponGrantBatchService
{
    /** 批次号前缀：{@code CB}（Coupon Batch）+ yyyyMMdd + 6 位流水。需求未定义，本次自定 */
    private static final String BATCH_NO_PREFIX = "CB";

    private final ReferenceGuard referenceGuard;

    private final CouponTemplateMapper templateMapper;

    private final ICouponService couponService;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<CouponGrantBatch> selectCouponGrantBatchList(CouponGrantBatch batch)
    {
        return baseMapper.selectCouponGrantBatchList(batch);
    }

    @Override
    public CouponGrantBatch selectCouponGrantBatchById(Long batchId)
    {
        return referenceGuard.requireExists(baseMapper, batchId, "优惠券发放批次");
    }

    // ==================================================================
    // 写
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addCouponGrantBatch(CouponGrantBatch batch)
    {
        if (batch.getTemplateId() == null)
        {
            throw BizErrors.badRequest("券模板不能为空");
        }
        if (batch.getTargetType() == null)
        {
            throw BizErrors.badRequest("目标人群类型不能为空");
        }
        requireTargetValue(batch);
        // 模板必须存在（未发布也允许先建批次，执行时才校验发布状态）
        referenceGuard.requireExists(templateMapper, batch.getTemplateId(), "优惠券模板");

        batch.setBatchId(null);
        batch.setBatchNo(null);
        batch.setStatus(CouponGrantBatch.STATUS_GRANTING);
        batch.setPlanQty(0);
        batch.setActualQty(0);
        batch.setReceivedQty(0);
        batch.setGrantBy(null);
        batch.setGrantTime(null);
        if (batch.getGrantType() == null)
        {
            batch.setGrantType(CouponGrantBatch.GRANT_DIRECTED);
        }

        Long predictedId = baseMapper.selectNextBatchId();
        batch.setBatchNo(buildBatchNo(predictedId == null ? 1L : predictedId));

        int rows = baseMapper.insert(batch);

        String actualNo = buildBatchNo(batch.getBatchId());
        if (!actualNo.equals(batch.getBatchNo()))
        {
            CouponGrantBatch patch = new CouponGrantBatch();
            patch.setBatchId(batch.getBatchId());
            patch.setBatchNo(actualNo);
            baseMapper.updateById(patch);
            batch.setBatchNo(actualNo);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCouponGrantBatch(CouponGrantBatch batch)
    {
        if (batch.getBatchId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的发放批次");
        }
        CouponGrantBatch current = referenceGuard.requireExists(baseMapper, batch.getBatchId(),
                "优惠券发放批次");
        // 已执行完成的批次不该再改人群 —— 那会让「发给了谁」与批次配置对不上
        if (Objects.equals(current.getStatus(), CouponGrantBatch.STATUS_DONE))
        {
            throw BizErrors.ruleViolated("批次「" + current.getBatchNo()
                    + "」已执行完成，不能再修改 —— 改了人群配置就与已发出的券对不上了");
        }
        if (batch.getTemplateId() != null)
        {
            referenceGuard.requireExists(templateMapper, batch.getTemplateId(), "优惠券模板");
        }
        if (batch.getTargetType() != null)
        {
            requireTargetValue(batch);
        }

        batch.setBatchNo(null);
        batch.setStatus(null);
        batch.setPlanQty(null);
        batch.setActualQty(null);
        batch.setReceivedQty(null);
        batch.setGrantBy(null);
        batch.setGrantTime(null);

        int rows = baseMapper.updateById(batch);
        referenceGuard.requireAffected(rows, "优惠券发放批次");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeCouponGrantBatches(Long[] batchIds)
    {
        List<Long> idList = (batchIds == null) ? Collections.emptyList() : Arrays.asList(batchIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "优惠券发放批次");

        for (Serializable id : ids)
        {
            CouponGrantBatch one = referenceGuard.requireExists(baseMapper, id, "优惠券发放批次");
            if (Objects.equals(one.getStatus(), CouponGrantBatch.STATUS_DONE))
            {
                throw BizErrors.ruleViolated("批次「" + one.getBatchNo()
                        + "」已执行完成，不能删除 —— 它记录着发给了谁，删了券就没了来源说明");
            }
        }
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 执行发放
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponBatchExecuteResult execute(Long batchId, Integer grantType)
    {
        CouponGrantBatch batch = referenceGuard.requireExists(baseMapper, batchId, "优惠券发放批次");
        // 契约：409 批次已执行
        if (!Objects.equals(batch.getStatus(), CouponGrantBatch.STATUS_GRANTING))
        {
            throw BizErrors.conflict("批次「" + batch.getBatchNo() + "」已执行过，不能重复发放");
        }

        CouponTemplate template = templateMapper.selectById(batch.getTemplateId());
        if (template == null || !Objects.equals(template.getStatus(), CouponTemplate.STATUS_PUBLISHED))
        {
            throw BizErrors.notFound("券模板不存在或未发布（模板ID=" + batch.getTemplateId() + "）");
        }

        List<Long> members = resolveTargetMembers(batch);
        // 契约：422 目标人群为空
        if (members.isEmpty())
        {
            throw BizErrors.ruleViolated("批次「" + batch.getBatchNo() + "」圈选到的会员为空，"
                    + "没有可发放的对象；请检查目标人群配置（"
                    + targetTypeName(batch.getTargetType()) + "：" + batch.getTargetValue() + "）");
        }

        int total = nzi(template.getTotalQty());
        int issued = nzi(template.getIssuedQty());
        // 契约：422 模板发放总量不足以覆盖计划人数
        if (total > 0 && issued + members.size() > total)
        {
            throw BizErrors.ruleViolated("模板「" + template.getCouponName() + "」的发放总量只剩 "
                    + Math.max(total - issued, 0) + " 张，不够本次计划发放的 " + members.size() + " 张");
        }

        int granted = 0;
        for (Long memberId : members)
        {
            couponService.issueCoupon(template, memberId, batchId);
            granted++;
        }

        LocalDateTime grantTime = LocalDateTime.now();
        String operator = SecurityUtils.getUsername();

        CouponGrantBatch patch = new CouponGrantBatch();
        patch.setBatchId(batchId);
        patch.setGrantType(grantType == null ? batch.getGrantType() : grantType);
        patch.setPlanQty(members.size());
        patch.setActualQty(granted);
        patch.setStatus(CouponGrantBatch.STATUS_DONE);
        patch.setGrantBy(operator);
        patch.setGrantTime(grantTime);
        referenceGuard.requireAffected(baseMapper.updateById(patch), "优惠券发放批次");

        CouponBatchExecuteResult result = new CouponBatchExecuteResult();
        result.setBatchId(batchId);
        result.setBatchNo(batch.getBatchNo());
        result.setPlanQty(members.size());
        result.setActualQty(granted);
        result.setStatus(CouponGrantBatch.STATUS_DONE);
        return result;
    }

    /**
     * 按 {@code targetType} 圈选会员（去重、保序）。
     *
     * <p>手工名单直接从 {@code targetValue} 解析；标签与等级走 Mapper 的跨域读。
     */
    private List<Long> resolveTargetMembers(CouponGrantBatch batch)
    {
        Set<Long> members = new LinkedHashSet<>();
        Integer targetType = batch.getTargetType();

        if (Objects.equals(targetType, CouponGrantBatch.TARGET_MANUAL))
        {
            // ⚠️ 名单是人工填的，必须**校验存在性**（见 Mapper 注释：不校验会发券给不存在的会员）
            List<Long> ids = parseIds(batch.getTargetValue());
            if (!ids.isEmpty())
            {
                members.addAll(baseMapper.selectExistingMemberIds(ids));
            }
        }
        else if (Objects.equals(targetType, CouponGrantBatch.TARGET_TAG))
        {
            List<Long> tagIds = parseIds(batch.getTargetValue());
            if (!tagIds.isEmpty())
            {
                members.addAll(baseMapper.selectMemberIdsByTagIds(tagIds));
            }
        }
        else if (Objects.equals(targetType, CouponGrantBatch.TARGET_LEVEL))
        {
            List<Long> levelIds = parseIds(batch.getTargetValue());
            if (!levelIds.isEmpty())
            {
                members.addAll(baseMapper.selectMemberIdsByLevelIds(levelIds));
            }
        }
        return new ArrayList<>(members);
    }

    /** 把「逗号分隔的ID串」解析成 ID 列表；空串或非法片段一律丢弃（不让一个笔误炸掉整批） */
    private List<Long> parseIds(String raw)
    {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank())
        {
            return ids;
        }
        for (String part : raw.split(","))
        {
            String s = part.trim();
            if (s.isEmpty())
            {
                continue;
            }
            try
            {
                ids.add(Long.valueOf(s));
            }
            catch (NumberFormatException ignored)
            {
                // 非法片段丢弃：目标人群值由人手工填，容忍笔误比整批失败更合理
            }
        }
        return ids;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 按标签 / 等级 / 手工名单圈人时都必须给出目标值 */
    private void requireTargetValue(CouponGrantBatch batch)
    {
        Integer targetType = batch.getTargetType();
        if (targetType == null)
        {
            return;
        }
        if (batch.getTargetValue() == null || batch.getTargetValue().isBlank())
        {
            throw BizErrors.ruleViolated("目标人群类型选了「" + targetTypeName(targetType)
                    + "」，但目标人群值是空的 —— 这样的批次圈不到任何人");
        }
    }

    private String targetTypeName(Integer targetType)
    {
        if (targetType == null)
        {
            return "未知";
        }
        return switch (targetType)
        {
            case CouponGrantBatch.TARGET_TAG -> "按标签";
            case CouponGrantBatch.TARGET_LEVEL -> "按等级";
            case CouponGrantBatch.TARGET_MANUAL -> "手工导入名单";
            default -> "未知";
        };
    }

    /** 生成批次号：{@code CB} + yyyyMMdd + 6 位流水（需求 §3.2 未定义，本次自定） */
    private String buildBatchNo(Long batchId)
    {
        return BATCH_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", batchId);
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
