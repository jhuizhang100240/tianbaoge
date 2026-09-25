package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.mapper.CouponTemplateMapper;
import com.tbgbook.bookstore.sale.service.ICouponTemplateService;

import lombok.RequiredArgsConstructor;

/**
 * 优惠券模板 Service 实现（阶段四步骤②）。
 *
 * <p>三条校验值得说明：
 * <ol>
 *   <li><b>门槛须 ≥ 面额</b>（满减券）—— 否则是「满 10 减 20」这种亏本券。
 *       这是两个字段的组合关系，字段注解办不到，故在 service 里判</li>
 *   <li><b>有效期参数按类型校验</b>：固定日期型必须有 {@code validStart}/{@code validEnd}，
 *       相对型必须有 {@code validDays > 0}。缺了的话发出的券没有有效期，
 *       核销时无从判断过期 —— 属必须拦的录入遗漏</li>
 *   <li><b>被券实例引用时禁止删除</b>：与分类/出版社同一条规则，只能停用。
 *       删了会让已发出的券失去来源说明（券上只有快照的面额门槛，没有模板就说不清来历）</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class CouponTemplateServiceImpl extends ServiceImpl<CouponTemplateMapper, CouponTemplate>
        implements ICouponTemplateService
{
    /** 模板编号前缀：{@code CT}（Coupon Template）+ yyyyMMdd + 6 位流水。需求未定义，本次自定 */
    private static final String TEMPLATE_NO_PREFIX = "CT";

    private final ReferenceGuard referenceGuard;

    @Override
    public List<CouponTemplate> selectCouponTemplateList(CouponTemplate template)
    {
        return baseMapper.selectCouponTemplateList(template);
    }

    @Override
    public CouponTemplate selectCouponTemplateById(Long templateId)
    {
        return referenceGuard.requireExists(baseMapper, templateId, "优惠券模板");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addCouponTemplate(CouponTemplate template)
    {
        if (template.getCouponName() == null || template.getCouponName().isBlank())
        {
            throw BizErrors.badRequest("券名称不能为空");
        }
        if (template.getCouponType() == null)
        {
            throw BizErrors.badRequest("券类型不能为空");
        }
        validate(template);

        // 服务端接管字段
        template.setTemplateId(null);
        template.setTemplateNo(null);
        template.setStatus(CouponTemplate.STATUS_DRAFT);
        template.setIssuedQty(0);
        template.setReceivedQty(0);
        template.setUsedQty(0);
        if (template.getTotalQty() == null)
        {
            template.setTotalQty(0);
        }
        if (template.getPerLimit() == null)
        {
            template.setPerLimit(1);
        }
        if (template.getScopeType() == null)
        {
            template.setScopeType(CouponTemplate.SCOPE_ALL);
        }
        if (template.getStackable() == null)
        {
            template.setStackable(0);
        }

        Long predictedId = baseMapper.selectNextTemplateId();
        template.setTemplateNo(buildTemplateNo(predictedId == null ? 1L : predictedId));

        int rows = baseMapper.insert(template);

        String actualNo = buildTemplateNo(template.getTemplateId());
        if (!actualNo.equals(template.getTemplateNo()))
        {
            CouponTemplate patch = new CouponTemplate();
            patch.setTemplateId(template.getTemplateId());
            patch.setTemplateNo(actualNo);
            baseMapper.updateById(patch);
            template.setTemplateNo(actualNo);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCouponTemplate(CouponTemplate template)
    {
        if (template.getTemplateId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的优惠券模板");
        }
        CouponTemplate current = referenceGuard.requireExists(baseMapper, template.getTemplateId(),
                "优惠券模板");
        validate(template);

        // 编号、状态与统计四列都不接受客户端写入
        template.setTemplateNo(null);
        template.setStatus(null);
        template.setIssuedQty(null);
        template.setReceivedQty(null);
        template.setUsedQty(null);

        int rows = baseMapper.updateById(template);
        referenceGuard.requireAffected(rows, "优惠券模板");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeCouponTemplates(Long[] templateIds)
    {
        List<Long> idList = (templateIds == null) ? Collections.emptyList() : Arrays.asList(templateIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "优惠券模板");

        for (Serializable id : ids)
        {
            referenceGuard.requireExists(baseMapper, id, "优惠券模板");
            // 删除保护：已被券实例引用时只能停用（biz_coupon 没有 del_flag，故传 null）
            referenceGuard.requireNoReference("biz_coupon", "template_id", id, null,
                    "券实例", "优惠券模板", "改为停用");
        }
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 模板的三类校验（新增与修改共用）。
     *
     * <p>⚠️ 只校验**本次提交里给了的字段**：PUT 允许只改一个字段（如只改券名），
     * 此时 {@code faceValue} 等为 null，不能因为「null 不满足门槛≥面额」而拒绝 ——
     * 故判空前先跳过（缺的字段沿用库里的值）。
     */
    private void validate(CouponTemplate template)
    {
        // ① 门槛须 ≥ 面额（满减券）：两个字段都给了才判
        BigDecimal face = template.getFaceValue();
        BigDecimal threshold = template.getThresholdAmount();
        if (face != null && threshold != null && threshold.compareTo(face) < 0)
        {
            throw BizErrors.ruleViolated("使用门槛（" + threshold + "）不能小于券面额（" + face
                    + "）—— 那会变成「满 " + threshold + " 减 " + face + "」的亏本券");
        }

        // ② 有效期参数按类型校验
        Integer validityType = template.getValidityType();
        if (Objects.equals(validityType, CouponTemplate.VALIDITY_FIXED))
        {
            if (template.getValidStart() != null && template.getValidEnd() != null
                    && !template.getValidEnd().isAfter(template.getValidStart()))
            {
                throw BizErrors.ruleViolated("失效时间必须晚于生效时间");
            }
        }
        else if (Objects.equals(validityType, CouponTemplate.VALIDITY_RELATIVE))
        {
            if (template.getValidDays() != null && template.getValidDays() <= 0)
            {
                throw BizErrors.ruleViolated("「领取后 N 天」模式下，有效天数必须大于 0");
            }
        }

        // ③ 指定范围必须给范围值
        Integer scopeType = template.getScopeType();
        if (scopeType != null && !Objects.equals(scopeType, CouponTemplate.SCOPE_ALL)
                && template.getScopeValue() != null && template.getScopeValue().isBlank())
        {
            throw BizErrors.ruleViolated("适用范围选了「"
                    + (Objects.equals(scopeType, 2) ? "指定分类" : "指定图书")
                    + "」，但范围值是空的 —— 这样的券对任何商品都用不了");
        }
    }

    /** 生成模板编号：{@code CT} + yyyyMMdd + 6 位流水（需求 §3.2 未定义券模板编号，本次自定） */
    private String buildTemplateNo(Long templateId)
    {
        return TEMPLATE_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", templateId);
    }
}
