package com.tbgbook.bookstore.member.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.mapper.MemberLevelMapper;
import com.tbgbook.bookstore.member.service.IMemberLevelService;

import lombok.RequiredArgsConstructor;

/**
 * 会员等级 Service 实现（阶段五步骤①，页面 A-46）。
 *
 * <p>三条与契约一致的规则放在这里（而不是实体注解，因为注解区分不了 POST / PUT）：
 * <ol>
 *   <li><b>默认等级全表唯一</b>：设某行为默认时，同一事务内清除其他行的标记；
 *       不允许把「唯一的默认等级」取消 —— 否则注册流程没有初始等级可用（422）</li>
 *   <li><b>默认等级禁止删除</b>：删掉它注册流程直接失效，先切换默认再删</li>
 *   <li><b>被会员引用的等级禁止删除</b>（契约明示）：走 {@link ReferenceGuard} 统一封装，
 *       {@code biz_member} 没有 {@code del_flag}，软删列传 {@code null}</li>
 * </ol>
 *
 * <p>等级历史（{@code biz_member_level_log}）不参与删除保护：等级是配置数据，
 * 逻辑删除后历史记录仍保留等级 ID，不影响财务可追溯（与「出版社被历史图书引用」同一取舍）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberLevelServiceImpl extends ServiceImpl<MemberLevelMapper, MemberLevel>
        implements IMemberLevelService
{
    private final ReferenceGuard referenceGuard;

    @Override
    public List<MemberLevel> selectMemberLevelList(MemberLevel level)
    {
        return baseMapper.selectMemberLevelList(level);
    }

    @Override
    public MemberLevel selectMemberLevelById(Long levelId)
    {
        return referenceGuard.requireExists(baseMapper, levelId, "会员等级");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMemberLevel(MemberLevel level)
    {
        level.setLevelId(null);
        if (level.getStatus() == null)
        {
            level.setStatus("0");
        }
        if (level.getCanRenew() == null)
        {
            level.setCanRenew(1);
        }
        if (level.getIsDefault() == null)
        {
            level.setIsDefault(0);
        }
        requireCodeUnique(level.getLevelCode(), null);
        if (Integer.valueOf(1).equals(level.getIsDefault()))
        {
            clearOtherDefaults(null);
        }
        return baseMapper.insert(level);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMemberLevel(MemberLevel level)
    {
        if (level.getLevelId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的会员等级");
        }
        MemberLevel existing = referenceGuard.requireExists(baseMapper, level.getLevelId(), "会员等级");
        requireCodeUnique(level.getLevelCode(), level.getLevelId());

        if (Integer.valueOf(1).equals(level.getIsDefault()))
        {
            clearOtherDefaults(level.getLevelId());
        }
        else if (Integer.valueOf(0).equals(level.getIsDefault())
                && Integer.valueOf(1).equals(existing.getIsDefault()))
        {
            // 取消默认前必须已有其它默认等级兜底，否则注册流程没有初始等级
            long others = count(new LambdaQueryWrapper<MemberLevel>()
                    .eq(MemberLevel::getIsDefault, 1)
                    .ne(MemberLevel::getLevelId, level.getLevelId()));
            if (others == 0)
            {
                throw BizErrors.ruleViolated("必须保留一个默认等级；请先将其他等级设为默认");
            }
        }
        return baseMapper.updateById(level);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMemberLevels(Long[] levelIds)
    {
        List<Long> idList = (levelIds == null) ? Collections.emptyList() : Arrays.asList(levelIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "会员等级");

        for (Serializable id : ids)
        {
            MemberLevel level = referenceGuard.requireExists(baseMapper, id, "会员等级");
            if (Integer.valueOf(1).equals(level.getIsDefault()))
            {
                throw BizErrors.ruleViolated("默认等级不能删除；请先将其他等级设为默认");
            }
            // 删除保护：被会员引用的等级禁止删除（biz_member 无 del_flag）
            referenceGuard.requireNoReference(
                    "biz_member", "level_id", id, null,
                    "会员", "会员等级", "改为停用");
        }
        // 逻辑删除（@TableLogic：del_flag 0→2）
        return baseMapper.deleteByIds(ids);
    }

    /** 等级编码唯一（唯一索引 uk_level_code；预检给友好 409，竞态由全局 DuplicateKeyException 兜底） */
    private void requireCodeUnique(String levelCode, Long excludeId)
    {
        LambdaQueryWrapper<MemberLevel> wrapper = new LambdaQueryWrapper<MemberLevel>()
                .eq(MemberLevel::getLevelCode, levelCode);
        if (excludeId != null)
        {
            wrapper.ne(MemberLevel::getLevelId, excludeId);
        }
        if (count(wrapper) > 0)
        {
            throw BizErrors.duplicate("会员等级", "等级编码", levelCode);
        }
    }

    /** 清除其他行的默认标记（excludeId 为本次要设为默认的行，可为 null） */
    private void clearOtherDefaults(Long excludeId)
    {
        LambdaUpdateWrapper<MemberLevel> wrapper = new LambdaUpdateWrapper<MemberLevel>()
                .eq(MemberLevel::getIsDefault, 1)
                .set(MemberLevel::getIsDefault, 0);
        if (excludeId != null)
        {
            wrapper.ne(MemberLevel::getLevelId, excludeId);
        }
        update(wrapper);
    }
}
