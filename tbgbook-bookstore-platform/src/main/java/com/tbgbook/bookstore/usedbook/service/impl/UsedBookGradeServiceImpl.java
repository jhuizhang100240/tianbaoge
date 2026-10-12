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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;
import com.tbgbook.bookstore.usedbook.domain.PricePreviewRequest;
import com.tbgbook.bookstore.usedbook.domain.PricePreviewResult;
import com.tbgbook.bookstore.usedbook.mapper.ConditionGradeMapper;
import com.tbgbook.bookstore.usedbook.service.IUsedBookGradeService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 二手书成色等级 Service 实现（阶段六第一批，页面 A-42）。
 *
 * <p><b>自定口径（登记 A44）</b>：
 * <ol>
 *   <li><b>gradeCode 同店唯一</b>（预检 409）；{@code priceFactor} 取值 0~1；</li>
 *   <li><b>删除保护</b>：被 {@code biz_recycle_item}（会员提交档 / 店员判定档）或
 *       {@code biz_used_book.condition_grade_id} 引用时禁止删除（422，建议改为停用）；</li>
 *   <li><b>停用档位</b>：不允许新增回收明细时选择（校验在第二批的回收明细服务里），
 *       但**存量数据仍可正常查询与试算**；</li>
 *   <li><b>试算口径</b>：回收价 = 基准价 × 系数（四舍五入到分），响应回计算式说明
 *       「基准价 ¥50.00 × 系数 0.90 = ¥45.00」；{@code basePrice} 必须 &gt; 0（422）；</li>
 *   <li><b>档位定位</b>：{@code gradeId} / {@code gradeCode} 至少给一个，都不给 400；
 *       给了但查不到 404。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class UsedBookGradeServiceImpl extends ServiceImpl<ConditionGradeMapper, ConditionGrade>
        implements IUsedBookGradeService
{
    /** 单店部署固定门店ID */
    private static final long DEFAULT_STORE_ID = 1L;

    /** 状态：0正常 */
    private static final String STATUS_NORMAL = "0";

    private final ReferenceGuard referenceGuard;

    @Override
    public List<ConditionGrade> selectConditionGradeList(ConditionGrade grade)
    {
        return baseMapper.selectConditionGradeList(grade);
    }

    @Override
    public ConditionGrade selectConditionGradeById(Long gradeId)
    {
        return referenceGuard.requireExists(baseMapper, gradeId, "二手书成色等级");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addConditionGrade(ConditionGrade grade)
    {
        grade.setGradeId(null);
        if (grade.getStoreId() == null)
        {
            grade.setStoreId(DEFAULT_STORE_ID);
        }
        normalize(grade);
        requireCodeUnique(grade.getGradeCode(), null);
        LocalDateTime now = LocalDateTime.now();
        grade.setCreateBy(currentUsername());
        grade.setCreateTime(now);
        grade.setUpdateBy(currentUsername());
        grade.setUpdateTime(now);
        return baseMapper.insert(grade);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateConditionGrade(ConditionGrade grade)
    {
        if (grade.getGradeId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的二手书成色等级");
        }
        ConditionGrade existing = referenceGuard.requireExists(baseMapper, grade.getGradeId(),
                "二手书成色等级");
        fillMissing(grade, existing);
        normalize(grade);
        requireCodeUnique(grade.getGradeCode(), grade.getGradeId());
        grade.setStoreId(existing.getStoreId());
        grade.setCreateBy(existing.getCreateBy());
        grade.setCreateTime(existing.getCreateTime());
        grade.setUpdateBy(currentUsername());
        grade.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(grade);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeConditionGrades(Long[] gradeIds)
    {
        List<Long> idList = (gradeIds == null) ? Collections.emptyList() : Arrays.asList(gradeIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "二手书成色等级");
        for (Serializable id : ids)
        {
            referenceGuard.requireExists(baseMapper, id, "二手书成色等级");
            referenceGuard.requireNoReference("biz_recycle_item", "submit_grade_id", id, null,
                    "回收明细（会员提交档）", "成色等级", "改为停用");
            referenceGuard.requireNoReference("biz_recycle_item", "judge_grade_id", id, null,
                    "回收明细（店员判定档）", "成色等级", "改为停用");
            referenceGuard.requireNoReference("biz_used_book", "condition_grade_id", id, null,
                    "二手书副本", "成色等级", "改为停用");
        }
        // 逻辑删除（@TableLogic）
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PricePreviewResult pricePreview(PricePreviewRequest request)
    {
        if (request == null || request.getBasePrice() == null)
        {
            throw BizErrors.badRequest("基准价不能为空");
        }
        BigDecimal basePrice = request.getBasePrice();
        if (basePrice.signum() <= 0)
        {
            throw BizErrors.ruleViolated("基准价必须大于 0");
        }
        if (request.getGradeId() == null
                && (request.getGradeCode() == null || request.getGradeCode().isBlank()))
        {
            throw BizErrors.badRequest("gradeId 与 gradeCode 至少给一个");
        }
        ConditionGrade grade = requireGrade(request.getGradeId(), request.getGradeCode());
        BigDecimal factor = nzd(grade.getPriceFactor());
        BigDecimal finalPrice = basePrice.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        PricePreviewResult result = new PricePreviewResult();
        result.setGradeName(grade.getGradeName());
        result.setPriceFactor(factor);
        result.setFinalPrice(finalPrice);
        result.setFormula("基准价 ¥" + basePrice.setScale(2, RoundingMode.HALF_UP)
                + " × 系数 " + factor + " = ¥" + finalPrice);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConditionGrade requireGrade(Long gradeId, String gradeCode)
    {
        if (gradeId == null && (gradeCode == null || gradeCode.isBlank()))
        {
            throw BizErrors.badRequest("成色等级未指定（gradeId 与 gradeCode 至少给一个）");
        }
        ConditionGrade grade;
        if (gradeId != null)
        {
            grade = baseMapper.selectById(gradeId);
        }
        else
        {
            grade = baseMapper.selectOne(new LambdaQueryWrapper<ConditionGrade>()
                    .eq(ConditionGrade::getGradeCode, gradeCode.trim())
                    .last("limit 1"));
        }
        if (grade == null)
        {
            throw BizErrors.notFound("二手书成色等级",
                    gradeId != null ? gradeId : gradeCode);
        }
        return grade;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void requireCodeUnique(String gradeCode, Long excludeId)
    {
        LambdaQueryWrapper<ConditionGrade> wrapper = new LambdaQueryWrapper<ConditionGrade>()
                .eq(ConditionGrade::getGradeCode, gradeCode);
        if (excludeId != null)
        {
            wrapper.ne(ConditionGrade::getGradeId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0)
        {
            throw BizErrors.duplicate("二手书成色等级", "成色编码", gradeCode);
        }
    }

    private void normalize(ConditionGrade grade)
    {
        if (grade.getGradeCode() == null || grade.getGradeCode().isBlank())
        {
            throw BizErrors.badRequest("成色编码不能为空");
        }
        grade.setGradeCode(grade.getGradeCode().trim());
        if (grade.getGradeName() == null || grade.getGradeName().isBlank())
        {
            throw BizErrors.badRequest("成色名称不能为空");
        }
        grade.setGradeName(grade.getGradeName().trim());
        if (grade.getPriceFactor() == null)
        {
            grade.setPriceFactor(BigDecimal.ONE);
        }
        if (grade.getPriceFactor().signum() < 0
                || grade.getPriceFactor().compareTo(BigDecimal.ONE) > 0)
        {
            throw BizErrors.ruleViolated("定价系数取值必须在 0~1 之间");
        }
        if (grade.getOrderNum() == null)
        {
            grade.setOrderNum(0);
        }
        if (grade.getStatus() == null)
        {
            grade.setStatus(STATUS_NORMAL);
        }
        if (grade.getGradeDesc() == null)
        {
            grade.setGradeDesc("");
        }
        if (grade.getRemark() == null)
        {
            grade.setRemark("");
        }
    }

    private void fillMissing(ConditionGrade target, ConditionGrade existing)
    {
        if (target.getGradeCode() == null)
        {
            target.setGradeCode(existing.getGradeCode());
        }
        if (target.getGradeName() == null)
        {
            target.setGradeName(existing.getGradeName());
        }
        if (target.getPriceFactor() == null)
        {
            target.setPriceFactor(existing.getPriceFactor());
        }
        if (target.getGradeDesc() == null)
        {
            target.setGradeDesc(existing.getGradeDesc());
        }
        if (target.getOrderNum() == null)
        {
            target.setOrderNum(existing.getOrderNum());
        }
        if (target.getStatus() == null)
        {
            target.setStatus(existing.getStatus());
        }
        if (target.getRemark() == null)
        {
            target.setRemark(existing.getRemark());
        }
    }

    private BigDecimal nzd(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
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
