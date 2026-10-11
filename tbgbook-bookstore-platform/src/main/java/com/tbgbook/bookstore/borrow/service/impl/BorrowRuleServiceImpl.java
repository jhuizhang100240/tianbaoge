package com.tbgbook.bookstore.borrow.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.borrow.domain.BorrowRule;
import com.tbgbook.bookstore.borrow.domain.LevelQuotaView;
import com.tbgbook.bookstore.borrow.mapper.BorrowRuleMapper;
import com.tbgbook.bookstore.borrow.service.IBorrowRuleService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 借阅全局规则 Service 实现（阶段五步骤④，页面 A-57）。
 *
 * <p><b>自定口径（登记 A42）</b>：
 * <ol>
 *   <li><b>单行配置</b>：一门店仅一条（{@code uk_store_id}）；{@code POST} 重复建档 409；</li>
 *   <li><b>PUT 按 storeId 更新已有行</b>（契约明文），未传 storeId 时按 ruleId 定位，
 *       两者都没有则 400；</li>
 *   <li><b>删除恒 422</b>：全局规则必须保留一行，停用改 {@code status=1}，不允许删空；</li>
 *   <li><b>缺行兜底</b>：读取时若库中无行，直接返回一份内置默认值
 *       （需求 §3.2 已确认的默认规则），不报 404 —— 前端规则页首屏不应因未初始化而空白。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class BorrowRuleServiceImpl extends ServiceImpl<BorrowRuleMapper, BorrowRule>
        implements IBorrowRuleService
{
    /** 单店部署固定门店ID */
    private static final long DEFAULT_STORE_ID = 1L;

    /** 规则状态：0正常 */
    private static final String STATUS_NORMAL = "0";

    /** level-quota 一并返回的配置键（只读展示用） */
    private static final String[] CONFIG_KEYS = {
            "biz.borrow.due.notify.days", "biz.borrow.overdue.notify.days",
            "biz.points.valid.months", "biz.points.expire.notify.days"
    };

    private final ReferenceGuard referenceGuard;

    private final MemberLevelMapper memberLevelMapper;

    private final ISysConfigService configService;

    @Override
    public List<BorrowRule> selectBorrowRuleList(BorrowRule rule)
    {
        return baseMapper.selectBorrowRuleList(rule);
    }

    @Override
    public BorrowRule selectBorrowRuleById(Long ruleId)
    {
        return referenceGuard.requireExists(baseMapper, ruleId, "借阅全局规则");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addBorrowRule(BorrowRule rule)
    {
        long storeId = rule.getStoreId() == null ? DEFAULT_STORE_ID : rule.getStoreId();
        rule.setStoreId(storeId);
        Long exists = baseMapper.selectCount(new LambdaQueryWrapper<BorrowRule>()
                .eq(BorrowRule::getStoreId, storeId));
        if (exists != null && exists > 0)
        {
            throw BizErrors.conflict("该门店已存在借阅全局规则（一门店仅一条），请直接修改");
        }
        normalize(rule);
        LocalDateTime now = LocalDateTime.now();
        rule.setRuleId(null);
        rule.setCreateBy(currentUsername());
        rule.setCreateTime(now);
        rule.setUpdateBy(currentUsername());
        rule.setUpdateTime(now);
        return baseMapper.insert(rule);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateBorrowRule(BorrowRule rule)
    {
        BorrowRule existing;
        if (rule.getRuleId() != null)
        {
            existing = referenceGuard.requireExists(baseMapper, rule.getRuleId(), "借阅全局规则");
        }
        else if (rule.getStoreId() != null)
        {
            existing = findFirstByStore(rule.getStoreId());
            if (existing == null)
            {
                throw BizErrors.notFound("借阅全局规则", rule.getStoreId());
            }
        }
        else
        {
            throw BizErrors.badRequest("未指定要修改的借阅全局规则（ruleId 或 storeId 至少给一个）");
        }

        rule.setRuleId(existing.getRuleId());
        rule.setStoreId(existing.getStoreId());
        fillMissing(rule, existing);
        normalize(rule);
        rule.setCreateBy(existing.getCreateBy());
        rule.setCreateTime(existing.getCreateTime());
        rule.setUpdateBy(currentUsername());
        rule.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(rule);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeBorrowRules(Long[] ruleIds)
    {
        List<Long> idList = (ruleIds == null) ? Collections.emptyList() : Arrays.asList(ruleIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "借阅全局规则");
        for (Serializable id : ids)
        {
            referenceGuard.requireExists(baseMapper, id, "借阅全局规则");
        }
        throw BizErrors.ruleViolated("借阅全局规则必须保留一条，不能删除；如需停用请把状态改为 1");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LevelQuotaView levelQuota(Long levelId)
    {
        List<MemberLevel> levels;
        if (levelId == null)
        {
            levels = memberLevelMapper.selectList(new LambdaQueryWrapper<MemberLevel>()
                    .orderByAsc(MemberLevel::getLevelRank));
        }
        else
        {
            levels = new ArrayList<>();
            levels.add(referenceGuard.requireExists(memberLevelMapper, levelId, "会员等级"));
        }

        LevelQuotaView view = new LevelQuotaView();
        for (MemberLevel level : levels)
        {
            LevelQuotaView.LevelQuota quota = new LevelQuotaView.LevelQuota();
            quota.setLevelId(level.getLevelId());
            quota.setLevelName(level.getLevelName());
            quota.setDiscountRate(level.getDiscountRate());
            quota.setBorrowQtyLimit(level.getBorrowQtyLimit());
            quota.setBorrowDays(level.getBorrowDays());
            quota.setDepositAmount(level.getDepositAmount());
            quota.setCanRenew(level.getCanRenew());
            view.getLevels().add(quota);
        }
        view.setGlobalRule(requireActiveRule());
        Map<String, String> configs = new LinkedHashMap<>();
        for (String key : CONFIG_KEYS)
        {
            configs.put(key, configService.selectConfigByKey(key));
        }
        view.setConfigKeys(configs);
        return view;
    }

    /**
     * {@inheritDoc}
     *
     * <p>库中缺行时返回内置默认值（不落库）：首次打开规则页不会报 404，
     * 管理员保存时才真正建档。
     */
    @Override
    public BorrowRule requireActiveRule()
    {
        BorrowRule existing = findFirstByStore(DEFAULT_STORE_ID);
        if (existing != null)
        {
            return existing;
        }
        BorrowRule defaults = new BorrowRule();
        defaults.setStoreId(DEFAULT_STORE_ID);
        defaults.setFinePerDay(new BigDecimal("0.20"));
        defaults.setFineMaxRate(new BigDecimal("0.50"));
        defaults.setCompensateRate(new BigDecimal("0.80"));
        defaults.setRenewTimes(1);
        defaults.setRenewDays(14);
        defaults.setReserveHoldDays(3);
        defaults.setRemindDaysBefore(3);
        defaults.setOverdueRemindInterval(3);
        defaults.setBlockOverdueBorrow(1);
        defaults.setStatus(STATUS_NORMAL);
        defaults.setRemark("内置默认值（库中尚未建档，保存后落库）");
        return defaults;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private BorrowRule findFirstByStore(long storeId)
    {
        List<BorrowRule> rows = baseMapper.selectList(new LambdaQueryWrapper<BorrowRule>()
                .eq(BorrowRule::getStoreId, storeId)
                .orderByAsc(BorrowRule::getRuleId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 部分更新时把未传字段从现有行拷回，避免 MP 把 null 写成空 */
    private void fillMissing(BorrowRule target, BorrowRule existing)
    {
        if (target.getFinePerDay() == null)
        {
            target.setFinePerDay(existing.getFinePerDay());
        }
        if (target.getFineMaxRate() == null)
        {
            target.setFineMaxRate(existing.getFineMaxRate());
        }
        if (target.getCompensateRate() == null)
        {
            target.setCompensateRate(existing.getCompensateRate());
        }
        if (target.getRenewTimes() == null)
        {
            target.setRenewTimes(existing.getRenewTimes());
        }
        if (target.getRenewDays() == null)
        {
            target.setRenewDays(existing.getRenewDays());
        }
        if (target.getReserveHoldDays() == null)
        {
            target.setReserveHoldDays(existing.getReserveHoldDays());
        }
        if (target.getRemindDaysBefore() == null)
        {
            target.setRemindDaysBefore(existing.getRemindDaysBefore());
        }
        if (target.getOverdueRemindInterval() == null)
        {
            target.setOverdueRemindInterval(existing.getOverdueRemindInterval());
        }
        if (target.getBlockOverdueBorrow() == null)
        {
            target.setBlockOverdueBorrow(existing.getBlockOverdueBorrow());
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

    /** 新建时补默认值 + 状态规范化 */
    private void normalize(BorrowRule rule)
    {
        if (rule.getFinePerDay() == null)
        {
            rule.setFinePerDay(new BigDecimal("0.20"));
        }
        if (rule.getFineMaxRate() == null)
        {
            rule.setFineMaxRate(new BigDecimal("0.50"));
        }
        if (rule.getCompensateRate() == null)
        {
            rule.setCompensateRate(new BigDecimal("0.80"));
        }
        if (rule.getRenewTimes() == null)
        {
            rule.setRenewTimes(1);
        }
        if (rule.getRenewDays() == null)
        {
            rule.setRenewDays(14);
        }
        if (rule.getReserveHoldDays() == null)
        {
            rule.setReserveHoldDays(3);
        }
        if (rule.getRemindDaysBefore() == null)
        {
            rule.setRemindDaysBefore(3);
        }
        if (rule.getOverdueRemindInterval() == null)
        {
            rule.setOverdueRemindInterval(3);
        }
        if (rule.getBlockOverdueBorrow() == null)
        {
            rule.setBlockOverdueBorrow(1);
        }
        if (rule.getStatus() == null)
        {
            rule.setStatus(STATUS_NORMAL);
        }
        if (rule.getRemark() == null)
        {
            rule.setRemark("");
        }
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
