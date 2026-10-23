package com.tbgbook.bookstore.stock.service.impl;

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
import com.tbgbook.bookstore.stock.domain.StockTransfer;
import com.tbgbook.bookstore.stock.domain.StockTransferItem;
import com.tbgbook.bookstore.stock.mapper.StockTransferItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockTransferMapper;
import com.tbgbook.bookstore.stock.service.IStockTransferItemService;

import lombok.RequiredArgsConstructor;

/**
 * 库存调拨明细 Service 实现。
 *
 * <p>明细行的写操作有一条统一前置：<b>所属单据必须处于草稿状态</b>。
 * 与 {@code StockInItemServiceImpl} 同构，差异只在「没有派生列」——
 * 调拨明细只有 {@code bookId} / {@code qty}，{@code isbn} / {@code title} 是冗余回填。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockTransferItemServiceImpl extends ServiceImpl<StockTransferItemMapper, StockTransferItem>
        implements IStockTransferItemService
{
    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final StockTransferMapper transferMapper;

    @Override
    public List<StockTransferItem> selectStockTransferItemList(StockTransferItem item)
    {
        return baseMapper.selectStockTransferItemList(item);
    }

    @Override
    public StockTransferItem selectStockTransferItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "调拨单明细");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStockTransferItem(StockTransferItem item)
    {
        item.setItemId(null);
        // 本实体 POST/PUT 共用，故必填性一律在这里按**操作**判定：
        // 新增两个都要有；PUT 允许省略（沿用原行），见 updateStockTransferItem。
        // 用 @NotNull 办不到这件事 —— 注解不知道这次是哪种操作。
        if (item.getBookId() == null)
        {
            throw BizErrors.badRequest("图书不能为空");
        }
        if (item.getQty() == null)
        {
            throw BizErrors.badRequest("调拨数量不能为空");
        }
        StockTransfer parent = requireDraftParent(item.getTransferId());
        prepare(item);
        requireNoDuplicateBook(parent.getTransferId(), item.getBookId(), null);
        int rows = baseMapper.insert(item);
        // ⚠️ 刷新必须在 insert **之后** —— 放前面会按插入前的明细则算，汇总永远是旧的
        refreshSummary(parent.getTransferId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStockTransferItem(StockTransferItem item)
    {
        if (item.getItemId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的调拨单明细");
        }
        StockTransferItem current = referenceGuard.requireExists(baseMapper, item.getItemId(), "调拨单明细");
        // 父单据取自**库里那一行**而不是请求体：请求体可以伪造 transferId 指向一张别的草稿单
        StockTransfer parent = requireDraftParent(current.getTransferId());
        item.setTransferId(current.getTransferId());

        // 图书与数量没传就沿用原值 —— 只改数量时不该要求重传整行
        if (item.getBookId() == null)
        {
            item.setBookId(current.getBookId());
        }
        if (item.getQty() == null)
        {
            item.setQty(current.getQty());
        }
        prepare(item);
        requireNoDuplicateBook(current.getTransferId(), item.getBookId(), current.getItemId());

        int rows = baseMapper.updateById(item);
        referenceGuard.requireAffected(rows, "调拨单明细");
        refreshSummary(current.getTransferId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStockTransferItems(Long[] itemIds)
    {
        List<Long> idList = (itemIds == null) ? Collections.emptyList() : Arrays.asList(itemIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "调拨单明细");

        Set<Long> parents = new HashSet<>();
        for (Serializable id : ids)
        {
            StockTransferItem current = referenceGuard.requireExists(baseMapper, id, "调拨单明细");
            requireDraftParent(current.getTransferId());
            parents.add(current.getTransferId());
        }
        // 本表无 del_flag，删行即物理删（明细是单据的组成部分，没有独立生命期）
        int rows = baseMapper.deleteByIds(ids);
        for (Long transferId : parents)
        {
            refreshSummary(transferId);
        }
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 所属单据必须存在且处于草稿状态 */
    private StockTransfer requireDraftParent(Long transferId)
    {
        if (transferId == null)
        {
            throw BizErrors.badRequest("未指定所属调拨单");
        }
        StockTransfer parent = referenceGuard.requireExists(transferMapper, transferId, "调拨单");
        if (!Objects.equals(parent.getStatus(), StockTransfer.STATUS_DRAFT))
        {
            String state = Objects.equals(parent.getStatus(), StockTransfer.STATUS_TRANSFERRED)
                    ? "已调拨" : "已作废";
            throw BizErrors.ruleViolated("调拨单「" + parent.getTransferNo() + "」已" + state
                    + "，不能再改明细（库存是按当时的明细挪过的，改动会让单据与库存对不上）");
        }
        return parent;
    }

    /**
     * 校验引用、回填冗余列。
     *
     * <p>与 {@code StockTransferServiceImpl#prepareItems} 同一口径 —— 两处都要能独立写明细，
     * 校验规则必须一致，否则「开单时校验一次、事后补行再校验一次」会得出不同结论。
     */
    private void prepare(StockTransferItem item)
    {
        Book book = referenceGuard.requireExists(bookMapper, item.getBookId(), "图书");
        item.setIsbn(book.getIsbn());
        item.setTitle(book.getTitle());
        if (item.getQty() != null && item.getQty() <= 0)
        {
            throw BizErrors.ruleViolated("调拨数量必须大于 0（当前为 " + item.getQty() + "）");
        }
    }

    /**
     * 同一张调拨单里，同一本书只能有一行。
     *
     * <p>理由与 {@code StockTransferServiceImpl#prepareItems} 的查重一致：
     * 单据头只有一个调出 / 一个调入库位，同一本书拆两行只会产生两条重复流水。
     * 明细侧也必须拦 —— 只靠开单时的校验挡不住「事后单独加一行」这条路径。
     *
     * @param selfItemId 修改场景下要排除自身，新增传 {@code null}
     */
    private void requireNoDuplicateBook(Long transferId, Long bookId, Long selfItemId)
    {
        for (StockTransferItem row : selectItems(transferId))
        {
            if (Objects.equals(row.getBookId(), bookId) && !Objects.equals(row.getItemId(), selfItemId))
            {
                throw BizErrors.ruleViolated("「" + row.getTitle()
                        + "」在本单里已有一行，请改成那一行的数量，不要再加一行");
            }
        }
    }

    /** 按库里现有的明细则重算单据头的聚合列 */
    private void refreshSummary(Long transferId)
    {
        Set<Long> books = new HashSet<>();
        int qty = 0;
        for (StockTransferItem row : selectItems(transferId))
        {
            books.add(row.getBookId());
            qty += (row.getQty() == null) ? 0 : row.getQty();
        }

        StockTransfer patch = new StockTransfer();
        patch.setTransferId(transferId);
        patch.setBookKind(books.size());
        patch.setTotalQty(qty);
        transferMapper.updateById(patch);
    }

    private List<StockTransferItem> selectItems(Long transferId)
    {
        StockTransferItem query = new StockTransferItem();
        query.setTransferId(transferId);
        return baseMapper.selectStockTransferItemList(query);
    }
}
