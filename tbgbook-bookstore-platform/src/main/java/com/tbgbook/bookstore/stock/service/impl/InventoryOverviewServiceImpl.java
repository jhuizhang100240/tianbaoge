package com.tbgbook.bookstore.stock.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.support.IsbnUtils;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.stock.domain.CategoryStock;
import com.tbgbook.bookstore.stock.domain.ScanResult;
import com.tbgbook.bookstore.stock.domain.ScanStock;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockOverview;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.service.IInventoryOverviewService;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.common.utils.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 库存总览 服务层实现。
 *
 * <p>全程只读，故不加 {@code @Transactional}。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class InventoryOverviewServiceImpl implements IInventoryOverviewService
{
    /** 成本可见性权限串（根 CLAUDE.md §6.1 的 5 个成本权限之一） */
    private static final String COST_VIEW_PERM = "inventory:cost:view";

    /** 二手副本条码前缀（`biz_secondhand_stock.bar_code`） */
    private static final String USED_BARCODE_PREFIX = "BC";

    /** 占比保留位数 */
    private static final int RATIO_SCALE = 4;

    private final StockMapper stockMapper;

    private final BookMapper bookMapper;

    @Override
    public StockOverview summary(Long categoryId, Long locationId)
    {
        Stock query = new Stock();
        query.setCategoryId(categoryId);
        query.setLocationId(locationId);
        StockOverview overview = stockMapper.selectOverviewTotals(query);
        if (overview == null)
        {
            // SQL 已用 ifnull 兜底，理论上不会为 null；防的是空结果让调用方 NPE
            overview = new StockOverview();
        }
        overview.setWarningCounts(stockMapper.selectWarningCounts());
        if (!canViewCost())
        {
            // 无权限时置 null 而不是 0 —— 0 会被误读成「库存一文不值」
            overview.setTotalAmount(null);
        }
        return overview;
    }

    @Override
    public List<CategoryStock> byCategory(Long locationId)
    {
        Stock query = new Stock();
        query.setLocationId(locationId);
        List<CategoryStock> list = stockMapper.selectCategoryDistribution(query);
        boolean costVisible = canViewCost();
        if (!costVisible)
        {
            list.forEach(row -> row.setTotalAmount(null));
        }
        // 占比：优先按金额算 —— 但金额可能是「不可用」的，两种情形都要退化到按实物总量算：
        //   ① 调用方无 inventory:cost:view，totalAmount 已被裁成 null；
        //   ② 有权限但成本价全为 0（新库、尚未发生过入库 —— cost_price 由移动加权平均维护）。
        // 不退化的后果是占比全 0、饼图空白，而「各分类占多少货」本身是有意义的信息。
        // 用金额合计是否为 0 来判定，比「看权限」更准：它同时覆盖上面两种情形。
        BigDecimal amountTotal = list.stream().map(row -> nz(row.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean byAmount = costVisible && amountTotal.signum() != 0;
        BigDecimal total = byAmount ? amountTotal
                : list.stream().map(row -> BigDecimal.valueOf(nzi(row.getTotalQty())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        for (CategoryStock row : list)
        {
            BigDecimal part = byAmount ? nz(row.getTotalAmount())
                    : BigDecimal.valueOf(nzi(row.getTotalQty()));
            row.setRatio(total.signum() == 0 ? BigDecimal.ZERO
                    : part.divide(total, RATIO_SCALE, RoundingMode.HALF_UP));
        }
        return list;
    }

    @Override
    public ScanResult scan(String code)
    {
        String raw = StringUtils.trim(code);
        if (StringUtils.isBlank(raw))
        {
            throw BizErrors.badRequest("扫码内容不能为空");
        }

        // 二手副本条码走独立库存池，其数据模型属阶段六 —— 这里**明确拒绝**，
        // 而不是把它当正价书去查（那会静默返回「查无此书」，把「未接入」伪装成「不存在」）
        if (raw.toUpperCase().startsWith(USED_BARCODE_PREFIX))
        {
            throw BizErrors.notFound("条码",
                    raw + "（二手副本条码暂不支持查询：二手书库存池属阶段六，本接口当前只查正价书）");
        }

        Book book = findByIsbnOrBookNo(raw);
        if (book == null)
        {
            throw BizErrors.notFound("条码", raw);
        }

        List<ScanStock> stocks = stockMapper.selectStocksByBookId(book.getBookId());
        ScanResult result = new ScanResult();
        result.setBookType(ScanResult.TYPE_NORMAL);
        result.setBookId(book.getBookId());
        result.setTitle(book.getTitle());
        result.setPrice(book.getPrice());
        result.setCoverUrl(book.getCoverUrl());
        result.setStocks(stocks);
        result.setShelfHint(buildShelfHint(stocks));
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 先按 ISBN 查、再按图书编号查。
     *
     * <p>扫码枪两种码都可能扫到：ISBN 归一化后是 10/13 位数字，图书编号是 {@code BK} + 8 位。
     */
    private Book findByIsbnOrBookNo(String raw)
    {
        String isbn = IsbnUtils.normalize(raw);
        if (IsbnUtils.isFormatOk(isbn))
        {
            Book byIsbn = bookMapper.selectOne(Wrappers.<Book>lambdaQuery()
                    .eq(Book::getIsbn, isbn).last("limit 1"));
            if (byIsbn != null)
            {
                return byIsbn;
            }
        }
        return bookMapper.selectOne(Wrappers.<Book>lambdaQuery()
                .eq(Book::getBookNo, raw).last("limit 1"));
    }

    /**
     * 找书提示：取**可售数最多**的库位拼成「区域 + 库位编码（名称）」。
     *
     * <p>全部库位都无可售库存时返回 {@code null}（前端据此提示「暂无可售」）。
     * 由服务端拼而不是前端拼，是因为「哪个库位最该去」是个业务判断，不该散在页面里。
     */
    private String buildShelfHint(List<ScanStock> stocks)
    {
        ScanStock best = null;
        for (ScanStock stock : stocks)
        {
            if (nzi(stock.getQtyAvailable()) <= 0)
            {
                continue;
            }
            if (best == null || nzi(stock.getQtyAvailable()) > nzi(best.getQtyAvailable()))
            {
                best = stock;
            }
        }
        if (best == null)
        {
            return null;
        }
        StringBuilder hint = new StringBuilder();
        if (StringUtils.isNotBlank(best.getZone()))
        {
            hint.append(best.getZone()).append(' ');
        }
        hint.append(best.getLocationCode());
        if (StringUtils.isNotBlank(best.getLocationName()))
        {
            hint.append('（').append(best.getLocationName()).append('）');
        }
        return hint.toString();
    }

    /**
     * 成本可见性判断。
     *
     * <p>无登录上下文（定时任务 / 内部调用）时按「无权限」处理、不抛异常 ——
     * 与图书域 {@code BookServiceImpl#canViewCost} 同一口径。
     */
    private boolean canViewCost()
    {
        try
        {
            return SecurityUtils.hasPermi(COST_VIEW_PERM);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static BigDecimal nz(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nzi(Integer value)
    {
        return value == null ? 0 : value;
    }
}
