package com.tbgbook.bookstore.book.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.TextbookList;
import com.tbgbook.bookstore.book.domain.TextbookListItem;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.mapper.TextbookListItemMapper;
import com.tbgbook.bookstore.book.service.ITextbookListService;
import com.tbgbook.bookstore.book.service.ITextbookListItemService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;

import lombok.RequiredArgsConstructor;

/**
 * 教材清单明细 服务层实现。
 *
 * <p><b>三类服务端接管</b>（接口文档「教材清单明细」业务约束）：
 * <ol>
 *   <li>{@code isbn} / {@code title} / {@code price} —— 按 {@code bookId} 查图书主档**回填**，
 *       客户端传值被忽略。这三列在 DDL 注释里写明是「冗余」，允许客户端写会让明细与主档不一致</li>
 *   <li>{@code pickedQty} —— 由取书核销累减（阶段四），当前不接受传入也不覆盖库中原值</li>
 *   <li>{@code remainQty} / {@code status} —— 由数量关系算出，见 {@link #applyDerivedFields}</li>
 * </ol>
 *
 * <p>每次明细增删改后都会调 {@code ITextbookListService#refreshStatistics} 重算主表统计列 ——
 * 主表的品种数/预订数/到货数/已取数「由明细聚合、不接受传入」，唯一的维护点就在这里。
 * 两边在**同一事务**内：明细写成功而统计没跟上会让 A-12 列表显示旧数字。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class TextbookListItemServiceImpl extends ServiceImpl<TextbookListItemMapper, TextbookListItem>
        implements ITextbookListItemService
{
    /** 明细状态：待到货（尚未到货） */
    public static final int ITEM_STATUS_WAITING = 1;

    /** 明细状态：已到货待取书（货到了但一本都还没取） */
    public static final int ITEM_STATUS_ARRIVED = 2;

    /** 明细状态：部分取书 */
    public static final int ITEM_STATUS_PARTIAL = 3;

    /** 明细状态：已取完 */
    public static final int ITEM_STATUS_DONE = 4;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final ITextbookListService textbookListService;

    @Override
    public List<TextbookListItem> selectTextbookListItemList(TextbookListItem textbookListItem)
    {
        return baseMapper.selectTextbookListItemList(textbookListItem);
    }

    @Override
    public TextbookListItem selectTextbookListItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "教材清单明细");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addTextbookListItem(TextbookListItem textbookListItem)
    {
        TextbookList list = textbookListService.selectTextbookListById(textbookListItem.getListId());
        requireNotFinished(list);

        Book book = applyBookSnapshot(textbookListItem);
        requireBookNotDuplicated(textbookListItem.getListId(), textbookListItem.getBookId(), null, book.getTitle());

        // 新增时已取数必然为 0（核销在阶段四）
        textbookListItem.setPickedQty(null);
        applyDerivedFields(textbookListItem, 0);

        int rows = baseMapper.insert(textbookListItem);
        textbookListService.refreshStatistics(textbookListItem.getListId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTextbookListItem(TextbookListItem textbookListItem)
    {
        Long itemId = textbookListItem.getItemId();
        if (itemId == null)
        {
            throw BizErrors.badRequest("未指定要修改的教材清单明细");
        }
        TextbookListItem current = referenceGuard.requireExists(baseMapper, itemId, "教材清单明细");

        // 不允许改挂到别的清单 —— 那等于换了业务含义，应删了重建
        if (textbookListItem.getListId() != null
                && !textbookListItem.getListId().equals(current.getListId()))
        {
            throw BizErrors.ruleViolated("教材清单明细不能改挂到其它清单，请删除后重新添加");
        }
        textbookListItem.setListId(current.getListId());

        Book book = applyBookSnapshot(textbookListItem);
        requireBookNotDuplicated(current.getListId(), textbookListItem.getBookId(), itemId, book.getTitle());

        // ⚠️ 已取数取**库中原值**而不是入参：本接口不接受传入，而 MP 的 updateById 跳过 null，
        //    库里那一列不动。若这里按 0 计算，一旦阶段四写入过 pickedQty，剩余数与状态就会被算错
        int pickedQty = current.getPickedQty() == null ? 0 : current.getPickedQty();
        textbookListItem.setPickedQty(null);
        applyDerivedFields(textbookListItem, pickedQty);

        int rows = baseMapper.updateById(textbookListItem);
        textbookListService.refreshStatistics(current.getListId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeTextbookListItems(Long[] itemIds)
    {
        Collection<? extends Serializable> ids = referenceGuard.requireIds(Arrays.asList(itemIds), "教材清单明细");
        Set<Long> affectedListIds = new LinkedHashSet<>();
        for (Serializable id : ids)
        {
            TextbookListItem current = referenceGuard.requireExists(baseMapper, id, "教材清单明细");
            affectedListIds.add(current.getListId());
            baseMapper.deleteById(id);
        }
        // 统计要跟着减 —— 不重算的话主表的品种数会停在旧值
        affectedListIds.forEach(textbookListService::refreshStatistics);
        return ids.size();
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 按 {@code bookId} 回填三个冗余字段，忽略客户端传值。
     *
     * @param item 明细（{@code bookId} 由 {@code @NotNull} 保证非空）
     * @return 图书主档，供调用方拼错误文案复用
     */
    private Book applyBookSnapshot(TextbookListItem item)
    {
        Book book = referenceGuard.requireExists(bookMapper, item.getBookId(), "图书");
        item.setIsbn(book.getIsbn());
        item.setTitle(book.getTitle());
        item.setPrice(book.getPrice());
        return book;
    }

    /**
     * 同一清单内同一本书只允许一行。
     *
     * <p>表上**没有** {@code (list_id, book_id)} 唯一索引（只有两个普通索引），
     * 所以重复只能由服务端挡；沿用 {@code BookTextbook} 重复建档返回 409 的口径。
     */
    private void requireBookNotDuplicated(Long listId, Long bookId, Long excludeItemId, String bookTitle)
    {
        Long same = baseMapper.countSameBook(listId, bookId, excludeItemId);
        if (same != null && same > 0)
        {
            throw BizErrors.duplicate("教材清单明细", "图书", bookTitle);
        }
    }

    /**
     * 算出剩余数与状态。
     *
     * @param item      明细（{@code arrivedQty} 允许为 null，按 0 处理）
     * @param pickedQty 已取数（新增传 0；修改传库中原值）
     */
    private void applyDerivedFields(TextbookListItem item, int pickedQty)
    {
        int arrivedQty = item.getArrivedQty() == null ? 0 : item.getArrivedQty();
        item.setRemainQty(arrivedQty - pickedQty);
        item.setStatus(deriveStatus(arrivedQty, pickedQty));
    }

    /**
     * 状态派生（用户 2026-09-22 裁决的口径，四态）：
     * <pre>
     *   arrivedQty == 0                     → 1 待到货
     *   arrivedQty &gt; 0 且 pickedQty == 0   → 2 已到货待取书
     *   0 &lt; pickedQty &lt; arrivedQty        → 3 部分取书
     *   pickedQty &gt;= arrivedQty &gt; 0     → 4 已取完
     * </pre>
     * 第 2 态是本轮新增（原 DDL 只有 1/2/3）—— 「货到了但一本未取」与「货还没到」
     * 对运营是两件事，前者可以催取书、后者只能等。
     */
    static int deriveStatus(int arrivedQty, int pickedQty)
    {
        if (arrivedQty <= 0)
        {
            return ITEM_STATUS_WAITING;
        }
        if (pickedQty <= 0)
        {
            return ITEM_STATUS_ARRIVED;
        }
        if (pickedQty < arrivedQty)
        {
            return ITEM_STATUS_PARTIAL;
        }
        return ITEM_STATUS_DONE;
    }

    /**
     * 已结束的清单是终态，不再接受新增教材。
     *
     * <p>修改与删除**不设这道闸** —— 到货标记与取书核销都发生在发布之后，
     * 结束清单时也可能还有漏记的到货要补。
     */
    private void requireNotFinished(TextbookList list)
    {
        if (list.getStatus() != null && list.getStatus() == TextbookListServiceImpl.STATUS_FINISHED)
        {
            throw BizErrors.ruleViolated("教材清单「" + list.getListName() + "」已结束，不能再新增教材");
        }
    }
}
