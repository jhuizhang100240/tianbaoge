package com.tbgbook.bookstore.stock.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockCheck;
import com.tbgbook.bookstore.stock.domain.StockCheckItem;
import com.tbgbook.bookstore.stock.mapper.LocationMapper;
import com.tbgbook.bookstore.stock.mapper.StockCheckItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockCheckMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.service.IStockCheckItemService;
import com.tbgbook.bookstore.stock.service.IStockCheckService;

import lombok.RequiredArgsConstructor;

/**
 * 库存盘点明细 Service 实现。
 *
 * <p><b>派生列的口径必须与 {@code StockCheckServiceImpl#prepareItems} 一致</b> ——
 * 两条路径都能写明细（单头带明细一起提交 / 明细页单独增删），口径不一致的话
 * 「范围生成的单」与「手工补的行」算法会不同。
 * 口径定义见 {@link StockCheckItem} 的类注释（账面数 = 在场实物量 = 可售+锁定+残损）。
 *
 * <p>每写完一条明细都调 {@code IStockCheckService#refreshStats} 重算单据头统计列 ——
 * 否则「明细页加一行、列表页的品种数与差异率还是旧的」。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockCheckItemServiceImpl extends ServiceImpl<StockCheckItemMapper, StockCheckItem>
        implements IStockCheckItemService
{
    private static final int AMOUNT_SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final LocationMapper locationMapper;

    private final StockMapper stockMapper;

    private final StockCheckMapper stockCheckMapper;

    private final IStockCheckService stockCheckService;

    @Override
    public List<StockCheckItem> selectStockCheckItemList(StockCheckItem item)
    {
        return baseMapper.selectStockCheckItemList(item);
    }

    @Override
    public StockCheckItem selectStockCheckItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "盘点明细");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStockCheckItem(StockCheckItem item)
    {
        item.setItemId(null);
        if (item.getBookId() == null)
        {
            // 本实体 POST/PUT 共用，必填性在 service 按操作判定（PUT 允许沿用原行的）
            throw BizErrors.badRequest("图书不能为空");
        }
        requireRunningParent(item.getCheckId());
        prepare(item);
        int rows = baseMapper.insert(item);
        stockCheckService.refreshStats(item.getCheckId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStockCheckItem(StockCheckItem item)
    {
        if (item.getItemId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的盘点明细");
        }
        StockCheckItem current = referenceGuard.requireExists(baseMapper, item.getItemId(), "盘点明细");
        // 父单据取自**库里那一行**而不是请求体：请求体可以伪造 checkId 指向别的单
        requireRunningParent(current.getCheckId());
        item.setCheckId(current.getCheckId());

        // 图书与库位没传就沿用原值 —— 只改实盘数时不该要求重传整行
        if (item.getBookId() == null)
        {
            item.setBookId(current.getBookId());
        }
        if (item.getLocationId() == null)
        {
            item.setLocationId(current.getLocationId());
        }
        if (item.getActualQty() == null)
        {
            item.setActualQty(current.getActualQty());
        }
        prepare(item);

        int rows = baseMapper.updateById(item);
        referenceGuard.requireAffected(rows, "盘点明细");
        stockCheckService.refreshStats(current.getCheckId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStockCheckItems(Long[] itemIds)
    {
        List<Long> idList = (itemIds == null) ? Collections.emptyList() : Arrays.asList(itemIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "盘点明细");

        Set<Long> parents = new LinkedHashSet<>();
        for (Serializable id : ids)
        {
            StockCheckItem current = referenceGuard.requireExists(baseMapper, id, "盘点明细");
            requireRunningParent(current.getCheckId());
            parents.add(current.getCheckId());
        }
        int rows = baseMapper.deleteByIds(ids);
        for (Long checkId : parents)
        {
            stockCheckService.refreshStats(checkId);
        }
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    private void requireRunningParent(Long checkId)
    {
        if (checkId == null)
        {
            throw BizErrors.badRequest("未指定所属盘点单");
        }
        StockCheck parent = referenceGuard.requireExists(stockCheckMapper, checkId, "盘点单");
        if (!Objects.equals(parent.getStatus(), StockCheck.STATUS_RUNNING))
        {
            throw BizErrors.ruleViolated("盘点单「" + parent.getCheckNo()
                    + "」不处于进行中，不能再改明细（差异若已应用，改动会让单据与库存对不上）");
        }
    }

    /** 校验引用、回填冗余列、快照账面数、算派生列（口径见类注释） */
    private void prepare(StockCheckItem item)
    {
        Book book = referenceGuard.requireExists(bookMapper, item.getBookId(), "图书");
        item.setIsbn(book.getIsbn());
        item.setTitle(book.getTitle());

        if (item.getLocationId() == null || item.getLocationId() <= 0)
        {
            item.setLocationId(0L);
        }
        else
        {
            referenceGuard.requireExists(locationMapper, item.getLocationId(), "库位");
        }

        Stock stock = stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, item.getBookId())
                .eq(Stock::getLocationId, item.getLocationId()));
        // 账面数由服务端快照，不接受客户端传入 —— 那是判定盈亏的基准
        int bookQty = (stock == null) ? 0
                : nzi(stock.getQtyAvailable()) + nzi(stock.getQtyLocked()) + nzi(stock.getQtyDamaged());
        item.setBookQty(bookQty);

        // ⚠️ **本路径只定义「盘哪些行」，不录入实盘数**（理由见
        //    StockCheckServiceImpl#prepareItems）：实盘数一律走
        //    `POST /inventory/stockcheck/{checkId}/items`。
        //    这里若接受 actualQty，「默认 0 的表单」会把每行都算成盘亏。
        item.setActualQty(0);
        item.setDiffQty(0);
        item.setDiffType(StockCheckItem.DIFF_NONE);
        item.setDiffAmount(BigDecimal.ZERO);
        item.setCheckTime(null);
        if (item.getIsPeriodDiff() == null)
        {
            item.setIsPeriodDiff(0);
        }
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }
}
