package com.tbgbook.bookstore.stock.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.StockIn;
import com.tbgbook.bookstore.stock.domain.StockInItem;
import com.tbgbook.bookstore.stock.mapper.LocationMapper;
import com.tbgbook.bookstore.stock.mapper.StockInItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockInMapper;
import com.tbgbook.bookstore.stock.service.IStockInItemService;

import lombok.RequiredArgsConstructor;

/**
 * 采购入库单明细 Service 实现。
 *
 * <p>明细行的写操作有一条统一前置：<b>所属单据必须处于草稿状态</b>。
 * 已入库的单据改明细会让库存与单据对不上（库存是按当时的明细加过的），
 * 而这一点必须**在明细这一侧也拦一道** —— 只靠单据侧的校验挡不住
 * 「直接对明细发 PUT」这条路径。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockInItemServiceImpl extends ServiceImpl<StockInItemMapper, StockInItem>
        implements IStockInItemService
{
    private static final int AMOUNT_SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final LocationMapper locationMapper;

    private final StockInMapper stockInMapper;

    @Override
    public List<StockInItem> selectStockInItemList(StockInItem item)
    {
        return baseMapper.selectStockInItemList(item);
    }

    @Override
    public StockInItem selectStockInItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "入库单明细");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStockInItem(StockInItem item)
    {
        item.setItemId(null);
        // 本实体 POST/PUT 共用，故必填性一律在这里按**操作**判定：
        // 新增三个都要有；PUT 允许省略（沿用原行），见 updateStockInItem。
        // 用 @NotNull 办不到这件事 —— 注解不知道这次是哪种操作（与 Stock 上踩过的是同一个坑）。
        if (item.getBookId() == null)
        {
            throw BizErrors.badRequest("图书不能为空");
        }
        if (item.getOrderQty() == null)
        {
            throw BizErrors.badRequest("采购数量不能为空");
        }
        if (item.getUnitCost() == null)
        {
            throw BizErrors.badRequest("成本单价不能为空");
        }
        StockIn parent = requireDraftParent(item.getInId());
        prepare(item, parent);
        int rows = baseMapper.insert(item);
        // ⚠️ 刷新必须在 insert **之后** —— 放前面会按插入前的明细则算，汇总永远是旧的
        refreshSummary(parent.getInId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStockInItem(StockInItem item)
    {
        if (item.getItemId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的入库单明细");
        }
        StockInItem current = referenceGuard.requireExists(baseMapper, item.getItemId(), "入库单明细");
        // 父单据取自**库里那一行**而不是请求体：请求体可以伪造 inId 指向一张别的草稿单
        StockIn parent = requireDraftParent(current.getInId());
        item.setInId(current.getInId());

        // 商品与库位没传就沿用原值 —— 只改数量时不该要求重传整行
        if (item.getBookId() == null)
        {
            item.setBookId(current.getBookId());
        }
        if (item.getOrderQty() == null)
        {
            item.setOrderQty(current.getOrderQty());
        }
        if (item.getUnitCost() == null)
        {
            item.setUnitCost(current.getUnitCost());
        }
        prepare(item, parent);

        int rows = baseMapper.updateById(item);
        referenceGuard.requireAffected(rows, "入库单明细");
        refreshSummary(current.getInId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStockInItems(Long[] itemIds)
    {
        List<Long> idList = (itemIds == null) ? Collections.emptyList() : Arrays.asList(itemIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "入库单明细");

        java.util.Set<Long> parents = new java.util.HashSet<>();
        for (Serializable id : ids)
        {
            StockInItem current = referenceGuard.requireExists(baseMapper, id, "入库单明细");
            requireDraftParent(current.getInId());
            parents.add(current.getInId());
        }
        // 本表无 del_flag，删行即物理删（明细是单据的组成部分，没有独立生命期）
        int rows = baseMapper.deleteByIds(ids);
        for (Long inId : parents)
        {
            refreshSummary(inId);
        }
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 所属单据必须存在且处于草稿状态 */
    private StockIn requireDraftParent(Long inId)
    {
        if (inId == null)
        {
            throw BizErrors.badRequest("未指定所属入库单");
        }
        StockIn parent = referenceGuard.requireExists(stockInMapper, inId, "入库单");
        if (!Objects.equals(parent.getStatus(), StockIn.STATUS_DRAFT))
        {
            String state = Objects.equals(parent.getStatus(), StockIn.STATUS_CONFIRMED) ? "已入库" : "已作废";
            throw BizErrors.ruleViolated("入库单「" + parent.getInNo() + "」已" + state
                    + "，不能再改明细（库存是按当时的明细加过的，改动会让单据与库存对不上）");
        }
        return parent;
    }

    /**
     * 校验引用、回填冗余列、计算派生列。
     *
     * <p>与 {@code StockInServiceImpl#prepareItems} 同一口径 —— 两处都要能独立写明细，
     * 派生列的算法必须一致，否则「开单时算一次、事后补行再算一次」会得出不同的数。
     */
    private void prepare(StockInItem item, StockIn parent)
    {
        Book book = referenceGuard.requireExists(bookMapper, item.getBookId(), "图书");
        item.setIsbn(book.getIsbn());
        item.setTitle(book.getTitle());

        if (item.getLocationId() != null && item.getLocationId() > 0)
        {
            referenceGuard.requireExists(locationMapper, item.getLocationId(), "库位");
        }
        else
        {
            item.setLocationId(0L);
        }

        if (item.getOrderQty() == null)
        {
            item.setOrderQty(0);
        }
        if (item.getActualQty() == null)
        {
            item.setActualQty(item.getOrderQty());
        }
        item.setDiffQty(item.getActualQty() - item.getOrderQty());
        item.setAmount(item.getUnitCost().multiply(BigDecimal.valueOf(item.getActualQty()))
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
    }

    /** 按库里现有的明细则重算单据头的聚合列 */
    private void refreshSummary(Long inId)
    {
        StockInItem query = new StockInItem();
        query.setInId(inId);
        List<StockInItem> items = baseMapper.selectStockInItemList(query);

        int qty = 0;
        BigDecimal amount = BigDecimal.ZERO;
        java.util.Set<Long> books = new java.util.HashSet<>();
        for (StockInItem row : items)
        {
            books.add(row.getBookId());
            qty += (row.getActualQty() == null) ? 0 : row.getActualQty();
            amount = amount.add(row.getAmount() == null ? BigDecimal.ZERO : row.getAmount());
        }

        StockIn patch = new StockIn();
        patch.setInId(inId);
        patch.setBookKind(books.size());
        patch.setTotalQty(qty);
        patch.setTotalAmount(amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
        stockInMapper.updateById(patch);
    }
}
