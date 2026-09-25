package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
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
import com.tbgbook.bookstore.sale.domain.Promotion;
import com.tbgbook.bookstore.sale.domain.PromotionStatusResult;
import com.tbgbook.bookstore.sale.mapper.PromotionBookMapper;
import com.tbgbook.bookstore.sale.mapper.PromotionMapper;
import com.tbgbook.bookstore.sale.service.IPromotionService;

import lombok.RequiredArgsConstructor;

/**
 * 促销活动 Service 实现（阶段四步骤①，页面 A-32~A-34）。
 *
 * <p>三处值得说明的取舍：
 * <ol>
 *   <li><b>状态只能走 {@link #changeStatus}</b> —— 新增强制草稿、修改忽略客户端传的 status。
 *       否则客户端把 status 直接置 2 就绕过了「活动商品不能为空」这道校验</li>
 *   <li><b>可改 / 可删的状态收窄到「非进行中」</b>：进行中的活动正在影响售价，
 *       改它等于营业中途改价。要改先停用 —— 与「单据只有草稿可改」同一条思路</li>
 *   <li><b>冲突只告警不阻断</b>：同一商品命中多个活动时按 {@code priority} 取一、不相加
 *       （契约业务约束），所以时间区间重叠**不是错误**，只是提醒运营「这两个会互相盖住」</li>
 * </ol>
 *
 * <p><b>不校验「本类型该填的字段填了没」</b>（折扣率 / 门槛+减免 / 赠品说明 / 活动价）：
 * 契约把「按类型生效」定为**字段语义**而非必填约束，运营常常先建草稿再补参数。
 * 真正参与计算时（步骤②的优惠计算）才按类型取字段，缺字段即该活动不生效。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl extends ServiceImpl<PromotionMapper, Promotion>
        implements IPromotionService
{
    /**
     * 活动编号前缀：{@code PM} + yyyyMMdd + 6 位流水。
     *
     * <p>需求 §3.2 的编码规则表**没有定义促销活动编号**（只给了图书 BK / 订单 SO /
     * 借阅 BR / 回收 RC / 会员卡 M）。此处取「促销」的拼音首字母 {@code PM}，
     * 与阶段三给调拨取的 {@code DB} 是同一处置（结构一致：2 字母 + 8 位日期 + 6 位流水）。
     */
    private static final String PROMOTION_NO_PREFIX = "PM";

    private final ReferenceGuard referenceGuard;

    private final PromotionBookMapper promotionBookMapper;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<Promotion> selectPromotionList(Promotion promotion)
    {
        return baseMapper.selectPromotionList(promotion);
    }

    @Override
    public Promotion selectPromotionById(Long promotionId)
    {
        return referenceGuard.requireExists(baseMapper, promotionId, "促销活动");
    }

    // ==================================================================
    // 写
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPromotion(Promotion promotion)
    {
        // 必填性按**操作**判定（本实体 POST/PUT 共用，注解不知道这次是哪种操作）
        if (promotion.getPromotionName() == null || promotion.getPromotionName().isBlank())
        {
            throw BizErrors.badRequest("活动名称不能为空");
        }
        if (promotion.getPromotionType() == null)
        {
            throw BizErrors.badRequest("活动类型不能为空");
        }
        requireScopeValue(promotion);

        // 服务端接管字段：客户端传了也不认
        promotion.setPromotionId(null);
        promotion.setPromotionNo(null);
        promotion.setStatus(Promotion.STATUS_DRAFT);
        if (promotion.getScopeType() == null)
        {
            promotion.setScopeType(Promotion.SCOPE_ALL);
        }
        if (promotion.getIsSpecialPrice() == null)
        {
            promotion.setIsSpecialPrice(0);
        }
        if (promotion.getPriority() == null)
        {
            promotion.setPriority(0);
        }

        // promotion_no 是 NOT NULL 且带唯一索引 —— 不能「先插入再回填」。
        // 与 StockInServiceImpl#addStockIn 同一手法：预取 AUTO_INCREMENT 生成，插入后校正。
        Long predictedId = baseMapper.selectNextPromotionId();
        promotion.setPromotionNo(buildPromotionNo(predictedId == null ? 1L : predictedId));

        int rows = baseMapper.insert(promotion);

        String actualNo = buildPromotionNo(promotion.getPromotionId());
        if (!actualNo.equals(promotion.getPromotionNo()))
        {
            Promotion patch = new Promotion();
            patch.setPromotionId(promotion.getPromotionId());
            patch.setPromotionNo(actualNo);
            baseMapper.updateById(patch);
            promotion.setPromotionNo(actualNo);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePromotion(Promotion promotion)
    {
        if (promotion.getPromotionId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的促销活动");
        }
        Promotion current = referenceGuard.requireExists(baseMapper, promotion.getPromotionId(), "促销活动");
        requireEditable(current, "修改");

        requireScopeValue(promotion);

        // 状态与编号都不接受客户端写入（状态流转走 changeStatus）
        promotion.setPromotionNo(null);
        promotion.setStatus(null);

        int rows = baseMapper.updateById(promotion);
        referenceGuard.requireAffected(rows, "促销活动");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removePromotions(Long[] promotionIds)
    {
        List<Long> idList = (promotionIds == null) ? Collections.emptyList() : Arrays.asList(promotionIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "促销活动");

        for (Serializable id : ids)
        {
            Promotion one = referenceGuard.requireExists(baseMapper, id, "促销活动");
            requireEditable(one, "删除");
        }

        // 主从级联：**先删活动商品（物理）再删活动（逻辑）**，顺序不可颠倒
        // （docs/db/README.md §6.1 第 3 类）
        promotionBookMapper.deleteByPromotionIds(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 状态流转
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionStatusResult changeStatus(Long promotionId, Integer status)
    {
        Promotion promotion = referenceGuard.requireExists(baseMapper, promotionId, "促销活动");
        requireValidStatus(status);

        // 已结束是终态：历史活动不该被重新激活（契约错误码举的正是「从已结束直接到进行中」）
        if (Objects.equals(promotion.getStatus(), Promotion.STATUS_FINISHED))
        {
            throw BizErrors.ruleViolated("活动「" + promotion.getPromotionName()
                    + "」已结束，不能再变更状态 —— 需要继续做活动请新建一个");
        }
        if (Objects.equals(promotion.getStatus(), status))
        {
            throw BizErrors.ruleViolated("活动「" + promotion.getPromotionName() + "」已经是"
                    + statusName(status) + "状态，无需重复变更");
        }

        List<String> warnings = new ArrayList<>();
        if (Objects.equals(status, Promotion.STATUS_RUNNING))
        {
            // 启用前硬校验：没有商品的活动启用了也不会有任何效果
            if (promotionBookMapper.countByPromotionId(promotionId) <= 0)
            {
                throw BizErrors.ruleViolated("活动「" + promotion.getPromotionName()
                        + "」还没有配置活动商品，启用后不会对任何图书生效；"
                        + "请先在「活动商品」里加至少一本图书");
            }
            warnings.addAll(collectWarnings(promotion));
        }

        Promotion patch = new Promotion();
        patch.setPromotionId(promotionId);
        patch.setStatus(status);
        referenceGuard.requireAffected(baseMapper.updateById(patch), "促销活动");

        PromotionStatusResult result = new PromotionStatusResult();
        result.setPromotionId(promotionId);
        result.setPromotionNo(promotion.getPromotionNo());
        result.setStatus(status);
        result.setWarnings(warnings);
        return result;
    }

    /**
     * 启用时的**非阻断**告警。
     *
     * <p>两类：
     * <ol>
     *   <li>与其它进行中活动时间重叠 —— 提醒「同一商品会按优先级取一」，
     *       运营若本意是叠加，就该合并成一个活动</li>
     *   <li>已过 {@code endTime} 还启用 —— 启用了也不会生效（时间区间在 {@code startTime}~{@code endTime} 内）</li>
     * </ol>
     */
    private List<String> collectWarnings(Promotion promotion)
    {
        List<String> warnings = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        if (promotion.getEndTime() != null && promotion.getEndTime().isBefore(now))
        {
            warnings.add("活动结束时间（" + promotion.getEndTime()
                    + "）已经过去，启用后不会对售价生效；请先修改时间区间");
        }

        List<Promotion> conflicts = baseMapper.selectRunningOverlapping(
                promotion.getPromotionId(), promotion.getStartTime(), promotion.getEndTime());
        for (Promotion other : conflicts)
        {
            warnings.add("与进行中的活动「" + other.getPromotionName() + "（" + other.getPromotionNo()
                    + "，优先级 " + nzi(other.getPriority()) + "）」时间区间重叠 —— "
                    + "同一图书同时命中两个活动时按优先级取一、**不相加**，请确认是否符合预期");
        }
        return warnings;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 指定范围的活动必须给出范围值。
     *
     * <p>{@code scopeType=2指定分类 / 3指定图书} 而 {@code scopeValue} 为空时，
     * 这个活动**匹配不到任何商品** —— 启用后不会有任何效果，属明显的录入遗漏，开单时就拦下。
     */
    private void requireScopeValue(Promotion promotion)
    {
        Integer scopeType = promotion.getScopeType();
        if (scopeType == null || Objects.equals(scopeType, Promotion.SCOPE_ALL))
        {
            return;
        }
        if (promotion.getScopeValue() == null || promotion.getScopeValue().isBlank())
        {
            throw BizErrors.ruleViolated("适用范围选了「"
                    + (Objects.equals(scopeType, Promotion.SCOPE_CATEGORY) ? "指定分类" : "指定图书")
                    + "」，但范围值是空的 —— 这样的活动匹配不到任何商品");
        }
    }

    /** 只有「非进行中」的活动可改可删（进行中的正在影响售价） */
    private void requireEditable(Promotion promotion, String action)
    {
        if (Objects.equals(promotion.getStatus(), Promotion.STATUS_RUNNING))
        {
            throw BizErrors.ruleViolated("活动「" + promotion.getPromotionName()
                    + "」正在进行中，不能" + action + " —— 正在影响售价，请先停用再" + action);
        }
    }

    private void requireValidStatus(Integer status)
    {
        if (status == null
                || status < Promotion.STATUS_DRAFT || status > Promotion.STATUS_DISABLED)
        {
            throw BizErrors.ruleViolated("目标状态只能填 1~4（1草稿 2进行中 3已结束 4已停用），当前为 "
                    + status);
        }
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case Promotion.STATUS_DRAFT -> "草稿";
            case Promotion.STATUS_RUNNING -> "进行中";
            case Promotion.STATUS_FINISHED -> "已结束";
            case Promotion.STATUS_DISABLED -> "已停用";
            default -> "未知";
        };
    }

    /** 按需求 §3.2 的编码形态生成活动编号：{@code PM} + yyyyMMdd + 6 位流水 */
    private String buildPromotionNo(Long promotionId)
    {
        return PROMOTION_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", promotionId);
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
