package com.tbgbook.bookstore.book.service.impl;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.TextbookList;
import com.tbgbook.bookstore.book.mapper.TextbookListMapper;
import com.tbgbook.bookstore.book.mapper.TextbookListItemMapper;
import com.tbgbook.bookstore.book.service.ITextbookListService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.common.utils.DateUtils;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 教材清单 服务层实现。
 *
 * <p>本表没有 {@code del_flag}（单据，物理删除）、没有 {@code version}（无乐观锁列），
 * 所以三件事靠代码保证：
 * <ol>
 *   <li><b>并发状态流转</b> —— 用带 {@code status} 条件的单条 UPDATE
 *       （{@code TextbookListMapper#transition}），影响 0 行即视为并发冲突并返回 409，
 *       不静默重复发布</li>
 *   <li><b>删除保护</b> —— 只有草稿可删（见 {@code #removeTextbookLists}）</li>
 *   <li><b>主从级联</b> —— 删主表时先删明细（{@code ReferenceGuard#cascadeRemove}）</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class TextbookListServiceImpl extends ServiceImpl<TextbookListMapper, TextbookList>
        implements ITextbookListService
{
    /** 状态：草稿（可编辑、可删、会员端不可见） */
    public static final int STATUS_DRAFT = 1;

    /** 状态：已发布（会员端可见并接受预订） */
    public static final int STATUS_PUBLISHED = 2;

    /** 状态：已结束（停止接受新预订，终态） */
    public static final int STATUS_FINISHED = 3;

    /** 清单编号前缀。需求 §3.2 的编码规则表未定义教材清单编号，此处按同族「单据」格式取 TB */
    private static final String LIST_NO_PREFIX = "TB";

    /** 编号里的随机段位数（4 位，与需求「+4位流水」的位数对齐；真随机的理由见 generateListNo） */
    private static final int LIST_NO_RANDOM_MIN = 1000;

    private static final int LIST_NO_RANDOM_MAX = 10000;

    private final ReferenceGuard referenceGuard;

    private final TextbookListItemMapper itemMapper;

    @Override
    public List<TextbookList> selectTextbookList(TextbookList textbookList)
    {
        return baseMapper.selectTextbookList(textbookList);
    }

    @Override
    public TextbookList selectTextbookListById(Long listId)
    {
        return referenceGuard.requireExists(baseMapper, listId, "教材清单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addTextbookList(TextbookList textbookList)
    {
        // 状态：新增只能是草稿。**显式拒绝**而不是静默改写 ——
        // 允许 POST 直接传「已发布」会绕过发布端点的「清单非空 + 每行明细均已建档」前置校验
        Integer status = textbookList.getStatus();
        if (status != null && status != STATUS_DRAFT)
        {
            throw BizErrors.illegalTransition("新建的教材清单", statusName(STATUS_DRAFT), statusName(status));
        }
        textbookList.setStatus(STATUS_DRAFT);

        // 服务端接管字段：编号 / 统计列 / 发布时间一律不接受客户端传入
        textbookList.setListNo(generateListNo());
        textbookList.setBookKindCount(0);
        textbookList.setReserveQty(0);
        textbookList.setArrivedQty(0);
        textbookList.setPickedQty(0);
        textbookList.setPublishTime(null);

        return baseMapper.insert(textbookList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTextbookList(TextbookList textbookList)
    {
        Long listId = textbookList.getListId();
        if (listId == null)
        {
            throw BizErrors.badRequest("未指定要修改的教材清单");
        }
        TextbookList current = referenceGuard.requireExists(baseMapper, listId, "教材清单");

        // 服务端接管字段一律忽略（不是报错，与图书/出版社的既有口径一致）
        textbookList.setListNo(null);
        textbookList.setBookKindCount(null);
        textbookList.setReserveQty(null);
        textbookList.setArrivedQty(null);
        textbookList.setPickedQty(null);
        textbookList.setPublishTime(null);
        textbookList.setStoreId(null);

        // 状态不在这里直接写：走与发布/结束**同一套**流转校验，
        // 否则 PUT + status=2 就能绕过发布的前置校验
        Integer targetStatus = textbookList.getStatus();
        textbookList.setStatus(null);
        if (targetStatus != null && !targetStatus.equals(current.getStatus()))
        {
            applyTransition(current, targetStatus, textbookList.getEndTime());
            // 流转已按规则写过 end_time（发布不动它、结束写计划值或当前时刻），
            // 这里摘掉，避免下一步把 null 写回去
            textbookList.setEndTime(null);
        }
        // 其余可编辑字段。listName / term 是 @NotBlank，PUT 必带，故不可能出现「SET 子句为空」
        return baseMapper.updateById(textbookList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeTextbookLists(Long[] listIds)
    {
        Collection<? extends Serializable> ids = referenceGuard.requireIds(Arrays.asList(listIds), "教材清单");
        for (Serializable id : ids)
        {
            TextbookList current = referenceGuard.requireExists(baseMapper, id, "教材清单");
            int status = statusOf(current);
            // ⚠️ 用户 2026-09-22 裁决的原文是「已发布(2) 禁止删除」，实现时扩到了「非草稿都不能删」，
            //    理由：已结束(3) 的清单同样承载着到货/取书历史，而 FR-4.6.8（教材季专题分析）要算
            //    到货率与取书率，删掉即丢分析口径。
            //    **该扩范围已由用户 2026-09-22 追认（C16 结项）**，故不再回退；
            //    若将来要收窄回「仅禁已发布」，把条件改成 `status == STATUS_PUBLISHED` 即可（一行）
            if (status != STATUS_DRAFT)
            {
                throw BizErrors.ruleViolated("教材清单「" + current.getListName() + "」当前为「"
                        + statusName(status) + "」，已发布或已结束的清单不能删除");
            }
            referenceGuard.cascadeRemove(id,
                    headerId -> itemMapper.deleteByListId((Long) headerId),
                    headerId -> baseMapper.deleteById(headerId));
        }
        return ids.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(Long listId)
    {
        TextbookList current = referenceGuard.requireExists(baseMapper, listId, "教材清单");
        // 契约：「清单已是发布状态」是 409 而非 422（见接口文档 §发布教材清单）
        if (statusOf(current) == STATUS_PUBLISHED)
        {
            throw BizErrors.conflict("教材清单「" + current.getListName() + "」已是发布状态");
        }
        return applyTransition(current, STATUS_PUBLISHED, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finish(Long listId)
    {
        TextbookList current = referenceGuard.requireExists(baseMapper, listId, "教材清单");
        // 前置「必须是已发布」由 applyTransition 的单向推进校验覆盖（草稿→已结束、已结束→已结束 都 422）
        return applyTransition(current, STATUS_FINISHED, null);
    }

    @Override
    public void refreshStatistics(Long listId)
    {
        if (listId == null)
        {
            return;
        }
        TextbookList stats = itemMapper.selectStatistics(listId);
        TextbookList patch = new TextbookList();
        patch.setListId(listId);
        patch.setBookKindCount(stats.getBookKindCount());
        patch.setReserveQty(stats.getReserveQty());
        patch.setArrivedQty(stats.getArrivedQty());
        patch.setPickedQty(stats.getPickedQty());
        // update_by / update_time 交给 MyMetaObjectHandler 的 updateFill（updateById 走填充）
        baseMapper.updateById(patch);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 单向推进一格的状态流转：草稿(1) → 已发布(2) → 已结束(3)。
     *
     * <p>跳级（1→3）与回退（2→1、3→2）一律 422。**用带 {@code status} 条件的 UPDATE 落地**，
     * 并发第二次提交拿 0 行 → 409，而不是重复发布。
     *
     * @param current      当前行（已查库）
     * @param targetStatus 目标状态
     * @param plannedEndTime 客户端填的计划结束时间，仅「结束」时参考
     * @return 影响行数（恒为 1，0 行会直接抛 409）
     */
    private int applyTransition(TextbookList current, int targetStatus, LocalDateTime plannedEndTime)
    {
        int from = statusOf(current);
        if (targetStatus != from + 1)
        {
            throw BizErrors.illegalTransition("教材清单", statusName(from), statusName(targetStatus));
        }

        LocalDateTime now = LocalDateTime.now();
        TextbookList patch = new TextbookList();
        patch.setListId(current.getListId());
        patch.setStatus(targetStatus);

        if (targetStatus == STATUS_PUBLISHED)
        {
            // 发布的前置：清单非空 + 每行明细都已关联有效图书（与契约里的 /publish 说明一致）
            requirePublishable(current.getListId(), current.getListName());
            patch.setPublishTime(now);
        }
        else if (targetStatus == STATUS_FINISHED)
        {
            // 不覆盖运营填的计划结束时间；未填才补当前时刻
            patch.setEndTime(plannedEndTime != null ? plannedEndTime : now);
        }

        // XML 的 update 不走 MyMetaObjectHandler，审计字段要自己写
        patch.setUpdateBy(SecurityUtils.getUsername());
        patch.setUpdateTime(now);

        int rows = baseMapper.transition(patch, from);
        referenceGuard.requireAffected(rows, "教材清单状态");
        return rows;
    }

    /**
     * 发布前置校验：清单非空，且每行明细都已关联到一本有效图书。
     *
     * @param listId   清单ID
     * @param listName 清单名称（拼错误文案用）
     */
    private void requirePublishable(Long listId, String listName)
    {
        TextbookList stats = itemMapper.selectStatistics(listId);
        int kinds = stats.getBookKindCount() == null ? 0 : stats.getBookKindCount();
        if (kinds == 0)
        {
            throw BizErrors.ruleViolated("教材清单「" + listName + "」还没有教材明细，不能发布");
        }
        Long unlinked = itemMapper.countUnlinkedItems(listId);
        if (unlinked != null && unlinked > 0)
        {
            throw BizErrors.ruleViolated("教材清单「" + listName + "」有 " + unlinked
                    + " 行明细未关联到有效图书，请先处理");
        }
    }

    /**
     * 生成清单编号 {@code TB + yyyyMMddHHmmss + 4 位随机}。
     *
     * <p><b>为什么用随机而不是真流水</b>：需求 §3.2 的编码规则表**没有定义**教材清单编号
     * （只有图书/订单/借阅/回收/会员卡），此处按同族的「单据」格式取「前缀 + 时间戳 + 4 位」，
     * 后缀沿用 {@code BookImportBatchServiceImpl#generateBatchNo} 已在用的随机段做法 ——
     * 它不需要额外的序列表或 Redis 计数，而 {@code uk_list_no} 唯一索引是撞号时的兜底
     * （撞上会抛 {@code DuplicateKeyException}，由全局异常处理器转 500，量级上可忽略：
     * 教材清单是每个学期个位数的单据）。
     */
    private String generateListNo()
    {
        String stamp = DateUtils.dateTimeNow();
        int random = ThreadLocalRandom.current().nextInt(LIST_NO_RANDOM_MIN, LIST_NO_RANDOM_MAX);
        return LIST_NO_PREFIX + stamp + random;
    }

    private static int statusOf(TextbookList list)
    {
        return list.getStatus() == null ? STATUS_DRAFT : list.getStatus();
    }

    private static String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case STATUS_DRAFT -> "草稿";
            case STATUS_PUBLISHED -> "已发布";
            case STATUS_FINISHED -> "已结束";
            default -> "未知";
        };
    }
}
