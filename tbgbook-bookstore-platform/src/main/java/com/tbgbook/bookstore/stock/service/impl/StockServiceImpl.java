package com.tbgbook.bookstore.stock.service.impl;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.mapper.LocationMapper;
import com.tbgbook.bookstore.stock.mapper.StockFlowMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.service.IStockService;

import lombok.RequiredArgsConstructor;

/**
 * 库存 服务层实现。
 *
 * <p><b>乐观锁在这里第一次真正跑起来</b>：阶段一任务 1.5 要验证 {@code @Version} 是否生效，
 * 但样板表 {@code biz_publisher} 没有 {@code version} 列，当时顺延至今。
 * 本表的 {@code version} 由 {@code OptimisticLockerInnerInterceptor} 保护 ——
 * 注意 **MP 在影响 0 行时不抛异常**，必须显式检查影响行数（{@code requireAffected}）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockServiceImpl extends ServiceImpl<StockMapper, Stock> implements IStockService
{
    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final LocationMapper locationMapper;

    private final StockFlowMapper stockFlowMapper;

    @Override
    public List<Stock> selectStockList(Stock stock)
    {
        return baseMapper.selectStockList(stock);
    }

    @Override
    public Stock selectStockById(Long stockId)
    {
        return referenceGuard.requireExists(baseMapper, stockId, "库存");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStock(Stock stock)
    {
        // 必填性按操作判定（实体上没有 @NotNull —— 那会把「改库位」的 PUT 也拦成 400）
        if (stock.getBookId() == null)
        {
            throw BizErrors.badRequest("图书ID不能为空");
        }
        if (stock.getLocationId() == null)
        {
            throw BizErrors.badRequest("库位ID不能为空");
        }
        // 引用存在性：图书必须存在；库位允许 0（=未分配库位），非 0 时校验存在
        referenceGuard.requireExists(bookMapper, stock.getBookId(), "图书");
        if (stock.getLocationId() != null && stock.getLocationId() > 0)
        {
            referenceGuard.requireExists(locationMapper, stock.getLocationId(), "库位");
        }

        // 四类数量与安全库存不接受传入：新行一律 0，之后由单据维护（FR-4.2 规则 1）
        stock.setQtyAvailable(0);
        stock.setQtyLocked(0);
        stock.setQtyBorrowed(0);
        stock.setQtyDamaged(0);
        stock.setSafetyQty(0);
        stock.setVersion(0);
        stock.setStoreId(null);

        requireBookLocationUnique(stock.getBookId(), stock.getLocationId(), null);
        return baseMapper.insert(stock);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStock(Stock stock)
    {
        Long stockId = stock.getStockId();
        if (stockId == null)
        {
            throw BizErrors.badRequest("未指定要修改的库存");
        }
        Stock current = referenceGuard.requireExists(baseMapper, stockId, "库存");

        // 只允许改库位；数量与版本不接受传入
        stock.setBookId(null);
        stock.setQtyAvailable(null);
        stock.setQtyLocked(null);
        stock.setQtyBorrowed(null);
        stock.setQtyDamaged(null);
        stock.setSafetyQty(null);
        stock.setStoreId(null);
        // ⚠️ 把**库里的版本号**带上，@Version 才会生成 `and version = ?`。
        //    客户端不传版本（契约把它放在 skip 里），所以这里由服务端读出来 ——
        //    若期间被他人改过，版本已变 → 影响 0 行 → 下面 requireAffected 抛 409
        stock.setVersion(current.getVersion());

        if (stock.getLocationId() != null)
        {
            if (stock.getLocationId() > 0)
            {
                referenceGuard.requireExists(locationMapper, stock.getLocationId(), "库位");
            }
            requireBookLocationUnique(current.getBookId(), stock.getLocationId(), stockId);
        }

        int rows = baseMapper.updateById(stock);
        referenceGuard.requireAffected(rows, "库存");
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStocks(Long[] stockIds)
    {
        Collection<? extends Serializable> ids = referenceGuard.requireIds(Arrays.asList(stockIds), "库存");
        for (Serializable id : ids)
        {
            Stock current = referenceGuard.requireExists(baseMapper, id, "库存");
            // 流水表按「图书+库位」定位库存行（没有 stock_id），故不能用 ReferenceGuard 的单列版本
            Long flowCount = stockFlowMapper.countByBookAndLocation(current.getBookId(), current.getLocationId());
            if (flowCount != null && flowCount > 0)
            {
                throw BizErrors.referenced("库存", "库存流水", flowCount, "保留该行（流水需可回溯）");
            }
        }
        return baseMapper.deleteByIds(ids);
    }

    /**
     * 「图书 + 库位」唯一（{@code uk_book_location}）。
     *
     * @param excludeId 修改时排除自身；新增传 {@code null}
     */
    private void requireBookLocationUnique(Long bookId, Long locationId, Long excludeId)
    {
        if (bookId == null || locationId == null)
        {
            return;
        }
        Long count = baseMapper.selectCount(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .eq(Stock::getLocationId, locationId)
                .ne(excludeId != null, Stock::getStockId, excludeId));
        if (count != null && count > 0)
        {
            throw BizErrors.duplicate("库存", "图书+库位", bookId + "/" + locationId);
        }
    }
}
