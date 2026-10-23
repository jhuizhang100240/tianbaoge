package com.tbgbook.bookstore.stock.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.LossApply;
import com.tbgbook.bookstore.stock.domain.LossApplyItem;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.mapper.LossApplyItemMapper;
import com.tbgbook.bookstore.stock.mapper.LossApplyMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.service.ILossApplyItemService;
import com.tbgbook.bookstore.stock.service.ILossApplyService;

import lombok.RequiredArgsConstructor;

/**
 * 报损申请明细 Service 实现。
 *
 * <p>明细行的写操作有一条统一前置：<b>所属单据必须处于待审批状态</b>。
 * 与 {@code StockInItemServiceImpl} 同构，但多挡两种终态（已驳回 / 已撤销）——
 * 入库单只有「草稿 / 已入库 / 已作废」三态，而报损单有四态，
 * 且终态有**两个**（驳回、撤销），不是「非草稿即已入库」的二选一。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class LossApplyItemServiceImpl extends ServiceImpl<LossApplyItemMapper, LossApplyItem>
        implements ILossApplyItemService
{
    /** 金额列一律两位小数（`decimal(12,2)`） */
    private static final int AMOUNT_SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final StockMapper stockMapper;

    private final LossApplyMapper applyMapper;

    private final ILossApplyService lossApplyService;

    @Override
    public List<LossApplyItem> selectLossApplyItemList(LossApplyItem item)
    {
        return baseMapper.selectLossApplyItemList(item);
    }

    @Override
    public LossApplyItem selectLossApplyItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "报损明细");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addLossApplyItem(LossApplyItem item)
    {
        item.setItemId(null);
        // 本实体 POST/PUT 共用，故必填性一律在这里按**操作**判定：
        // 新增两个都要有；PUT 允许省略（沿用原行），见 updateLossApplyItem。
        if (item.getBookId() == null)
        {
            throw BizErrors.badRequest("图书不能为空");
        }
        if (item.getQty() == null)
        {
            throw BizErrors.badRequest("报损数量不能为空");
        }
        LossApply parent = requirePendingParent(item.getApplyId());
        prepare(item);
        int rows = baseMapper.insert(item);
        // ⚠️ 刷新必须在 insert **之后** —— 放前面会按插入前的明细则算，汇总永远是旧的
        refreshSummary(parent.getApplyId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateLossApplyItem(LossApplyItem item)
    {
        if (item.getItemId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的报损明细");
        }
        LossApplyItem current = referenceGuard.requireExists(baseMapper, item.getItemId(), "报损明细");
        // 父单据取自**库里那一行**而不是请求体：请求体可以伪造 applyId 指向一张别的待审批单
        LossApply parent = requirePendingParent(current.getApplyId());
        item.setApplyId(current.getApplyId());

        // 图书与数量没传就沿用原值 —— 只改数量时不该要求重传整行
        if (item.getBookId() == null)
        {
            item.setBookId(current.getBookId());
        }
        if (item.getQty() == null)
        {
            item.setQty(current.getQty());
        }
        if (item.getLocationId() == null)
        {
            item.setLocationId(current.getLocationId());
        }
        prepare(item);

        int rows = baseMapper.updateById(item);
        referenceGuard.requireAffected(rows, "报损明细");
        refreshSummary(current.getApplyId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeLossApplyItems(Long[] itemIds)
    {
        List<Long> idList = (itemIds == null) ? Collections.emptyList() : Arrays.asList(itemIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "报损明细");

        Set<Long> parents = new HashSet<>();
        for (Serializable id : ids)
        {
            LossApplyItem current = referenceGuard.requireExists(baseMapper, id, "报损明细");
            requirePendingParent(current.getApplyId());
            parents.add(current.getApplyId());
        }
        // 本表无 del_flag，删行即物理删（明细是单据的组成部分，没有独立生命期）
        int rows = baseMapper.deleteByIds(ids);
        for (Long applyId : parents)
        {
            refreshSummary(applyId);
        }
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 所属单据必须存在且处于待审批状态 */
    private LossApply requirePendingParent(Long applyId)
    {
        if (applyId == null)
        {
            throw BizErrors.badRequest("未指定所属报损单");
        }
        LossApply parent = referenceGuard.requireExists(applyMapper, applyId, "报损单");
        if (!Objects.equals(parent.getStatus(), LossApply.STATUS_PENDING))
        {
            throw BizErrors.ruleViolated("报损单「" + parent.getApplyNo() + "」不是待审批状态，"
                    + "不能再改明细（要重新报损请新开一单，这样审批链才完整）");
        }
        return parent;
    }

    /**
     * 校验引用、回填冗余列、取成本单价与库存快照、计算金额。
     *
     * <p>与 {@code LossApplyServiceImpl#prepareItems} 同一口径 —— 两处都要能独立写明细，
     * 派生列的算法必须一致，否则「开单时算一次、事后补行再算一次」会得出不同的数。
     *
     * <p>⚠️ {@code unitCost} 取 {@code biz_book.cost_price}，**不接受客户端传入** ——
     * 理由见 {@code LossApplyItem} 类注释（它决定「超 500 元需二次确认」这道闸门）。
     */
    private void prepare(LossApplyItem item)
    {
        Book book = referenceGuard.requireExists(bookMapper, item.getBookId(), "图书");
        item.setIsbn(book.getIsbn());
        item.setTitle(book.getTitle());
        if (item.getQty() != null && item.getQty() <= 0)
        {
            throw BizErrors.ruleViolated("报损数量必须大于 0（当前为 " + item.getQty() + "）");
        }
        if (item.getLocationId() == null || item.getLocationId() < 0)
        {
            item.setLocationId(0L);
        }

        BigDecimal unitCost = (book.getCostPrice() == null) ? BigDecimal.ZERO : book.getCostPrice();
        item.setUnitCost(unitCost);
        item.setAmount(unitCost.multiply(BigDecimal.valueOf(item.getQty()))
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        item.setStockQty(availableOf(item.getBookId(), item.getLocationId()));
    }

    /**
     * 按库里现有的明细则重算单据头的聚合列与 {@code needConfirm}。
     *
     * <p>{@code needConfirm} 必须一并重算 —— 加一行明细把金额推过阈值，
     * 标志却还是 0 的话，「超 500 需二次确认」这道闸门就被绕过了。
     * 阈值比较走 {@code compareTo}（理由见 {@code LossApplyServiceImpl#summarize}）。
     */
    private void refreshSummary(Long applyId)
    {
        Set<Long> books = new HashSet<>();
        int qty = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (LossApplyItem row : selectItems(applyId))
        {
            books.add(row.getBookId());
            qty += (row.getQty() == null) ? 0 : row.getQty();
            amount = amount.add(row.getAmount() == null ? BigDecimal.ZERO : row.getAmount());
        }
        BigDecimal total = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        LossApply patch = new LossApply();
        patch.setApplyId(applyId);
        patch.setBookKind(books.size());
        patch.setTotalQty(qty);
        patch.setTotalAmount(total);
        patch.setNeedConfirm(total.compareTo(lossApplyService.confirmAmountThreshold()) > 0 ? 1 : 0);
        applyMapper.updateById(patch);
    }

    private List<LossApplyItem> selectItems(Long applyId)
    {
        LossApplyItem query = new LossApplyItem();
        query.setApplyId(applyId);
        return baseMapper.selectLossApplyItemList(query);
    }

    /** 某本书在某库位的可售数（无库存行时视为 0） */
    private int availableOf(Long bookId, Long locationId)
    {
        Stock row = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .eq(Stock::getLocationId, locationId));
        return (row == null || row.getQtyAvailable() == null) ? 0 : row.getQtyAvailable();
    }
}
