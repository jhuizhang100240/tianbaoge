package com.tbgbook.bookstore.member.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.MemberTag;
import com.tbgbook.bookstore.member.domain.MemberTagRel;
import com.tbgbook.bookstore.member.domain.TagMarkResult;
import com.tbgbook.bookstore.member.domain.TagRecalcResult;
import com.tbgbook.bookstore.member.domain.TagRelRequest;
import com.tbgbook.bookstore.member.domain.TagRemoveResult;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.MemberTagMapper;
import com.tbgbook.bookstore.member.mapper.MemberTagRelMapper;
import com.tbgbook.bookstore.member.service.IMemberTagService;
import com.tbgbook.bookstore.member.support.TagRule;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 会员标签 Service 实现（阶段五步骤③b，页面 A-50）。
 *
 * <p><b>自定口径（登记 A38）</b>：
 * <ol>
 *   <li><b>同名唯一</b>：同店标签名称不可重复（预检 409）；</li>
 *   <li><b>isRule 不允许修改</b>（手动↔规则切换会破坏既有关系语义，要换类型请删除重建）；</li>
 *   <li><b>规则标签约束</b>：表达式在保存与重算时都校验（422）；规则标签的关联只能由
 *       {@link #recalc} 整体替换，手工增删一律 422；</li>
 *   <li><b>停用标签</b>：不允许新打标（422），但允许解标与重算；</li>
 *   <li><b>删除保护</b>：被关系引用的标签禁止删除（422，建议停用）；</li>
 *   <li><b>规则表达式语法</b>：{@code 指标 运算符 数值}（白名单见 {@link TagRule}），
 *       近 30 天指标的时间窗由应用侧计算（A32）。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class MemberTagServiceImpl extends ServiceImpl<MemberTagMapper, MemberTag>
        implements IMemberTagService
{
    /** 规则来源：2规则自动 */
    private static final int SOURCE_RULE = 2;

    /** 手工来源：1手动打标 */
    private static final int SOURCE_MANUAL = 1;

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final MemberTagRelMapper memberTagRelMapper;

    // ==================================================================
    // 标签定义
    // ==================================================================

    @Override
    public List<MemberTag> selectMemberTagList(MemberTag tag)
    {
        return baseMapper.selectMemberTagList(tag);
    }

    @Override
    public MemberTag selectMemberTagById(Long tagId)
    {
        return referenceGuard.requireExists(baseMapper, tagId, "会员标签");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMemberTag(MemberTag tag)
    {
        tag.setTagId(null);
        if (tag.getTagType() == null)
        {
            tag.setTagType(4);
        }
        if (tag.getIsRule() == null)
        {
            tag.setIsRule(0);
        }
        if (tag.getStatus() == null)
        {
            tag.setStatus("0");
        }
        tag.setMemberCount(0);
        normalizeRule(tag);
        tag.setTagName(tag.getTagName().trim());
        requireNameUnique(tag.getTagName(), null);
        return baseMapper.insert(tag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateMemberTag(MemberTag tag)
    {
        if (tag.getTagId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的会员标签");
        }
        MemberTag existing = referenceGuard.requireExists(baseMapper, tag.getTagId(), "会员标签");
        if (tag.getIsRule() != null && !tag.getIsRule().equals(existing.getIsRule()))
        {
            throw BizErrors.ruleViolated("标签类型（手动/规则）不允许修改；请删除后重建");
        }
        if (tag.getTagRule() == null)
        {
            tag.setTagRule(existing.getTagRule());
        }
        normalizeRule(tag);
        if (tag.getTagName() != null)
        {
            tag.setTagName(tag.getTagName().trim());
            requireNameUnique(tag.getTagName(), tag.getTagId());
        }
        // 覆盖数由打标/解标/重算维护，不接受直接写入
        tag.setMemberCount(existing.getMemberCount());
        return baseMapper.updateById(tag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMemberTags(Long[] tagIds)
    {
        List<Long> idList = (tagIds == null) ? Collections.emptyList() : Arrays.asList(tagIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "会员标签");
        for (Serializable id : ids)
        {
            referenceGuard.requireExists(baseMapper, id, "会员标签");
            referenceGuard.requireNoReference(
                    "biz_member_tag_rel", "tag_id", id, null,
                    "会员", "会员标签", "改为停用");
        }
        // 逻辑删除（@TableLogic）
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 标签关系
    // ==================================================================

    @Override
    public List<MemberTagRel> selectMemberTagRelList(MemberTagRel rel)
    {
        return memberTagRelMapper.selectMemberTagRelList(rel);
    }

    @Override
    public MemberTagRel selectMemberTagRelById(Long relId)
    {
        return referenceGuard.requireExists(memberTagRelMapper, relId, "会员标签关系");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addMemberTagRel(MemberTagRel rel)
    {
        if (rel.getMemberId() == null || rel.getTagId() == null)
        {
            throw BizErrors.badRequest("会员ID与标签ID不能为空");
        }
        referenceGuard.requireExists(memberMapper, rel.getMemberId(), "会员");
        MemberTag tag = referenceGuard.requireExists(baseMapper, rel.getTagId(), "会员标签");
        requireManualAndActive(tag);
        if (existsRel(rel.getMemberId(), rel.getTagId()))
        {
            throw BizErrors.conflict("该会员已拥有此标签，不能重复打标");
        }
        rel.setRelId(null);
        rel.setSource(SOURCE_MANUAL);
        rel.setCreateBy(currentUsername());
        rel.setCreateTime(LocalDateTime.now());
        int rows = memberTagRelMapper.insert(rel);
        baseMapper.addMemberCount(rel.getTagId(), 1);
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int updateMemberTagRel(MemberTagRel rel)
    {
        if (rel.getRelId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的标签关系");
        }
        referenceGuard.requireExists(memberTagRelMapper, rel.getRelId(), "会员标签关系");
        throw BizErrors.ruleViolated("标签关系不支持修改；请先解除打标后重新打标");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeMemberTagRels(Long[] relIds)
    {
        List<Long> idList = (relIds == null) ? Collections.emptyList() : Arrays.asList(relIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "会员标签关系");
        Map<Long, Integer> perTag = new HashMap<>();
        for (Serializable id : ids)
        {
            MemberTagRel row = referenceGuard.requireExists(memberTagRelMapper, id, "会员标签关系");
            MemberTag tag = referenceGuard.requireExists(baseMapper, row.getTagId(), "会员标签");
            requireManualTag(tag);
            perTag.merge(row.getTagId(), 1, Integer::sum);
        }
        int deleted = memberTagRelMapper.deleteByIds(ids);
        perTag.forEach((tagId, count) -> baseMapper.addMemberCount(tagId, -count));
        return deleted;
    }

    // ==================================================================
    // 手工打标 / 解标
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagMarkResult mark(TagRelRequest request)
    {
        List<MemberTag> tags = validateRequest(request, true);
        Long memberId = request.getMemberId();
        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        int skipped = 0;
        for (MemberTag tag : tags)
        {
            if (existsRel(memberId, tag.getTagId()))
            {
                skipped++;
                continue;
            }
            MemberTagRel rel = new MemberTagRel();
            rel.setMemberId(memberId);
            rel.setTagId(tag.getTagId());
            rel.setSource(SOURCE_MANUAL);
            rel.setCreateBy(currentUsername());
            rel.setCreateTime(now);
            memberTagRelMapper.insert(rel);
            baseMapper.addMemberCount(tag.getTagId(), 1);
            added++;
        }
        TagMarkResult result = new TagMarkResult();
        result.setAdded(added);
        result.setSkipped(skipped);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagRemoveResult remove(TagRelRequest request)
    {
        List<MemberTag> tags = validateRequest(request, false);
        Long memberId = request.getMemberId();
        int removed = 0;
        for (MemberTag tag : tags)
        {
            int deleted = memberTagRelMapper.delete(new LambdaQueryWrapper<MemberTagRel>()
                    .eq(MemberTagRel::getMemberId, memberId)
                    .eq(MemberTagRel::getTagId, tag.getTagId()));
            if (deleted > 0)
            {
                baseMapper.addMemberCount(tag.getTagId(), -deleted);
                removed += deleted;
            }
        }
        TagRemoveResult result = new TagRemoveResult();
        result.setRemoved(removed);
        return result;
    }

    // ==================================================================
    // 规则标签重算
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagRecalcResult recalc(Long tagId)
    {
        MemberTag tag = referenceGuard.requireExists(baseMapper, tagId, "会员标签");
        if (!Integer.valueOf(1).equals(tag.getIsRule()))
        {
            throw BizErrors.ruleViolated("该标签不是规则标签（isRule≠1）");
        }
        TagRule rule = TagRule.parse(tag.getTagRule());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusDays(30);
        List<Map<String, Object>> rows =
                memberTagRelMapper.selectMemberMetricValues(rule.metricKey(), windowStart, now);
        Set<Long> target = new HashSet<>();
        for (Map<String, Object> row : rows)
        {
            Object rawId = row.get("memberId");
            if (rawId == null)
            {
                continue;
            }
            Object rawValue = row.get("metricValue");
            BigDecimal actual = (rawValue == null) ? null : new BigDecimal(rawValue.toString());
            if (rule.test(actual))
            {
                target.add(((Number) rawId).longValue());
            }
        }

        Set<Long> current = new HashSet<>(memberTagRelMapper.selectRelMemberIdsByTag(tagId));
        List<Long> added = new ArrayList<>();
        for (Long memberId : target)
        {
            if (!current.contains(memberId))
            {
                added.add(memberId);
            }
        }
        List<Long> removed = new ArrayList<>();
        for (Long memberId : current)
        {
            if (!target.contains(memberId))
            {
                removed.add(memberId);
            }
        }

        if (!removed.isEmpty())
        {
            memberTagRelMapper.deleteByTagAndMembers(tagId, removed);
        }
        if (!added.isEmpty())
        {
            memberTagRelMapper.insertRelBatch(tagId, added, SOURCE_RULE, currentUsername(), now);
        }
        baseMapper.setMemberCount(tagId, target.size());

        TagRecalcResult result = new TagRecalcResult();
        result.setMemberCount(target.size());
        result.setAdded(added.size());
        result.setRemoved(removed.size());
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 校验打标/解标请求并返回标签（missing→404；规则标签→422；打标时停用→422） */
    private List<MemberTag> validateRequest(TagRelRequest request, boolean forMark)
    {
        if (request.getMemberId() == null)
        {
            throw BizErrors.badRequest("会员ID不能为空");
        }
        if (request.getTagIds() == null || request.getTagIds().isEmpty())
        {
            throw BizErrors.badRequest("标签ID不能为空");
        }
        referenceGuard.requireExists(memberMapper, request.getMemberId(), "会员");
        List<MemberTag> tags = new ArrayList<>();
        for (Long tagId : request.getTagIds())
        {
            MemberTag tag = referenceGuard.requireExists(baseMapper, tagId, "会员标签");
            if (forMark)
            {
                requireManualAndActive(tag);
            }
            else
            {
                requireManualTag(tag);
            }
            tags.add(tag);
        }
        return tags;
    }

    /** 手工且启用（打标用） */
    private void requireManualAndActive(MemberTag tag)
    {
        requireManualTag(tag);
        if ("1".equals(tag.getStatus()))
        {
            throw BizErrors.ruleViolated("标签已停用，不能打标（tagId=" + tag.getTagId() + "）");
        }
    }

    /** 手工标签（规则标签的关联不允许手工增删） */
    private void requireManualTag(MemberTag tag)
    {
        if (Integer.valueOf(1).equals(tag.getIsRule()))
        {
            throw BizErrors.ruleViolated("规则标签（isRule=1）的关联不允许手工增删");
        }
    }

    /** 规则标签保存/修改时校验表达式；手动标签强制清空表达式 */
    private void normalizeRule(MemberTag tag)
    {
        if (Integer.valueOf(1).equals(tag.getIsRule()))
        {
            TagRule.parse(tag.getTagRule());
            tag.setTagRule(tag.getTagRule().trim());
        }
        else
        {
            tag.setTagRule("");
        }
    }

    private void requireNameUnique(String tagName, Long excludeTagId)
    {
        LambdaQueryWrapper<MemberTag> wrapper = new LambdaQueryWrapper<MemberTag>()
                .eq(MemberTag::getTagName, tagName);
        if (excludeTagId != null)
        {
            wrapper.ne(MemberTag::getTagId, excludeTagId);
        }
        if (count(wrapper) > 0)
        {
            throw BizErrors.duplicate("会员标签", "标签名称", tagName);
        }
    }

    private boolean existsRel(Long memberId, Long tagId)
    {
        return memberTagRelMapper.selectCount(new LambdaQueryWrapper<MemberTagRel>()
                .eq(MemberTagRel::getMemberId, memberId)
                .eq(MemberTagRel::getTagId, tagId)) > 0;
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
