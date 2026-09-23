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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.tbgbook.bookstore.stock.domain.CostChangeLog;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockFlow;
import com.tbgbook.bookstore.stock.domain.StockIn;
import com.tbgbook.bookstore.stock.domain.StockInConfirmResult;
import com.tbgbook.bookstore.stock.domain.StockInItem;
import com.tbgbook.bookstore.stock.mapper.CostChangeLogMapper;
import com.tbgbook.bookstore.stock.mapper.LocationMapper;
import com.tbgbook.bookstore.stock.mapper.StockFlowMapper;
import com.tbgbook.bookstore.stock.mapper.StockInItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockInMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.service.IStockInService;

import lombok.RequiredArgsConstructor;

/**
 * 采购入库单 Service 实现（阶段三步骤②）。
 *
 * <p><b>本类是全项目「单据改库存」的样板</b>，后续的盘点单（步骤③）、调拨与报损（步骤④）
 * 会照这里的骨架写。它集中演示了四件容易写错的事：
 * <ol>
 *   <li><b>库存只能在单据动作里改</b>（FR-4.2 规则 1）—— 草稿不动库存，
 *       {@link #confirm} 才累加；{@link #voidStockIn} 反向冲减</li>
 *   <li><b>改库存必留痕</b> —— 每一行都写 {@link StockFlow}，含**变动前后结存**
 *       （只写变动量的话，事后无法校验账面连续性）</li>
 *   <li><b>成本要在改库存之前算</b> —— 移动加权平均的分母是「本次入库前的实物总量」，
 *       顺序颠倒会算出偏低的成本。见 {@link #confirm} 的三段式</li>
 *   <li><b>乐观锁冲突要显式检查</b> —— {@code Stock} 带 {@code @Version}，
 *       MP 在影响 0 行时**不抛异常**，必须 {@code requireAffected}</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockInServiceImpl extends ServiceImpl<StockInMapper, StockIn> implements IStockInService
{
    /** 入库单号前缀（需求 §3.2 编码规则：RK + yyyyMMdd + 流水） */
    private static final String IN_NO_PREFIX = "RK";

    /** 金额列一律两位小数（`decimal(12,2)`） */
    private static final int AMOUNT_SCALE = 2;

    /** `biz_stock_flow.stock_type`：可售 */
    private static final int STOCK_TYPE_AVAILABLE = 1;

    /** `biz_stock_flow.biz_type`：采购入库 */
    private static final int BIZ_TYPE_PURCHASE_IN = 1;

    /** `biz_cost_change_log.change_type`：采购入库 */
    private static final int COST_CHANGE_TYPE_PURCHASE_IN = 1;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final LocationMapper locationMapper;

    private final StockMapper stockMapper;

    private final StockFlowMapper stockFlowMapper;

    private final CostChangeLogMapper costChangeLogMapper;

    private final StockInItemMapper itemMapper;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<StockIn> selectStockInList(StockIn stockIn)
    {
        return baseMapper.selectStockInList(stockIn);
    }

    @Override
    public StockIn selectStockInById(Long inId)
    {
        StockIn stockIn = referenceGuard.requireExists(baseMapper, inId, "入库单");
        stockIn.setItems(selectItems(inId));
        return stockIn;
    }

    // ==================================================================
    // 写：草稿的开单 / 改单 / 删单（**都不动库存**）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStockIn(StockIn stockIn)
    {
        requireItems(stockIn.getItems());

        // 服务端接管字段：客户端传了也不认
        stockIn.setInId(null);
        stockIn.setInNo(null);
        stockIn.setStatus(StockIn.STATUS_DRAFT);
        stockIn.setInTime(null);
        if (stockIn.getInType() == null)
        {
            stockIn.setInType(StockIn.TYPE_PURCHASE);
        }

        prepareItems(stockIn.getItems());
        summarize(stockIn);

        // in_no 是 NOT NULL 且带唯一索引 —— 不能「先插入再回填」，插入时就得给出唯一值。
        // 与 BookServiceImpl#addBook 同一手法：预取 AUTO_INCREMENT 生成，插入后按真实主键校正。
        Long predictedId = baseMapper.selectNextInId();
        stockIn.setInNo(buildInNo(predictedId == null ? 1L : predictedId));

        int rows = baseMapper.insert(stockIn);
        persistItems(stockIn.getInId(), stockIn.getItems());

        String actualNo = buildInNo(stockIn.getInId());
        if (!actualNo.equals(stockIn.getInNo()))
        {
            StockIn patch = new StockIn();
            patch.setInId(stockIn.getInId());
            patch.setInNo(actualNo);
            baseMapper.updateById(patch);
            stockIn.setInNo(actualNo);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStockIn(StockIn stockIn)
    {
        if (stockIn.getInId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的入库单");
        }
        StockIn current = referenceGuard.requireExists(baseMapper, stockIn.getInId(), "入库单");
        // 已入库的单据一旦改动明细，库存与单据就对不上了 —— 只能作废重开
        referenceGuard.requireState(current.getStatus(), StockIn.STATUS_DRAFT, "入库单", "草稿");

        stockIn.setInNo(null);
        stockIn.setStatus(null);
        stockIn.setInTime(null);

        if (stockIn.getItems() != null)
        {
            requireItems(stockIn.getItems());
            prepareItems(stockIn.getItems());
            summarize(stockIn);
            // 明细整体替换：删旧插新。明细有服务端算的派生列，逐行 diff 换不来收益，
            // 而「头与明细同事务」的语义反而更清晰。
            itemMapper.deleteByInIds(new Long[] { stockIn.getInId() });
            persistItems(stockIn.getInId(), stockIn.getItems());
        }
        else
        {
            // 未传 items = 只改单据头，统计列保持原值（置 null 让 MP 跳过它们）
            stockIn.setBookKind(null);
            stockIn.setTotalQty(null);
            stockIn.setTotalAmount(null);
        }

        int rows = baseMapper.updateById(stockIn);
        referenceGuard.requireAffected(rows, "入库单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStockIns(Long[] inIds)
    {
        List<Long> idList = (inIds == null) ? Collections.emptyList() : Arrays.asList(inIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "入库单");

        for (Serializable id : ids)
        {
            StockIn one = referenceGuard.requireExists(baseMapper, id, "入库单");
            if (!Objects.equals(one.getStatus(), StockIn.STATUS_DRAFT))
            {
                throw BizErrors.ruleViolated("入库单「" + one.getInNo()
                        + "」不是草稿状态，不能删除；已入库的单据请改用作废（作废会反向冲减库存）");
            }
        }

        // 主从级联：**先删明细再删头**，顺序不可颠倒（docs/db/README.md §6.1 第 3 类）
        itemMapper.deleteByInIds(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }


    // ==================================================================
    // 写：确认入库（**库存唯一真正变动的动作**）
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p><b>为什么分三段而不是一段</b>：移动加权平均的分母是「本次入库前的实物总量」。
     * 如果边加库存边算成本，后算的那本会把自己刚加的数量算进分母 → 成本被摊薄。
     * 所以先把所有书的成本算完（此时库存还没动），再改库存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockInConfirmResult confirm(Long inId, LocalDateTime inTime)
    {
        StockIn stockIn = referenceGuard.requireExists(baseMapper, inId, "入库单");

        // 防重复提交：已入库再点一次要给明确的 409，而不是静默再加一遍库存
        if (Objects.equals(stockIn.getStatus(), StockIn.STATUS_CONFIRMED))
        {
            throw BizErrors.conflict("入库单「" + stockIn.getInNo() + "」已是已入库状态，不能重复入库");
        }
        if (Objects.equals(stockIn.getStatus(), StockIn.STATUS_VOIDED))
        {
            throw BizErrors.ruleViolated("入库单「" + stockIn.getInNo() + "」已作废，不能再入库");
        }

        List<StockInItem> items = selectItems(inId);
        requireItems(items);
        validateBeforeConfirm(items);

        // ---------- pass 1：逐书算成本（**必须在改库存之前**） ----------
        Map<Long, BookCost> costs = new HashMap<>();
        for (Map.Entry<Long, int[]> entry : aggQtyByBook(items).entrySet())
        {
            Long bookId = entry.getKey();
            costs.put(bookId, computeCost(bookId, entry.getValue()[0], aggAmountByBook(items).get(bookId)));
        }

        // ---------- pass 2：改库存 + 写流水（逐明细行，一行一库位） ----------
        LocalDateTime effectiveTime = (inTime == null) ? LocalDateTime.now() : inTime;
        List<StockInConfirmResult.Row> rows = new ArrayList<>(items.size());
        for (StockInItem it : items)
        {
            Long locationId = nzl(it.getLocationId());
            BookCost cost = costs.get(it.getBookId());
            int[] beforeHolder = new int[1];
            int after = upsertAvailable(it.getBookId(), locationId, nzi(it.getActualQty()), beforeHolder);
            writeFlow(it.getBookId(), locationId, nzi(it.getActualQty()), beforeHolder[0], after,
                    stockIn.getInNo(), "采购入库");

            StockInConfirmResult.Row row = new StockInConfirmResult.Row();
            row.setItemId(it.getItemId());
            row.setBookId(it.getBookId());
            row.setTitle(it.getTitle());
            row.setLocationId(locationId);
            row.setQtyChange(nzi(it.getActualQty()));
            row.setStockBefore(beforeHolder[0]);
            row.setStockAfter(after);
            row.setBeforeCost(cost.beforeCost);
            row.setAfterCost(cost.afterCost);
            rows.add(row);
        }

        // ---------- pass 3：回写成本价 + 写成本变更流水 ----------
        for (Map.Entry<Long, BookCost> entry : costs.entrySet())
        {
            BookCost cost = entry.getValue();
            Book patch = new Book();
            patch.setBookId(entry.getKey());
            patch.setCostPrice(cost.afterCost);
            referenceGuard.requireAffected(bookMapper.updateById(patch), "图书");
            writeCostLog(entry.getKey(), cost, stockIn.getInNo());
        }

        // ---------- 单据置已入库 ----------
        StockIn patch = new StockIn();
        patch.setInId(inId);
        patch.setStatus(StockIn.STATUS_CONFIRMED);
        patch.setInTime(effectiveTime);
        patch.setBookKind(stockIn.getBookKind());
        patch.setTotalQty(stockIn.getTotalQty());
        patch.setTotalAmount(stockIn.getTotalAmount());
        referenceGuard.requireAffected(baseMapper.updateById(patch), "入库单");

        StockInConfirmResult result = new StockInConfirmResult();
        result.setInId(inId);
        result.setInNo(stockIn.getInNo());
        result.setBookKind(stockIn.getBookKind());
        result.setTotalQty(stockIn.getTotalQty());
        result.setTotalAmount(stockIn.getTotalAmount());
        result.setStatus(StockIn.STATUS_CONFIRMED);
        result.setInTime(effectiveTime);
        result.setRows(rows);
        return result;
    }


    // ==================================================================
    // 写：作废（已入库的单据要反向冲减库存）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidStockIn(Long inId, String reason)
    {
        StockIn stockIn = referenceGuard.requireExists(baseMapper, inId, "入库单");
        if (Objects.equals(stockIn.getStatus(), StockIn.STATUS_VOIDED))
        {
            throw BizErrors.conflict("入库单「" + stockIn.getInNo() + "」已是已作废状态");
        }

        if (Objects.equals(stockIn.getStatus(), StockIn.STATUS_CONFIRMED))
        {
            List<StockInItem> items = selectItems(inId);

            // 先整体校验再动手：不要改到一半才发现某一行冲不动（本方法在事务里，
            // 虽然会整体回滚，但先把能给的错误一次性给全，比让用户反复试更好）
            for (StockInItem it : items)
            {
                Stock current = findStock(it.getBookId(), nzl(it.getLocationId()));
                int have = (current == null) ? 0 : nzi(current.getQtyAvailable());
                if (have < nzi(it.getActualQty()))
                {
                    throw BizErrors.ruleViolated("「" + it.getTitle() + "」当前可售 " + have
                            + " 册，不足以冲减本单的 " + nzi(it.getActualQty())
                            + " 册 —— 该单入库后这批书已部分出库（销售 / 调拨 / 报损），"
                            + "不能直接作废；请走退货或报损流程");
                }
            }

            for (StockInItem it : items)
            {
                Long locationId = nzl(it.getLocationId());
                Stock current = findStock(it.getBookId(), locationId);
                int before = nzi(current.getQtyAvailable());
                int change = -nzi(it.getActualQty());

                Stock patch = new Stock();
                patch.setStockId(current.getStockId());
                patch.setVersion(current.getVersion());
                patch.setQtyAvailable(before + change);
                referenceGuard.requireAffected(stockMapper.updateById(patch), "库存");

                // 红字流水：qty_change 为负，qty_before/after 照实记 —— 账面连续性仍可校验
                writeFlow(it.getBookId(), locationId, change, before, before + change,
                        stockIn.getInNo(), "作废冲减");
            }
        }

        StockIn patch = new StockIn();
        patch.setInId(inId);
        patch.setStatus(StockIn.STATUS_VOIDED);
        patch.setRemark(appendReason(stockIn.getRemark(), reason));
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "入库单");
        return rows;
    }

    /**
     * 把作废原因并入备注。
     *
     * <p>⚠️ {@code biz_stock_in} **没有作废原因列**，而契约把 {@code reason} 定为**必填**。
     * 一个必填字段无处安放是不可接受的（与 A-06 状态变更那处同一类取舍），
     * 故并入 {@code remark} 并加可识别前缀。将来若要结构化查询作废原因，需要加列。
     */
    private String appendReason(String remark, String reason)
    {
        String base = (remark == null) ? "" : remark.trim();
        String note = "【作废】" + ((reason == null || reason.isBlank()) ? "未填原因" : reason.trim());
        return base.isEmpty() ? note : base + " | " + note;
    }

    /** 按需求 §3.2 生成入库单号：{@code RK} + yyyyMMdd + 6 位流水 */
    private String buildInNo(Long inId)
    {
        return IN_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", inId);
    }

    // ==================================================================
    // 内部：明细的准备与聚合
    // ==================================================================

    /** 按单据头主键取明细（走 XML 的列表查询，字段显式列出） */
    private List<StockInItem> selectItems(Long inId)
    {
        StockInItem query = new StockInItem();
        query.setInId(inId);
        return itemMapper.selectStockInItemList(query);
    }

    private void requireItems(List<StockInItem> items)
    {
        if (items == null || items.isEmpty())
        {
            throw BizErrors.ruleViolated("入库单至少要有一行明细");
        }
    }

    /**
     * 校验外键引用、回填冗余列、计算派生列。
     *
     * <p>零物理外键（`docs/db/README.md` §6.1），引用完整性靠这一层：
     * {@code bookId} 必须存在（404）；{@code locationId} 非 0 时必须存在（404），
     * 为 0 表示「未分配库位」，是 `biz_stock` 允许的合法值。
     *
     * <p>派生列由服务端算而不是信任客户端：{@code diffQty} 是收货差异核对的依据、
     * {@code amount} 是成本重算的输入，被写歪了两个业务都失准。
     */
    private void prepareItems(List<StockInItem> items)
    {
        for (StockInItem it : items)
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

            if (it.getOrderQty() == null)
            {
                it.setOrderQty(0);
            }
            // 实收留空 = 无差异收货（订多少收多少），这是开单时的常见情形
            if (it.getActualQty() == null)
            {
                it.setActualQty(it.getOrderQty());
            }
            it.setDiffQty(nzi(it.getActualQty()) - nzi(it.getOrderQty()));
            it.setAmount(nz(it.getUnitCost()).multiply(BigDecimal.valueOf(nzi(it.getActualQty()))));
        }
    }

    /** 聚合单据头的统计列（品种数 / 总数量 / 总金额），三个都是服务端维护 */
    private void summarize(StockIn stockIn)
    {
        Set<Long> books = new HashSet<>();
        int qty = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (StockInItem it : stockIn.getItems())
        {
            books.add(it.getBookId());
            qty += nzi(it.getActualQty());
            amount = amount.add(nz(it.getAmount()));
        }
        stockIn.setBookKind(books.size());
        stockIn.setTotalQty(qty);
        stockIn.setTotalAmount(amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
    }

    private void persistItems(Long inId, List<StockInItem> items)
    {
        for (StockInItem it : items)
        {
            it.setInId(inId);
            it.setItemId(null);
            itemMapper.insert(it);
        }
    }

    // ==================================================================
    // 内部：确认入库前的业务校验
    // ==================================================================

    /**
     * 确认入库前的三道校验。
     *
     * <p>⚠️ <b>「有差异必须填原因」按「任何差异」实现</b>，比契约错误码表
     * （只列了「实收**超过**采购数量且未填差异原因」）更严 —— 因为同资源下的业务约束写的是
     * 「**差异行**必须填 {@code diffReason}」（不区分超收 / 短收），而少收同样需要说明
     * （供货不足、破损退回都属要留痕的情形）。两者错误码同为 422，故取更严的一侧。
     *
     * <p>⚠️ <b>同一「图书 + 库位」不允许出现多行</b>：那样一行会写两条库存流水，
     * 且逐行的 {@code stockBefore/After} 只有一个是对的。与其猜，不如要求合并
     * —— 同一个库位分两行收同一本书，业务上也没有意义。
     */
    private void validateBeforeConfirm(List<StockInItem> items)
    {
        Set<String> seen = new HashSet<>();
        for (StockInItem it : items)
        {
            if (nzi(it.getActualQty()) <= 0)
            {
                throw BizErrors.ruleViolated("「" + it.getTitle() + "」实收数量为 0，"
                        + "请删除该行，或改成实际收到的数量");
            }
            if (!Objects.equals(it.getActualQty(), it.getOrderQty())
                    && (it.getDiffReason() == null || it.getDiffReason().isBlank()))
            {
                throw BizErrors.ruleViolated("「" + it.getTitle() + "」采购 " + nzi(it.getOrderQty())
                        + " 册、实收 " + nzi(it.getActualQty()) + " 册，有差异必须填写差异原因");
            }
            String key = it.getBookId() + ":" + nzl(it.getLocationId());
            if (!seen.add(key))
            {
                throw BizErrors.ruleViolated("「" + it.getTitle()
                        + "」在同一库位出现了多行，请合并为一行后再入库");
            }
        }
    }

    // ==================================================================
    // 内部：聚合与成本
    // ==================================================================

    /** 逐书聚合实收数量（成本重算要按**书**算，不是按明细行） */
    private Map<Long, int[]> aggQtyByBook(List<StockInItem> items)
    {
        Map<Long, int[]> map = new LinkedHashMap<>();
        for (StockInItem it : items)
        {
            map.computeIfAbsent(it.getBookId(), k -> new int[1])[0] += nzi(it.getActualQty());
        }
        return map;
    }

    /** 逐书聚合入库金额 */
    private Map<Long, BigDecimal> aggAmountByBook(List<StockInItem> items)
    {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (StockInItem it : items)
        {
            map.merge(it.getBookId(), nz(it.getAmount()), BigDecimal::add);
        }
        return map;
    }

    /**
     * 移动加权平均成本。
     *
     * <pre>
     *   新成本 = (入库前实物总量 × 入库前成本 + 本次入库金额) ÷ (入库前实物总量 + 本次入库数量)
     * </pre>
     *
     * <p>分母用**实物总量**（四类之和）而不是只看可售 —— 借出 / 锁定的书同样占着成本
     * （FR-4.2 规则 2：四类互斥、合计即实物总量）。
     *
     * <p>入库前成本为 0 时（从未核算过）结果自然退化成「本次加权单价」，这正是要的。
     */
    private BookCost computeCost(Long bookId, int inQty, BigDecimal inAmount)
    {
        Book book = bookMapper.selectById(bookId);
        BookCost cost = new BookCost();
        cost.beforeCost = (book == null || book.getCostPrice() == null)
                ? BigDecimal.ZERO : book.getCostPrice();
        cost.inQty = inQty;
        cost.inAmount = nz(inAmount);
        cost.unitCost = (inQty <= 0) ? cost.beforeCost
                : cost.inAmount.divide(BigDecimal.valueOf(inQty), AMOUNT_SCALE, RoundingMode.HALF_UP);

        int beforeQty = physicalQty(bookId);
        int afterQty = beforeQty + inQty;
        if (afterQty <= 0)
        {
            cost.afterCost = cost.beforeCost;
        }
        else
        {
            BigDecimal beforeAmount = cost.beforeCost.multiply(BigDecimal.valueOf(beforeQty));
            cost.afterCost = beforeAmount.add(cost.inAmount)
                    .divide(BigDecimal.valueOf(afterQty), AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return cost;
    }

    /** 某本书的实物总量 = 四类之和（跨全部库位） */
    private int physicalQty(Long bookId)
    {
        List<Stock> rows = stockMapper.selectList(
                Wrappers.<Stock>lambdaQuery().eq(Stock::getBookId, bookId));
        int total = 0;
        for (Stock row : rows)
        {
            total += nzi(row.getQtyAvailable()) + nzi(row.getQtyLocked())
                    + nzi(row.getQtyBorrowed()) + nzi(row.getQtyDamaged());
        }
        return total;
    }

    // ==================================================================
    // 内部：库存与流水的写
    // ==================================================================

    private Stock findStock(Long bookId, Long locationId)
    {
        return stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .eq(Stock::getLocationId, locationId));
    }

    /**
     * 把 {@code qty} 加到该「图书 + 库位」的可售库存上；没有库存行就建一行。
     *
     * <p>{@code outBefore[0]} 回传入库前的可售数（调用方要写进流水与返回结果）。
     *
     * <p><b>并发</b>：已有行时走 {@code @Version} 乐观锁 —— MP 在影响 0 行时**不抛异常**，
     * 故必须 {@code requireAffected} 转成 409。没有行时插入；若并发下别人先插了，
     * 会撞 {@code uk_book_location} → 全局处理器转 409（不会静默覆盖）。
     */
    private int upsertAvailable(Long bookId, Long locationId, int qty, int[] outBefore)
    {
        Stock current = findStock(bookId, locationId);
        if (current == null)
        {
            Stock fresh = new Stock();
            fresh.setBookId(bookId);
            fresh.setLocationId(locationId);
            fresh.setQtyAvailable(qty);
            fresh.setQtyLocked(0);
            fresh.setQtyBorrowed(0);
            fresh.setQtyDamaged(0);
            fresh.setSafetyQty(0);
            fresh.setVersion(0);
            stockMapper.insert(fresh);
            outBefore[0] = 0;
            return qty;
        }

        int before = nzi(current.getQtyAvailable());
        Stock patch = new Stock();
        patch.setStockId(current.getStockId());
        patch.setVersion(current.getVersion());
        patch.setQtyAvailable(before + qty);
        referenceGuard.requireAffected(stockMapper.updateById(patch), "库存");
        outBefore[0] = before;
        return before + qty;
    }

    /**
     * 写一条库存流水。
     *
     * <p>{@code qtyBefore} / {@code qtyAfter} 一定要记 —— 只记 {@code qtyChange} 的话，
     * 事后无法校验账面连续性（「流水加起来等于结存」这条对账规则就失效了）。
     */
    private void writeFlow(Long bookId, Long locationId, int change, int before, int after,
            String bizNo, String remark)
    {
        StockFlow flow = new StockFlow();
        flow.setBookId(bookId);
        flow.setLocationId(locationId);
        flow.setStockType(STOCK_TYPE_AVAILABLE);
        flow.setBizType(BIZ_TYPE_PURCHASE_IN);
        flow.setBizNo(bizNo);
        flow.setQtyChange(change);
        flow.setQtyBefore(before);
        flow.setQtyAfter(after);
        flow.setRemark(remark);
        stockFlowMapper.insert(flow);
    }

    private void writeCostLog(Long bookId, BookCost cost, String refNo)
    {
        CostChangeLog log = new CostChangeLog();
        log.setBookId(bookId);
        log.setChangeType(COST_CHANGE_TYPE_PURCHASE_IN);
        log.setQty(cost.inQty);
        log.setUnitCost(cost.unitCost);
        log.setBeforeCost(cost.beforeCost);
        log.setAfterCost(cost.afterCost);
        log.setRefNo(refNo);
        costChangeLogMapper.insert(log);
    }

    // ==================================================================
    // 内部：小工具
    // ==================================================================

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }

    private Long nzl(Long v)
    {
        return (v == null) ? 0L : v;
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    /** 单本书本次入库的成本重算结果（pass 1 产出，pass 2 / 3 消费） */
    private static final class BookCost
    {
        private BigDecimal beforeCost;
        private BigDecimal afterCost;
        private BigDecimal unitCost;
        private BigDecimal inAmount;
        private int inQty;
    }
}
