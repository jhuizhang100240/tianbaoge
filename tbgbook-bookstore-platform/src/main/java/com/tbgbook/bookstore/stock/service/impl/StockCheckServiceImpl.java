package com.tbgbook.bookstore.stock.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.tbgbook.bookstore.book.domain.Category;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.mapper.CategoryMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockCheck;
import com.tbgbook.bookstore.stock.domain.StockCheckDiffReport;
import com.tbgbook.bookstore.stock.domain.StockCheckGenerateRequest;
import com.tbgbook.bookstore.stock.domain.StockCheckItem;
import com.tbgbook.bookstore.stock.domain.StockCheckItemEntry;
import com.tbgbook.bookstore.stock.domain.StockCheckItemsRequest;
import com.tbgbook.bookstore.stock.domain.StockCheckRecheckResult;
import com.tbgbook.bookstore.stock.domain.StockFlow;
import com.tbgbook.bookstore.stock.mapper.LocationMapper;
import com.tbgbook.bookstore.stock.mapper.StockCheckItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockCheckMapper;
import com.tbgbook.bookstore.stock.mapper.StockFlowMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.service.IStockCheckService;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 库存盘点单 Service 实现（阶段三步骤③）。
 *
 * <p><b>与步骤②（采购入库单）的关系</b>：入库是「无到有」加库存，盘点是「按实际校准」。
 * 两者同一套骨架 —— 单据动作才改库存、每次改动都写 {@link StockFlow 流水}、
 * 乐观锁冲突显式检查。差别在盘点要处理**盘点期差异**（§4.2 规则 5）：
 * 盘点时店还在营业，书可能被卖掉或调走，那部分差异不该算在盘点人头上。
 * 见 {@link #finish} 里「为什么应用的是实盘−当前而不是实盘−快照」。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockCheckServiceImpl extends ServiceImpl<StockCheckMapper, StockCheck>
        implements IStockCheckService
{
    /** 盘点单号前缀（需求 §3.2 编码规则：PD + yyyyMMdd + 流水） */
    private static final String CHECK_NO_PREFIX = "PD";

    /** 差异率阈值的 `sys_config` 键（§3.2 库存规则 4，默认 2%） */
    private static final String CFG_DIFF_RATE = "biz.stocktake.diff.rate";

    /** 阈值兜底值（配置缺失时用，与 `04_ruoyi_custom.sql` 的种子值一致） */
    private static final BigDecimal DEFAULT_DIFF_RATE = new BigDecimal("2");

    /** 差异率小数位（`diff_rate` 是 `decimal(6,2)`） */
    private static final int RATE_SCALE = 2;

    /** `biz_stock_flow.stock_type`：可售（差异调整只落在可售上） */
    private static final int STOCK_TYPE_AVAILABLE = 1;

    /** `biz_stock_flow.biz_type`：盘盈 */
    private static final int BIZ_TYPE_CHECK_GAIN = 7;

    /** `biz_stock_flow.biz_type`：盘亏 */
    private static final int BIZ_TYPE_CHECK_LOSS = 8;

    /** 二次盘点在原单备注里留的标记（本表没有 `origin_check_id` 列） */
    private static final String RECHECK_MARK = "【二次盘点】源自 ";

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final CategoryMapper categoryMapper;

    private final StockMapper stockMapper;

    private final LocationMapper locationMapper;

    private final StockFlowMapper stockFlowMapper;

    private final StockCheckItemMapper itemMapper;

    private final ISysConfigService configService;

    @Override
    public BigDecimal diffRateThreshold()
    {
        // NFR-M4/R7：阈值必须运营可配，不得写死在 Java 里。走 ISysConfigService（带 Redis 缓存）
        String raw = configService.selectConfigByKey(CFG_DIFF_RATE);
        if (raw == null || raw.isBlank())
        {
            return DEFAULT_DIFF_RATE;
        }
        try
        {
            return new BigDecimal(raw.trim());
        }
        catch (NumberFormatException e)
        {
            // 运营把参数改坏了不该让盘点功能整体不可用 —— 回落默认值并继续
            return DEFAULT_DIFF_RATE;
        }
    }

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<StockCheck> selectStockCheckList(StockCheck stockCheck)
    {
        return baseMapper.selectStockCheckList(stockCheck);
    }

    @Override
    public StockCheck selectStockCheckById(Long checkId)
    {
        StockCheck check = referenceGuard.requireExists(baseMapper, checkId, "盘点单");
        check.setItems(maskBookQty(check, selectItems(checkId)));
        return check;
    }

    /** 按需求 §3.2 生成盘点单号：{@code PD} + yyyyMMdd + 6 位流水 */
    private String buildCheckNo(Long checkId)
    {
        return CHECK_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", checkId);
    }

    // ==================================================================
    // 标准 CRUD（日常开单请走 generate —— 它会自己解析范围并快照账面数）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStockCheck(StockCheck stockCheck)
    {
        requireItems(stockCheck.getItems());
        // 服务端接管字段，客户端传了也不认
        stockCheck.setCheckId(null);
        stockCheck.setCheckNo(null);
        stockCheck.setStatus(StockCheck.STATUS_RUNNING);
        stockCheck.setStartTime(LocalDateTime.now());
        stockCheck.setEndTime(null);
        stockCheck.setNeedRecheck(0);
        if (stockCheck.getShowBookQty() == null)
        {
            // 契约默认盲盘（不显示账面数）—— 让盘点人独立报数，避免照着账面抄
            stockCheck.setShowBookQty(0);
        }
        prepareItems(stockCheck.getItems());

        Long predictedId = baseMapper.selectNextCheckId();
        stockCheck.setCheckNo(buildCheckNo(predictedId == null ? 1L : predictedId));
        int rows = baseMapper.insert(stockCheck);
        persistItems(stockCheck.getCheckId(), stockCheck.getItems());

        String actualNo = buildCheckNo(stockCheck.getCheckId());
        if (!actualNo.equals(stockCheck.getCheckNo()))
        {
            StockCheck patch = new StockCheck();
            patch.setCheckId(stockCheck.getCheckId());
            patch.setCheckNo(actualNo);
            baseMapper.updateById(patch);
            stockCheck.setCheckNo(actualNo);
        }
        refreshStats(stockCheck.getCheckId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStockCheck(StockCheck stockCheck)
    {
        if (stockCheck.getCheckId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的盘点单");
        }
        StockCheck current = referenceGuard.requireExists(baseMapper, stockCheck.getCheckId(), "盘点单");
        // 已应用差异的单据不能再改明细 —— 库存是按当时的实盘数调过了
        referenceGuard.requireState(current.getStatus(), StockCheck.STATUS_RUNNING, "盘点单", "进行中");

        stockCheck.setCheckNo(null);
        stockCheck.setStatus(null);
        stockCheck.setStartTime(null);
        stockCheck.setEndTime(null);
        // 统计列一律由明细聚合，不接受客户端传入（契约 skip）。置 null 让 MP 跳过它们
        stockCheck.setBookKindTotal(null);
        stockCheck.setBookKindChecked(null);
        stockCheck.setQtyBookTotal(null);
        stockCheck.setQtyActualTotal(null);
        stockCheck.setQtyGain(null);
        stockCheck.setQtyLoss(null);
        stockCheck.setDiffKindCount(null);
        stockCheck.setDiffRate(null);
        stockCheck.setNeedRecheck(null);

        if (stockCheck.getItems() != null)
        {
            requireItems(stockCheck.getItems());
            prepareItems(stockCheck.getItems());
            itemMapper.deleteByCheckIds(new Long[] { stockCheck.getCheckId() });
            persistItems(stockCheck.getCheckId(), stockCheck.getItems());
        }

        int rows = baseMapper.updateById(stockCheck);
        referenceGuard.requireAffected(rows, "盘点单");
        refreshStats(stockCheck.getCheckId());
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStockChecks(Long[] checkIds)
    {
        List<Long> idList = (checkIds == null) ? Collections.emptyList() : Arrays.asList(checkIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "盘点单");

        for (Serializable id : ids)
        {
            StockCheck one = referenceGuard.requireExists(baseMapper, id, "盘点单");
            // 已完成/待复核的不能删：差异已经应用到库存，明细是那次盘点的唯一凭证
            if (!Objects.equals(one.getStatus(), StockCheck.STATUS_RUNNING)
                    && !Objects.equals(one.getStatus(), StockCheck.STATUS_VOIDED))
            {
                throw BizErrors.ruleViolated("盘点单「" + one.getCheckNo()
                        + "」不是进行中或已作废状态，不能删除 —— 它的差异已经应用到库存，"
                        + "明细是那次盘点的唯一凭证；如需撤销请另开盘点单校正");
            }
        }
        itemMapper.deleteByCheckIds(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 生成：按范围快照账面数
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCheck generate(StockCheckGenerateRequest request)
    {
        List<Stock> scope = resolveScope(request.getScopeType(), request.getScopeValue());
        if (scope.isEmpty())
        {
            throw BizErrors.ruleViolated("该范围内没有库存记录，无法生成盘点单"
                    + "（盘点只纳入有库存行的「图书 + 库位」—— 没有库存行就没有账面数可对）");
        }

        Set<Long> newBookIds = new LinkedHashSet<>();
        for (Stock stock : scope)
        {
            newBookIds.add(stock.getBookId());
        }
        requireNoRunningOverlap(newBookIds);

        StockCheck check = new StockCheck();
        check.setCheckType(request.getCheckType());
        check.setScopeType(request.getScopeType());
        check.setScopeValue(request.getScopeValue());
        check.setShowBookQty(Boolean.TRUE.equals(request.getShowBookQty()) ? 1 : 0);
        check.setRemark(request.getRemark());
        check.setStatus(StockCheck.STATUS_RUNNING);
        check.setStartTime(LocalDateTime.now());
        check.setEndTime(null);
        check.setNeedRecheck(0);

        Long predictedId = baseMapper.selectNextCheckId();
        check.setCheckNo(buildCheckNo(predictedId == null ? 1L : predictedId));
        baseMapper.insert(check);

        String actualNo = buildCheckNo(check.getCheckId());
        if (!actualNo.equals(check.getCheckNo()))
        {
            StockCheck patch = new StockCheck();
            patch.setCheckId(check.getCheckId());
            patch.setCheckNo(actualNo);
            baseMapper.updateById(patch);
            check.setCheckNo(actualNo);
        }

        // 逐行**快照账面数**：盘点的语义就是「与开始那一刻的账面对比」，
        // 之后实时算的话，盘点期间卖出去的书会被算成盘亏
        List<StockCheckItem> items = new ArrayList<>(scope.size());
        for (Stock stock : scope)
        {
            StockCheckItem it = new StockCheckItem();
            it.setCheckId(check.getCheckId());
            it.setBookId(stock.getBookId());
            Book book = bookMapper.selectById(stock.getBookId());
            if (book != null)
            {
                it.setIsbn(book.getIsbn());
                it.setTitle(book.getTitle());
            }
            it.setLocationId(stock.getLocationId());
            it.setBookQty(presentQty(stock));
            it.setActualQty(0);
            it.setDiffQty(0);
            it.setDiffAmount(BigDecimal.ZERO);
            it.setDiffType(StockCheckItem.DIFF_NONE);
            it.setIsPeriodDiff(0);
            items.add(it);
        }
        persistItems(check.getCheckId(), items);
        refreshStats(check.getCheckId());
        return selectStockCheckById(check.getCheckId());
    }

    /**
     * 同一批图书不允许同时存在两张进行中的盘点单。
     *
     * <p><b>为什么按「图书」而不是按「范围」查重</b>：范围是三种不同的表达
     * （分类 / 库位 / 图书列表），「按分类盘教材」与「按库位盘 A 区」完全可能覆盖同一本书。
     * 两张单同时对同一本书算差异，后结束的那张会覆盖前一张的结果。
     * 故按**解析出来的图书集合**取交集判断，而不是比对范围字符串。
     */
    private void requireNoRunningOverlap(Set<Long> bookIds)
    {
        List<StockCheck> running = baseMapper.selectList(
                Wrappers.<StockCheck>lambdaQuery().eq(StockCheck::getStatus, StockCheck.STATUS_RUNNING));
        if (running.isEmpty())
        {
            return;
        }
        Set<Long> runningIds = new LinkedHashSet<>();
        for (StockCheck rc : running)
        {
            runningIds.add(rc.getCheckId());
        }
        Long overlap = itemMapper.selectCount(Wrappers.<StockCheckItem>lambdaQuery()
                .in(StockCheckItem::getCheckId, runningIds)
                .in(StockCheckItem::getBookId, bookIds));
        if (overlap != null && overlap > 0)
        {
            throw BizErrors.conflict("该范围内已有 " + overlap
                    + " 行图书正处于另一张进行中的盘点单里，请先结束或作废那张单再开新单");
        }
    }

    /**
     * 把范围解析成「图书 + 库位」的库存行集合。
     *
     * <p>三种范围：① 按分类（**含子分类**）② 按库位 ③ 指定图书。
     * 只返回**有库存行**的组合 —— 没有库存行就没有账面数，纳入盘点没有意义。
     */
    private List<Stock> resolveScope(Integer scopeType, String scopeValue)
    {
        if (Objects.equals(scopeType, StockCheck.SCOPE_LOCATION))
        {
            Long locationId = parseSingleId(scopeValue, "库位");
            referenceGuard.requireExists(locationMapper, locationId, "库位");
            return stockMapper.selectList(
                    Wrappers.<Stock>lambdaQuery().eq(Stock::getLocationId, locationId));
        }

        List<Long> bookIds;
        if (Objects.equals(scopeType, StockCheck.SCOPE_CATEGORY))
        {
            Long categoryId = parseSingleId(scopeValue, "分类");
            referenceGuard.requireExists(categoryMapper, categoryId, "分类");
            bookIds = bookIdsInCategorySubtree(categoryId);
        }
        else
        {
            bookIds = parseIds(scopeValue);
        }
        if (bookIds.isEmpty())
        {
            return Collections.emptyList();
        }
        return stockMapper.selectList(Wrappers.<Stock>lambdaQuery().in(Stock::getBookId, bookIds));
    }

    /**
     * 取某分类**及其所有子孙分类**下的图书。
     *
     * <p>「按分类盘教材」应当把教材下的所有二级分类都盘到 —— 只按 `category_id`
     * 精确匹配会漏掉全部子分类，那盘点结果就是错的。
     * 分类是小表（种子里一二十行），故在内存里自上而下展开，不写递归 SQL。
     */
    private List<Long> bookIdsInCategorySubtree(Long rootCategoryId)
    {
        Set<Long> categoryIds = new LinkedHashSet<>();
        categoryIds.add(rootCategoryId);
        List<Category> all = categoryMapper.selectList(Wrappers.<Category>lambdaQuery());
        boolean grew = true;
        while (grew)
        {
            grew = false;
            for (Category c : all)
            {
                if (c.getParentId() != null && categoryIds.contains(c.getParentId())
                        && categoryIds.add(c.getCategoryId()))
                {
                    grew = true;
                }
            }
        }
        List<Book> books = bookMapper.selectList(
                Wrappers.<Book>lambdaQuery().in(Book::getCategoryId, categoryIds));
        List<Long> ids = new ArrayList<>(books.size());
        for (Book b : books)
        {
            ids.add(b.getBookId());
        }
        return ids;
    }

    // ==================================================================
    // 录入实盘数（增量提交）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCheck entryItems(Long checkId, StockCheckItemsRequest request)
    {
        StockCheck check = referenceGuard.requireExists(baseMapper, checkId, "盘点单");
        referenceGuard.requireState(check.getStatus(), StockCheck.STATUS_RUNNING, "盘点单", "进行中");

        String operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();

        for (StockCheckItemEntry entry : request.getItems())
        {
            StockCheckItem row = referenceGuard.requireExists(itemMapper, entry.getItemId(), "盘点明细");
            // 防串单：提交的明细必须属于本单，否则等于改了别的盘点单的数
            if (!Objects.equals(row.getCheckId(), checkId))
            {
                throw BizErrors.ruleViolated("提交了非本盘点单的明细行（明细 " + entry.getItemId()
                        + " 属于盘点单 " + row.getCheckId() + "）");
            }

            int actual = nzi(entry.getActualQty());
            int bookQty = nzi(row.getBookQty());
            int diff = actual - bookQty;

            StockCheckItem patch = new StockCheckItem();
            patch.setItemId(row.getItemId());
            patch.setActualQty(actual);
            patch.setDiffQty(diff);
            patch.setDiffType(diffTypeOf(diff));
            patch.setDiffAmount(diffAmountOf(row.getBookId(), diff));
            patch.setReason(entry.getReason());
            patch.setSuggestion(entry.getSuggestion());
            // ⚠️ checkTime 是「已录入」的标记。不写它的话，actualQty=0 分不清
            //    「数了确实是 0」与「还没数」，结束盘点时的必查项就失效了
            patch.setCheckedBy(operator);
            patch.setCheckTime(now);
            referenceGuard.requireAffected(itemMapper.updateById(patch), "盘点明细");
        }

        refreshStats(checkId);
        return selectStockCheckById(checkId);
    }

    // ==================================================================
    // 结束盘点：应用差异
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCheck finish(Long checkId, Boolean forceFinish)
    {
        StockCheck check = referenceGuard.requireExists(baseMapper, checkId, "盘点单");
        referenceGuard.requireState(check.getStatus(), StockCheck.STATUS_RUNNING, "盘点单", "进行中");

        List<StockCheckItem> items = selectItems(checkId);
        requireItems(items);

        int unentered = itemMapper.countUnentered(checkId);
        if (unentered > 0)
        {
            throw BizErrors.ruleViolated("还有 " + unentered
                    + " 行账面数不为 0 的图书没有录入实盘数，请数完再结束盘点"
                    + "（没数完就结束，差异报告是假的）");
        }
        for (StockCheckItem it : items)
        {
            // 差异行必须填原因 —— 否则差异报告里全是「为什么会差？」无从回答
            if (nzi(it.getDiffQty()) != 0 && (it.getReason() == null || it.getReason().isBlank()))
            {
                throw BizErrors.ruleViolated("「" + it.getTitle() + "」账面 " + nzi(it.getBookQty())
                        + " 册、实盘 " + nzi(it.getActualQty()) + " 册，有差异必须填写差异原因");
            }
        }

        refreshStats(checkId);
        StockCheck fresh = referenceGuard.requireExists(baseMapper, checkId, "盘点单");
        BigDecimal threshold = diffRateThreshold();
        boolean over = fresh.getDiffRate() != null && fresh.getDiffRate().compareTo(threshold) > 0;
        boolean forced = Boolean.TRUE.equals(forceFinish);

        if (over && !forced)
        {
            // 差异率超阈值且未强制 → 待复核，**不应用差异**（§3.2 库存规则 4：
            // 需二次盘点。此时数可能本身就是错的，先改了库存反而要再改回来）
            StockCheck patch = new StockCheck();
            patch.setCheckId(checkId);
            patch.setStatus(StockCheck.STATUS_PENDING_REVIEW);
            patch.setNeedRecheck(1);
            referenceGuard.requireAffected(baseMapper.updateById(patch), "盘点单");
            return selectStockCheckById(checkId);
        }

        applyDiffs(check, items);

        StockCheck patch = new StockCheck();
        patch.setCheckId(checkId);
        patch.setStatus(StockCheck.STATUS_FINISHED);
        patch.setEndTime(LocalDateTime.now());
        patch.setNeedRecheck(over ? 1 : 0);
        referenceGuard.requireAffected(baseMapper.updateById(patch), "盘点单");
        return selectStockCheckById(checkId);
    }

    /**
     * 把差异应用到库存，并逐行判定**盘点期差异**。
     *
     * <p><b>为什么应用的是「实盘 − 当前在场量」，而不是「实盘 − 快照账面数」</b>：
     * 快照是开始盘点那一刻的账面，而盘点期间店还在营业，书可能被卖掉 / 调拨走。
     * 那笔变动**已经**反映在当前库存里了。若按 {@code 实盘 − 快照} 去调，
     * 等于把盘点期间那笔变动再计一次：
     * <pre>
     *   快照 10 → 盘点期间卖掉 2（当前 8）→ 盘点人数到 8（实盘 8）
     *   按「实盘 − 快照」= 8 − 10 = -2 调整 → 8 − 2 = 6   ✗ 错（该是 8）
     *   按「实盘 − 当前」= 8 −  8 =  0 调整 → 8            ✓
     * </pre>
     * 这也是盘点要达成的不变量：结束后「在场实物量 == 实盘数」。
     * {@code isPeriodDiff} 标出这类行，让差异报告把它与盘点人的责任差异分开（§4.2 规则 5）。
     */
    private void applyDiffs(StockCheck check, List<StockCheckItem> items)
    {
        for (StockCheckItem it : items)
        {
            Stock stock = findStock(it.getBookId(), nzl(it.getLocationId()));
            if (stock == null)
            {
                // 走到这里说明库存行在盘点期间被删了（删除保护本应拦住），跳过而不是 NPE
                continue;
            }

            int current = presentQty(stock);
            int snapshot = nzi(it.getBookQty());
            int actual = nzi(it.getActualQty());
            int adjust = actual - current;
            boolean periodDiff = (current != snapshot);

            int available = nzi(stock.getQtyAvailable());
            int newAvailable = available + adjust;
            if (newAvailable < 0)
            {
                throw BizErrors.ruleViolated("「" + it.getTitle() + "」按实盘数调整后可售会变成负数"
                        + "（当前可售 " + available + "，需调整 " + adjust + " 册），请核对实盘数");
            }
            if (adjust != 0)
            {
                Stock sp = new Stock();
                sp.setStockId(stock.getStockId());
                sp.setVersion(stock.getVersion());
                sp.setQtyAvailable(newAvailable);
                referenceGuard.requireAffected(stockMapper.updateById(sp), "库存");
                writeFlow(it, adjust, available, newAvailable, check.getCheckNo());
            }

            StockCheckItem patch = new StockCheckItem();
            patch.setItemId(it.getItemId());
            patch.setIsPeriodDiff(periodDiff ? 1 : 0);
            // 差异类型：有盘点期变动就是 3（该值优先级高于单纯盈亏 ——
            // 这类行的差异不能全算在盘点人头上，报告里也是单独一组）
            patch.setDiffType(periodDiff ? StockCheckItem.DIFF_PERIOD : diffTypeOf(nzi(it.getDiffQty())));
            referenceGuard.requireAffected(itemMapper.updateById(patch), "盘点明细");
        }
    }

    /**
     * 写一条盘点引起的库存流水。
     *
     * <p>只调 {@code qty_available}，故 {@code stock_type} 恒为 1 可售；
     * {@code biz_type} 按盈亏正负取 7 盘盈 / 8 盘亏。
     */
    private void writeFlow(StockCheckItem it, int change, int before, int after, String bizNo)
    {
        StockFlow flow = new StockFlow();
        flow.setBookId(it.getBookId());
        flow.setLocationId(nzl(it.getLocationId()));
        flow.setStockType(STOCK_TYPE_AVAILABLE);
        flow.setBizType(change > 0 ? BIZ_TYPE_CHECK_GAIN : BIZ_TYPE_CHECK_LOSS);
        flow.setBizNo(bizNo);
        flow.setQtyChange(change);
        flow.setQtyBefore(before);
        flow.setQtyAfter(after);
        flow.setRemark("盘点差异调整");
        stockFlowMapper.insert(flow);
    }

    /**
     * 重算单据头的统计列（全部由明细聚合，客户端传不了）。
     *
     * <p><b>品种数按「去重的图书数」算，不是明细行数</b>：同一本书在 A、B 两个库位
     * 有库存行时会占两行明细，但它只是**一个品种**。差异率的分母是品种数，
     * 用行数当分母会让差异率被摊薄一半。
     */
    @Override
    public void refreshStats(Long checkId)
    {
        List<StockCheckItem> items = selectItems(checkId);
        Set<Long> allBooks = new LinkedHashSet<>();
        Set<Long> checkedBooks = new LinkedHashSet<>();
        Set<Long> diffBooks = new LinkedHashSet<>();
        int qtyBook = 0;
        int qtyActual = 0;
        int gain = 0;
        int loss = 0;
        for (StockCheckItem it : items)
        {
            allBooks.add(it.getBookId());
            qtyBook += nzi(it.getBookQty());
            if (it.getCheckTime() != null)
            {
                checkedBooks.add(it.getBookId());
                qtyActual += nzi(it.getActualQty());
            }
            int d = nzi(it.getDiffQty());
            if (d > 0)
            {
                gain += d;
                diffBooks.add(it.getBookId());
            }
            else if (d < 0)
            {
                loss += -d;
                diffBooks.add(it.getBookId());
            }
        }
        int kindTotal = allBooks.size();
        int diffKind = diffBooks.size();
        BigDecimal rate = (kindTotal == 0) ? BigDecimal.ZERO
                : BigDecimal.valueOf(diffKind * 100.0 / kindTotal).setScale(RATE_SCALE, RoundingMode.HALF_UP);

        StockCheck patch = new StockCheck();
        patch.setCheckId(checkId);
        patch.setBookKindTotal(kindTotal);
        patch.setBookKindChecked(checkedBooks.size());
        patch.setQtyBookTotal(qtyBook);
        patch.setQtyActualTotal(qtyActual);
        patch.setQtyGain(gain);
        patch.setQtyLoss(loss);
        patch.setDiffKindCount(diffKind);
        patch.setDiffRate(rate);
        baseMapper.updateById(patch);
    }

    // ==================================================================
    // 差异报告与二次盘点
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    public StockCheckDiffReport diffReport(Long checkId)
    {
        StockCheck check = referenceGuard.requireExists(baseMapper, checkId, "盘点单");
        List<StockCheckItem> all = selectItems(checkId);

        List<StockCheckItem> diffs = new ArrayList<>();
        List<StockCheckItem> periods = new ArrayList<>();
        for (StockCheckItem it : all)
        {
            if (Objects.equals(it.getIsPeriodDiff(), 1))
            {
                // 盘点期差异**单独一组**（§4.2 规则 5）—— 盘点期间卖掉的/调走的
                // 不该记在盘点人头上，混在一起盘点人得逐条解释不属于自己的差异
                periods.add(it);
            }
            else if (nzi(it.getDiffQty()) != 0)
            {
                diffs.add(it);
            }
        }

        BigDecimal threshold = diffRateThreshold();
        StockCheckDiffReport report = new StockCheckDiffReport();
        report.setCheckId(checkId);
        report.setCheckNo(check.getCheckNo());
        report.setStatus(check.getStatus());
        report.setNeedRecheck(nzi(check.getNeedRecheck()));
        report.setThreshold(threshold);

        StockCheckDiffReport.Summary summary = new StockCheckDiffReport.Summary();
        summary.setBookKindTotal(check.getBookKindTotal());
        summary.setBookKindChecked(check.getBookKindChecked());
        summary.setQtyBookTotal(check.getQtyBookTotal());
        summary.setQtyActualTotal(check.getQtyActualTotal());
        summary.setQtyGain(check.getQtyGain());
        summary.setQtyLoss(check.getQtyLoss());
        summary.setDiffKindCount(check.getDiffKindCount());
        summary.setDiffRate(check.getDiffRate());
        report.setSummary(summary);

        // ⚠️ 这里**不遮蔽**账面数：报告是 A-22 给复核人看的差异凭证，
        //    把差异遮掉等于交出一份没有内容的报告（实测踩到 —— 初版照
        //    selectStockCheckById 的做法遮了，报告里的 diffType 直接变 null）。
        //    盲盘要防的是**盘点人**在录入时看到账面数，而复核本来就该看到全部数字；
        //    录入接口（/{checkId}/items）与单据详情（selectStockCheckById）仍然遮蔽。
        report.setDiffItems(diffs);
        report.setPeriodDiffItems(periods);
        report.setSuggestion(buildSuggestion(check, diffs.size(), periods.size(), threshold));
        return report;
    }

    /** 按当前盘点情况给一句可执行的处理建议（比让复核人自己推断下一步要好） */
    private String buildSuggestion(StockCheck check, int diffRows, int periodRows, BigDecimal threshold)
    {
        StringBuilder sb = new StringBuilder();
        if (Objects.equals(check.getStatus(), StockCheck.STATUS_PENDING_REVIEW))
        {
            sb.append("差异率 ").append(check.getDiffRate()).append("% 已超过阈值 ").append(threshold)
              .append("%，差异尚未应用到库存。请复核后发起二次盘点（只盘本次的差异行），")
              .append("或确认数据无误后带 forceFinish 强制结束。");
        }
        else if (diffRows == 0 && periodRows == 0)
        {
            sb.append("账实相符，无差异。");
        }
        else
        {
            if (diffRows > 0)
            {
                sb.append("有 ").append(diffRows).append(" 行盘点差异，请逐条核对差异原因与处理建议后再结束盘点；");
            }
            if (periodRows > 0)
            {
                sb.append("另有 ").append(periodRows)
                  .append(" 行属盘点期差异（盘点期间发生过出入库），不计入盘点人责任，")
                  .append("结束盘点时按「实盘 − 当前库存」调整，不会重复计入。");
            }
        }
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCheckRecheckResult recheck(Long checkId, Integer checkType)
    {
        StockCheck origin = referenceGuard.requireExists(baseMapper, checkId, "盘点单");
        if (!Objects.equals(origin.getStatus(), StockCheck.STATUS_PENDING_REVIEW))
        {
            throw BizErrors.ruleViolated("盘点单「" + origin.getCheckNo() + "」当前不是「待复核」状态，"
                    + "不能发起二次盘点（只有差异率超阈值的单子才需要复核）");
        }

        List<StockCheckItem> all = selectItems(checkId);
        List<StockCheckItem> diffRows = new ArrayList<>();
        Set<Long> diffBookIds = new LinkedHashSet<>();
        for (StockCheckItem it : all)
        {
            if (nzi(it.getDiffQty()) != 0 || Objects.equals(it.getIsPeriodDiff(), 1))
            {
                diffRows.add(it);
                diffBookIds.add(it.getBookId());
            }
        }
        if (diffRows.isEmpty())
        {
            throw BizErrors.ruleViolated("该盘点单没有差异行，不需要二次盘点");
        }

        StringBuilder scopeValue = new StringBuilder();
        for (Long id : diffBookIds)
        {
            if (scopeValue.length() > 0)
            {
                scopeValue.append(',');
            }
            scopeValue.append(id);
        }
        if (scopeValue.length() > 500)
        {
            // scope_value 是 varchar(500)，超了会在插入时被截断或报错 —— 提前给可执行的提示
            throw BizErrors.ruleViolated("差异品种过多（" + diffBookIds.size()
                    + " 种），超出单张盘点单的范围上限，请按库位或分类分开二次盘点");
        }

        StockCheck fresh = new StockCheck();
        fresh.setCheckType(checkType == null ? StockCheck.TYPE_SAMPLE : checkType);
        // 二次盘点只含差异行 —— 范围如实写成「指定图书」，而不是照抄原单的范围
        // （原单是「按分类盘教材」，新单实际只盘其中差异的那几种，写原范围会误导）
        fresh.setScopeType(StockCheck.SCOPE_BOOKS);
        fresh.setScopeValue(scopeValue.toString());
        fresh.setShowBookQty(origin.getShowBookQty());
        fresh.setStatus(StockCheck.STATUS_RUNNING);
        fresh.setStartTime(LocalDateTime.now());
        fresh.setNeedRecheck(0);
        // ⚠️ 本表没有 origin_check_id 列，只能把出身写进备注（与作废入库单记原因同一处理）
        fresh.setRemark(RECHECK_MARK + origin.getCheckNo());

        Long predictedId = baseMapper.selectNextCheckId();
        fresh.setCheckNo(buildCheckNo(predictedId == null ? 1L : predictedId));
        baseMapper.insert(fresh);
        String actualNo = buildCheckNo(fresh.getCheckId());
        if (!actualNo.equals(fresh.getCheckNo()))
        {
            StockCheck patch = new StockCheck();
            patch.setCheckId(fresh.getCheckId());
            patch.setCheckNo(actualNo);
            baseMapper.updateById(patch);
            fresh.setCheckNo(actualNo);
        }

        List<StockCheckItem> items = new ArrayList<>(diffRows.size());
        for (StockCheckItem src : diffRows)
        {
            StockCheckItem it = new StockCheckItem();
            it.setCheckId(fresh.getCheckId());
            it.setBookId(src.getBookId());
            it.setIsbn(src.getIsbn());
            it.setTitle(src.getTitle());
            it.setLocationId(src.getLocationId());
            // **重新快照**账面数：二次盘点是「与此刻对比」，照抄原单的快照没有意义
            Stock stock = findStock(src.getBookId(), nzl(src.getLocationId()));
            it.setBookQty(stock == null ? 0 : presentQty(stock));
            it.setActualQty(0);
            it.setDiffQty(0);
            it.setDiffAmount(BigDecimal.ZERO);
            it.setDiffType(StockCheckItem.DIFF_NONE);
            it.setIsPeriodDiff(0);
            items.add(it);
        }
        persistItems(fresh.getCheckId(), items);
        refreshStats(fresh.getCheckId());

        StockCheckRecheckResult result = new StockCheckRecheckResult();
        result.setCheckId(fresh.getCheckId());
        result.setCheckNo(fresh.getCheckNo());
        result.setOriginCheckNo(origin.getCheckNo());
        result.setItemCount(items.size());
        return result;
    }

    // ==================================================================
    // 内部：明细的准备与读取
    // ==================================================================

    private List<StockCheckItem> selectItems(Long checkId)
    {
        StockCheckItem query = new StockCheckItem();
        query.setCheckId(checkId);
        return itemMapper.selectStockCheckItemList(query);
    }

    private void requireItems(List<StockCheckItem> items)
    {
        if (items == null || items.isEmpty())
        {
            throw BizErrors.ruleViolated("盘点单至少要有一行明细");
        }
    }

    /**
     * 校验引用、回填冗余列、快照账面数、算派生列（标准 CRUD 走这里）。
     *
     * <p>{@code generate} 那条路径不走本方法（它自己从库存行构造明细），
     * 但两条路径的**派生列算法必须一致**，否则手工开的单与范围生成的单口径不同。
     *
     * <p>⚠️ {@code bookQty} **由服务端按当前库存快照，不接受客户端传入** ——
     * 让客户端填账面数等于让被盘点的人自己写差异基准。
     */
    private void prepareItems(List<StockCheckItem> items)
    {
        for (StockCheckItem it : items)
        {
            Book book = referenceGuard.requireExists(bookMapper, it.getBookId(), "图书");
            it.setIsbn(book.getIsbn());
            it.setTitle(book.getTitle());

            if (it.getLocationId() != null && it.getLocationId() > 0)
            {
                referenceGuard.requireExists(locationMapper, it.getLocationId(), "库位");
            }
            else
            {
                it.setLocationId(0L);
            }

            Stock stock = findStock(it.getBookId(), it.getLocationId());
            it.setBookQty(stock == null ? 0 : presentQty(stock));

            // ⚠️ **本路径只定义「盘哪些行」，不录入实盘数** —— 实盘数一律走
            //    `POST /inventory/stockcheck/{checkId}/items`（契约里那个专门的录入端点）。
            //
            //    为什么不在 CRUD 里接受 actualQty（实测踩到）：`actualQty` 默认 0，
            //    一旦「传了就算已录入」，前端一张把所有行默认成 0 的表单提交上来，
            //    每一行都会被算成「盘亏 bookQty」—— 差异率直接 100%，一个人都还没数
            //    就被判要二次盘点。与其猜「0 是数了还是没数」，不如不接受。
            it.setActualQty(0);
            it.setDiffQty(0);
            it.setDiffType(StockCheckItem.DIFF_NONE);
            it.setDiffAmount(BigDecimal.ZERO);
            it.setCheckTime(null);
            it.setCheckedBy(null);
            if (it.getIsPeriodDiff() == null)
            {
                it.setIsPeriodDiff(0);
            }
        }
    }

    private void persistItems(Long checkId, List<StockCheckItem> items)
    {
        for (StockCheckItem it : items)
        {
            it.setCheckId(checkId);
            it.setItemId(null);
            itemMapper.insert(it);
        }
    }

    /**
     * 盲盘遮蔽：{@code showBookQty != 1} 时把**所有能反推出账面数的字段**置 null。
     *
     * <p>⚠️ 只遮 {@code bookQty} 是不够的 —— {@code diffQty = actualQty − bookQty}，
     * 盘点人拿 {@code diffQty} 就能算出 {@code bookQty = actualQty − diffQty}，
     * 盲盘等于没做。故 {@code diffQty} / {@code diffAmount} / {@code diffType} 一并遮蔽。
     */
    private List<StockCheckItem> maskBookQty(StockCheck check, List<StockCheckItem> items)
    {
        if (items == null || Objects.equals(check.getShowBookQty(), 1))
        {
            return items;
        }
        for (StockCheckItem it : items)
        {
            it.setBookQty(null);
            it.setDiffQty(null);
            it.setDiffAmount(null);
            it.setDiffType(null);
        }
        return items;
    }

    // ==================================================================
    // 内部：小工具
    // ==================================================================

    private Stock findStock(Long bookId, Long locationId)
    {
        return stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .eq(Stock::getLocationId, locationId));
    }

    /**
     * **在场实物量** = 可售 + 锁定 + 残损（**不含借出**）。
     *
     * <p>这是盘点「账面数」的口径，理由见 {@link StockCheckItem} 的类注释：
     * 借出的书在读者手上、架上数不到，计入账面会把每本借出的书变成一条假盘亏。
     */
    private int presentQty(Stock stock)
    {
        return nzi(stock.getQtyAvailable()) + nzi(stock.getQtyLocked()) + nzi(stock.getQtyDamaged());
    }

    private int diffTypeOf(int diff)
    {
        if (diff > 0)
        {
            return StockCheckItem.DIFF_GAIN;
        }
        return (diff < 0) ? StockCheckItem.DIFF_LOSS : StockCheckItem.DIFF_NONE;
    }

    /** 差异金额按**成本价**计（盘点损溢要走财务口径，不能用定价） */
    private BigDecimal diffAmountOf(Long bookId, int diff)
    {
        if (diff == 0)
        {
            return BigDecimal.ZERO;
        }
        Book book = bookMapper.selectById(bookId);
        BigDecimal cost = (book == null || book.getCostPrice() == null) ? BigDecimal.ZERO : book.getCostPrice();
        return cost.multiply(BigDecimal.valueOf(diff)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 范围值里只应有一个 ID（按分类 / 按库位） */
    private Long parseSingleId(String scopeValue, String label)
    {
        List<Long> ids = parseIds(scopeValue);
        if (ids.size() != 1)
        {
            throw BizErrors.badRequest("按" + label + "盘点时，范围值应是**单个**" + label
                    + "ID，收到「" + scopeValue + "」");
        }
        return ids.get(0);
    }

    /** 解析逗号分隔的 ID 串（去重保序；非法片段抛 400 而不是静默跳过） */
    private List<Long> parseIds(String raw)
    {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank())
        {
            return ids;
        }
        for (String part : raw.split(","))
        {
            String token = part.trim();
            if (token.isEmpty())
            {
                continue;
            }
            try
            {
                ids.add(Long.parseLong(token));
            }
            catch (NumberFormatException e)
            {
                throw BizErrors.badRequest("范围值格式非法：「" + token + "」不是有效的ID");
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /** 取当前操作人；无登录上下文（定时任务等）时返回空串而不是抛异常 */
    private String currentOperator()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }

    private Long nzl(Long v)
    {
        return (v == null) ? 0L : v;
    }
}
