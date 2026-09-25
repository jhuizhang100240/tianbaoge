package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.CouponGrantRequest;
import com.tbgbook.bookstore.sale.domain.CouponGrantResult;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.domain.CouponVerifyResult;
import com.tbgbook.bookstore.sale.mapper.CouponMapper;
import com.tbgbook.bookstore.sale.mapper.CouponTemplateMapper;
import com.tbgbook.bookstore.sale.service.ICouponService;

import lombok.RequiredArgsConstructor;

/**
 * 会员优惠券实例 Service 实现（阶段四步骤②）。
 *
 * <p><b>发放的共用原语是 {@link #issueCoupon}</b>：人工补发与批次发放都走它。
 * 一旦「快照哪几个字段、有效期怎么算」在两个调用方分叉，同一批券就会出现两种口径 ——
 * 而券是要拿去抵扣真金白银的。
 *
 * <p><b>过期的「惰性判定」做在读取路径上</b>（契约明写「由定时任务或查询时惰性判定」）：
 * <ul>
 *   <li>详情：只刷**这一行**（读到已过期但状态仍是未使用就顺手置 3）</li>
 *   <li>列表：只按 {@code memberId} 刷该会员的；**不做全店刷** ——
 *       每次列表都跑一遍全表 UPDATE 得不偿失，全店的过期由阶段十的定时任务负责</li>
 * </ul>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService
{
    /** 券码前缀：{@code CP} + yyyyMMdd + 6 位流水（需求 §3.2 未定义券码，本次自定） */
    private static final String COUPON_CODE_PREFIX = "CP";

    private final ReferenceGuard referenceGuard;

    private final CouponTemplateMapper templateMapper;

    // ==================================================================
    // 读（含惰性过期）
    // ==================================================================

    @Override
    public List<Coupon> selectCouponList(Coupon coupon)
    {
        // 按会员查时顺手刷该会员的过期状态；不按会员查时不刷（见类注释）
        if (coupon.getMemberId() != null)
        {
            baseMapper.expireOutdated(coupon.getMemberId());
        }
        return baseMapper.selectCouponList(coupon);
    }

    @Override
    public Coupon selectCouponById(Long couponId)
    {
        Coupon coupon = referenceGuard.requireExists(baseMapper, couponId, "优惠券");
        expireIfOutdated(coupon);
        return coupon;
    }

    /** 单行惰性过期：已过失效时间但状态仍是「未使用」时置为「已过期」 */
    private void expireIfOutdated(Coupon coupon)
    {
        if (!Objects.equals(coupon.getStatus(), Coupon.STATUS_UNUSED) || coupon.getValidEnd() == null)
        {
            return;
        }
        if (coupon.getValidEnd().isBefore(LocalDateTime.now()))
        {
            Coupon patch = new Coupon();
            patch.setCouponId(coupon.getCouponId());
            patch.setStatus(Coupon.STATUS_EXPIRED);
            baseMapper.updateById(patch);
            coupon.setStatus(Coupon.STATUS_EXPIRED);
        }
    }

    // ==================================================================
    // 写：后台补录
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addCoupon(Coupon coupon)
    {
        if (coupon.getTemplateId() == null)
        {
            throw BizErrors.badRequest("券模板不能为空");
        }
        if (coupon.getMemberId() == null)
        {
            throw BizErrors.badRequest("持有会员不能为空");
        }
        CouponTemplate template = referenceGuard.requireExists(templateMapper, coupon.getTemplateId(),
                "优惠券模板");

        Coupon fresh = buildCoupon(template, coupon.getMemberId(), 0L);
        // 补录可指定发放批次（如事后把一批券归到某批次下）
        fresh.setGrantBatchId(coupon.getGrantBatchId());
        return baseMapper.insert(fresh);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCoupon(Coupon coupon)
    {
        if (coupon.getCouponId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的优惠券");
        }
        Coupon current = referenceGuard.requireExists(baseMapper, coupon.getCouponId(), "优惠券");
        if (!Objects.equals(current.getStatus(), Coupon.STATUS_UNUSED))
        {
            throw BizErrors.ruleViolated("该券已"
                    + (Objects.equals(current.getStatus(), Coupon.STATUS_USED) ? "使用" : "过期")
                    + "，不能修改 —— 已使用的券是订单抵扣的凭据，改动会让账对不上");
        }

        // 快照字段与状态、核销字段都不接受写入：只能改「持有人 / 归属批次 / 领取时间」这类元数据
        coupon.setCouponCode(null);
        coupon.setTemplateId(null);
        coupon.setCouponName(null);
        coupon.setFaceValue(null);
        coupon.setThresholdAmount(null);
        coupon.setValidStart(null);
        coupon.setValidEnd(null);
        coupon.setStatus(null);
        coupon.setUseOrderId(null);
        coupon.setUseTime(null);

        int rows = baseMapper.updateById(coupon);
        referenceGuard.requireAffected(rows, "优惠券");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeCoupons(Long[] couponIds)
    {
        List<Long> idList = (couponIds == null) ? Collections.emptyList() : Arrays.asList(couponIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "优惠券");

        for (Serializable id : ids)
        {
            Coupon one = referenceGuard.requireExists(baseMapper, id, "优惠券");
            if (!Objects.equals(one.getStatus(), Coupon.STATUS_UNUSED))
            {
                throw BizErrors.ruleViolated("券「" + one.getCouponCode() + "」已"
                        + (Objects.equals(one.getStatus(), Coupon.STATUS_USED) ? "使用" : "过期")
                        + "，不能删除 —— 已使用的券是订单抵扣的凭据");
            }
        }
        // 本表无 del_flag，删行即物理删（未使用的券没有财务含义）
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 写：发放
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long issueCoupon(CouponTemplate template, Long memberId, Long batchId)
    {
        Coupon fresh = buildCoupon(template, memberId, batchId);
        baseMapper.insert(fresh);

        String actualCode = buildCouponCode(fresh.getCouponId());
        if (!actualCode.equals(fresh.getCouponCode()))
        {
            Coupon patch = new Coupon();
            patch.setCouponId(fresh.getCouponId());
            patch.setCouponCode(actualCode);
            baseMapper.updateById(patch);
            fresh.setCouponCode(actualCode);
        }

        // 模板的「已发放数量」累加
        CouponTemplate patch = new CouponTemplate();
        patch.setTemplateId(template.getTemplateId());
        patch.setIssuedQty(nzi(template.getIssuedQty()) + 1);
        templateMapper.updateById(patch);
        template.setIssuedQty(nzi(template.getIssuedQty()) + 1);

        return fresh.getCouponId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponGrantResult grant(CouponGrantRequest request)
    {
        CouponTemplate template = templateMapper.selectById(request.getTemplateId());
        // 契约：404 模板不存在或已停用 —— 未发布(1草稿)/已停用(3) 都不许发券
        if (template == null || !Objects.equals(template.getStatus(), CouponTemplate.STATUS_PUBLISHED))
        {
            throw BizErrors.notFound("券模板不存在或未发布（模板ID=" + request.getTemplateId() + "）");
        }

        int total = nzi(template.getTotalQty());
        int issued = nzi(template.getIssuedQty());
        int perLimit = nzi(template.getPerLimit());
        int wanted = request.getMemberIds().size();

        // 契约：422 模板发放总量已用尽（总量为 0 视为不限量，故只在 total > 0 时判）
        if (total > 0 && issued + wanted > total)
        {
            throw BizErrors.ruleViolated("模板「" + template.getCouponName() + "」的发放总量只剩 "
                    + Math.max(total - issued, 0) + " 张，不够本次补发的 " + wanted + " 张");
        }

        List<CouponGrantResult.FailDetail> fails = new ArrayList<>();
        int granted = 0;
        for (Long memberId : request.getMemberIds())
        {
            // ⚠️ 每人限领：**逐人失败、不整体回滚**（契约错误码原文）
            if (perLimit > 0
                    && baseMapper.countUsableByMemberAndTemplate(memberId, template.getTemplateId()) >= perLimit)
            {
                CouponGrantResult.FailDetail fail = new CouponGrantResult.FailDetail();
                fail.setMemberId(memberId);
                fail.setReason("已达每人限领 " + perLimit + " 张");
                fails.add(fail);
                continue;
            }
            issueCoupon(template, memberId, 0L);
            granted++;
        }

        CouponGrantResult result = new CouponGrantResult();
        result.setGranted(granted);
        result.setFailed(fails.size());
        result.setFailDetails(fails);
        return result;
    }

    // ==================================================================
    // 校验
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public CouponVerifyResult verify(Long couponId, Long memberId, BigDecimal amount)
    {
        Coupon coupon = referenceGuard.requireExists(baseMapper, couponId, "优惠券");
        expireIfOutdated(coupon);

        CouponVerifyResult result = new CouponVerifyResult();
        result.setUsable(true);

        if (!Objects.equals(coupon.getMemberId(), memberId))
        {
            result.setUsable(false);
            result.setReason("该券不属于当前会员");
        }
        else if (Objects.equals(coupon.getStatus(), Coupon.STATUS_USED))
        {
            result.setUsable(false);
            result.setReason("该券已使用");
        }
        else if (Objects.equals(coupon.getStatus(), Coupon.STATUS_EXPIRED))
        {
            result.setUsable(false);
            result.setReason("该券已过期");
        }
        else if (coupon.getValidStart() != null && coupon.getValidStart().isAfter(LocalDateTime.now()))
        {
            result.setUsable(false);
            result.setReason("该券尚未到生效时间");
        }
        else if (amount != null && coupon.getThresholdAmount() != null
                && amount.compareTo(coupon.getThresholdAmount()) < 0)
        {
            result.setUsable(false);
            result.setReason("商品金额 " + amount + " 未达使用门槛 " + coupon.getThresholdAmount());
        }
        return result;
    }

    // ==================================================================
    // 内部：构造一张券（快照模板的面额/门槛/有效期）
    // ==================================================================

    private Coupon buildCoupon(CouponTemplate template, Long memberId, Long batchId)
    {
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon = new Coupon();
        coupon.setTemplateId(template.getTemplateId());
        coupon.setMemberId(memberId);
        // 券名、面额、门槛都是**发放时快照**：模板后续改名改值不影响已发券
        coupon.setCouponName(template.getCouponName());
        coupon.setFaceValue(template.getFaceValue());
        coupon.setThresholdAmount(template.getThresholdAmount());

        // 有效期按模板的模式算：固定型抄模板，相对型 = 领取时间 + N 天
        if (Objects.equals(template.getValidityType(), CouponTemplate.VALIDITY_RELATIVE))
        {
            coupon.setValidStart(now);
            coupon.setValidEnd(now.plusDays(nzi(template.getValidDays())));
        }
        else
        {
            coupon.setValidStart(template.getValidStart());
            coupon.setValidEnd(template.getValidEnd());
        }

        coupon.setStatus(Coupon.STATUS_UNUSED);
        coupon.setGrantBatchId(batchId == null ? 0L : batchId);
        coupon.setReceiveTime(now);
        coupon.setUseOrderId(0L);
        // coupon_code 是 NOT NULL + 唯一索引，插入前必须先给出值（插入后按真实主键校正）
        Long predictedId = baseMapper.selectNextCouponId();
        coupon.setCouponCode(buildCouponCode(predictedId == null ? 1L : predictedId));
        return coupon;
    }

    /** 生成券码：{@code CP} + yyyyMMdd + 6 位流水 */
    private String buildCouponCode(Long couponId)
    {
        return COUPON_CODE_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", couponId);
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
