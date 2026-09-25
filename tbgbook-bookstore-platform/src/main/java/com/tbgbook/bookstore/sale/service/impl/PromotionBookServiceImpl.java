package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.Promotion;
import com.tbgbook.bookstore.sale.domain.PromotionBook;
import com.tbgbook.bookstore.sale.mapper.PromotionBookMapper;
import com.tbgbook.bookstore.sale.mapper.PromotionMapper;
import com.tbgbook.bookstore.sale.service.IPromotionBookService;

import lombok.RequiredArgsConstructor;

/**
 * 促销活动商品 Service 实现。
 *
 * <p>明细行的写操作有一条统一前置：<b>所属活动必须「非进行中」</b>。
 * 与 {@code PromotionServiceImpl#requireEditable} 同一规则，且**明细侧必须独立再判一次**
 * —— 只靠活动侧的校验挡不住「直接对商品行发 PUT」这条路径
 * （与 {@code StockInItemServiceImpl} 的处置一致）。
 *
 * <p><b>两处额外校验</b>：
 * <ol>
 *   <li>图书必须**未被停用**（{@code status} 为 2下架 / 4停售 时报 422）—— 契约明写。
 *       给一本不卖的书配活动价没有意义</li>
 *   <li>同一活动内图书不重复（{@code uk_promotion_book}）—— 预检后抛 409，
 *       而不是等数据库唯一索引报错（那样冒到兜底会变成不友好的文案）</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class PromotionBookServiceImpl extends ServiceImpl<PromotionBookMapper, PromotionBook>
        implements IPromotionBookService
{
    /** 图书状态：下架 */
    private static final int BOOK_STATUS_OFF_SHELF = 2;

    /** 图书状态：停售 */
    private static final int BOOK_STATUS_STOPPED = 4;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final PromotionMapper promotionMapper;

    @Override
    public List<PromotionBook> selectPromotionBookList(PromotionBook item)
    {
        return baseMapper.selectPromotionBookList(item);
    }

    @Override
    public PromotionBook selectPromotionBookById(Long relId)
    {
        return referenceGuard.requireExists(baseMapper, relId, "活动商品");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addPromotionBook(PromotionBook item)
    {
        item.setRelId(null);
        // 本实体 POST/PUT 共用，故必填性在这里按**操作**判定
        if (item.getBookId() == null)
        {
            throw BizErrors.badRequest("图书不能为空");
        }
        Promotion parent = requireEditableParent(item.getPromotionId());
        requireUsableBook(item.getBookId());
        requireBookNotDuplicated(parent.getPromotionId(), item.getBookId(), null);

        int rows = baseMapper.insert(item);
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePromotionBook(PromotionBook item)
    {
        if (item.getRelId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的活动商品");
        }
        PromotionBook current = referenceGuard.requireExists(baseMapper, item.getRelId(), "活动商品");
        // 父活动取自**库里那一行**而不是请求体：请求体可以伪造 promotionId 指向另一个活动
        Promotion parent = requireEditableParent(current.getPromotionId());
        item.setPromotionId(current.getPromotionId());

        // 图书没传就沿用原值 —— 只改活动价时不该要求重传
        if (item.getBookId() == null)
        {
            item.setBookId(current.getBookId());
        }
        requireUsableBook(item.getBookId());
        requireBookNotDuplicated(current.getPromotionId(), item.getBookId(), current.getRelId());

        int rows = baseMapper.updateById(item);
        referenceGuard.requireAffected(rows, "活动商品");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removePromotionBooks(Long[] relIds)
    {
        List<Long> idList = (relIds == null) ? Collections.emptyList() : Arrays.asList(relIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "活动商品");

        Set<Long> parents = new HashSet<>();
        for (Serializable id : ids)
        {
            PromotionBook current = referenceGuard.requireExists(baseMapper, id, "活动商品");
            requireEditableParent(current.getPromotionId());
            parents.add(current.getPromotionId());
        }
        // 本表无 del_flag，删行即物理删（关系行只有「在 / 不在」两种状态）
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 所属活动必须存在且**非进行中** */
    private Promotion requireEditableParent(Long promotionId)
    {
        if (promotionId == null)
        {
            throw BizErrors.badRequest("未指定所属促销活动");
        }
        Promotion parent = referenceGuard.requireExists(promotionMapper, promotionId, "促销活动");
        if (Objects.equals(parent.getStatus(), Promotion.STATUS_RUNNING))
        {
            throw BizErrors.ruleViolated("活动「" + parent.getPromotionName()
                    + "」正在进行中，不能增删改活动商品 —— 正在影响售价，请先停用");
        }
        return parent;
    }

    /**
     * 图书必须存在且**未被停用**（契约明写：设置活动商品时需校验图书未被停用）。
     *
     * <p>下架（2）与停售（4）都不允许 —— 给一本不卖的书配活动价没有意义。
     */
    private void requireUsableBook(Long bookId)
    {
        Book book = referenceGuard.requireExists(bookMapper, bookId, "图书");
        Integer status = book.getStatus() == null ? null : Integer.valueOf(book.getStatus());
        if (Objects.equals(status, BOOK_STATUS_OFF_SHELF)
                || Objects.equals(status, BOOK_STATUS_STOPPED))
        {
            throw BizErrors.ruleViolated("「" + book.getTitle() + "」已"
                    + (Objects.equals(status, BOOK_STATUS_OFF_SHELF) ? "下架" : "停售")
                    + "，不能加入促销活动 —— 请先在图书管理里恢复为在售");
        }
    }

    /** 同一活动内图书不可重复（`uk_promotion_book`），预检后给友好文案而不是等唯一索引报错 */
    private void requireBookNotDuplicated(Long promotionId, Long bookId, Long selfRelId)
    {
        PromotionBook query = new PromotionBook();
        query.setPromotionId(promotionId);
        query.setBookId(bookId);
        for (PromotionBook row : baseMapper.selectPromotionBookList(query))
        {
            if (!Objects.equals(row.getRelId(), selfRelId))
            {
                throw BizErrors.conflict("「" + row.getTitle() + "」已经在本活动里了，"
                        + "请直接修改那一行的活动价，不要重复添加");
            }
        }
    }
}
