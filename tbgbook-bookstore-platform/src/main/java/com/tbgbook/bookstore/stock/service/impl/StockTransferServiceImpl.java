package com.tbgbook.bookstore.stock.service.impl;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.tbgbook.bookstore.stock.domain.Location;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockFlow;
import com.tbgbook.bookstore.stock.domain.StockTransfer;
import com.tbgbook.bookstore.stock.domain.StockTransferConfirmResult;
import com.tbgbook.bookstore.stock.domain.StockTransferItem;
import com.tbgbook.bookstore.stock.mapper.LocationMapper;
import com.tbgbook.bookstore.stock.mapper.StockFlowMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.mapper.StockTransferItemMapper;
import com.tbgbook.bookstore.stock.mapper.StockTransferMapper;
import com.tbgbook.bookstore.stock.service.IStockTransferService;

import lombok.RequiredArgsConstructor;

/**
 * 库存调拨单 Service 实现（阶段三步骤④）。
 *
 * <p>骨架沿用 {@code StockInServiceImpl}（全项目「单据改库存」的样板），
 * 但有一处本质差异，也是本类最要紧的地方：
 *
 * <p><b>调拨是唯一「一次改两个库位」的动作</b>。入库只加、出库只减，
 * 而调拨要在同一事务里「这个库位减、那个库位加」。由此派生出三条约束：
 * <ol>
 *   <li><b>两条流水缺一不可</b>（{@code biz_type=10 调拨出} 与 {@code 9 调拨入}）。
 *       只写一条的话，「流水加起来等于结存」这条对账规则会在调拨处断开</li>
 *   <li><b>调拨前后实物总量不变</b>。这是可断言的不变量 ——
 *       本单不增不减任何一本，只是换了库位（{@code StockTransfer} 类注释）</li>
 *   <li><b>调出与调入库位不可相同</b>。相同则「减了又加」回到原点，
 *       却凭空产生两条流水；契约把它列为 422</li>
 * </ol>
 *
 * <p><b>为什么确认前要先整体校验</b>：调拨是成对动作，若逐行「减了才发现下一行不够」，
 * 虽然事务会整体回滚，但错误只报第一处、用户要改一次试一次。先把所有行的库存不足
 * 一次性查完，错误一次给全（与 {@code StockInServiceImpl#voidStockIn} 同一处置）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl extends ServiceImpl<StockTransferMapper, StockTransfer>
        implements IStockTransferService
{
    /**
     * 调拨单号前缀：{@code DB} + yyyyMMdd + 6 位流水。
     *
     * <p>需求 §3.2 只给了入库 {@code RK}、盘点 {@code PD}、报损 {@code BS} 三个前缀，
     * 调拨未定义。此处按同一形态取「调拨」的拼音首字母 {@code DB}，
     * 与既有三个前缀保持一致的「2 字母 + 8 位日期 + 6 位流水」结构。
     */
    private static final String TRANSFER_NO_PREFIX = "DB";

    /** `biz_stock_flow.stock_type`：可售（调拨只动可售） */
    private static final int STOCK_TYPE_AVAILABLE = 1;

    /** `biz_stock_flow.biz_type`：调拨入（书**进入**流水记录所指的那个库位） */
    private static final int BIZ_TYPE_TRANSFER_IN = 9;

    /** `biz_stock_flow.biz_type`：调拨出（书**离开**流水记录所指的那个库位） */
    private static final int BIZ_TYPE_TRANSFER_OUT = 10;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final LocationMapper locationMapper;

    private final StockMapper stockMapper;

    private final StockFlowMapper stockFlowMapper;

    private final StockTransferItemMapper itemMapper;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<StockTransfer> selectStockTransferList(StockTransfer transfer)
    {
        return baseMapper.selectStockTransferList(transfer);
    }

    @Override
    public StockTransfer selectStockTransferById(Long transferId)
    {
        StockTransfer transfer = referenceGuard.requireExists(baseMapper, transferId, "调拨单");
        transfer.setItems(selectItems(transferId));
        return transfer;
    }

    // ==================================================================
    // 写：草稿的开单 / 改单 / 删单（**都不动库存**）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addStockTransfer(StockTransfer transfer)
    {
        // 必填性按**操作**判定，不靠字段注解 —— 本实体 POST/PUT 共用，
        // 注解不知道这次是哪种操作（StockInItem#bookId 上踩过同一个坑）
        if (transfer.getFromLocationId() == null)
        {
            throw BizErrors.badRequest("调出库位不能为空");
        }
        if (transfer.getToLocationId() == null)
        {
            throw BizErrors.badRequest("调入库位不能为空");
        }
        requireItems(transfer.getItems());
        requireDistinctLocations(transfer.getFromLocationId(), transfer.getToLocationId());
        referenceGuard.requireExists(locationMapper, transfer.getFromLocationId(), "调出库位");
        referenceGuard.requireExists(locationMapper, transfer.getToLocationId(), "调入库位");

        // 服务端接管字段：客户端传了也不认
        transfer.setTransferId(null);
        transfer.setTransferNo(null);
        transfer.setStatus(StockTransfer.STATUS_DRAFT);
        transfer.setTransferTime(null);

        prepareItems(transfer.getItems());
        summarize(transfer);

        // transfer_no 是 NOT NULL 且带唯一索引 —— 不能「先插入再回填」。
        // 与 StockInServiceImpl#addStockIn 同一手法：预取 AUTO_INCREMENT 生成，插入后校正。
        Long predictedId = baseMapper.selectNextTransferId();
        transfer.setTransferNo(buildTransferNo(predictedId == null ? 1L : predictedId));

        int rows = baseMapper.insert(transfer);
        persistItems(transfer.getTransferId(), transfer.getItems());

        String actualNo = buildTransferNo(transfer.getTransferId());
        if (!actualNo.equals(transfer.getTransferNo()))
        {
            StockTransfer patch = new StockTransfer();
            patch.setTransferId(transfer.getTransferId());
            patch.setTransferNo(actualNo);
            baseMapper.updateById(patch);
            transfer.setTransferNo(actualNo);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateStockTransfer(StockTransfer transfer)
    {
        if (transfer.getTransferId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的调拨单");
        }
        StockTransfer current = referenceGuard.requireExists(baseMapper, transfer.getTransferId(), "调拨单");
        // 已调拨的单据一旦改动库位或明细，库存与单据就对不上了 —— 只能作废重开
        referenceGuard.requireState(current.getStatus(), StockTransfer.STATUS_DRAFT, "调拨单", "草稿");

        // 库位没传就沿用原值（PUT 允许只改明细）
        Long fromId = (transfer.getFromLocationId() == null)
                ? current.getFromLocationId() : transfer.getFromLocationId();
        Long toId = (transfer.getToLocationId() == null)
                ? current.getToLocationId() : transfer.getToLocationId();
        requireDistinctLocations(fromId, toId);
        referenceGuard.requireExists(locationMapper, fromId, "调出库位");
        referenceGuard.requireExists(locationMapper, toId, "调入库位");
        transfer.setFromLocationId(fromId);
        transfer.setToLocationId(toId);

        transfer.setTransferNo(null);
        transfer.setStatus(null);
        transfer.setTransferTime(null);

        if (transfer.getItems() != null)
        {
            requireItems(transfer.getItems());
            prepareItems(transfer.getItems());
            summarize(transfer);
            // 明细整体替换：删旧插新（明细没有服务端算的派生列，但「头与明细同事务」的语义更清晰）
            itemMapper.deleteByTransferIds(new Long[] { transfer.getTransferId() });
            persistItems(transfer.getTransferId(), transfer.getItems());
        }
        else
        {
            // 未传 items = 只改单据头，统计列保持原值（置 null 让 MP 跳过它们）
            transfer.setBookKind(null);
            transfer.setTotalQty(null);
        }

        int rows = baseMapper.updateById(transfer);
        referenceGuard.requireAffected(rows, "调拨单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeStockTransfers(Long[] transferIds)
    {
        List<Long> idList = (transferIds == null) ? Collections.emptyList() : Arrays.asList(transferIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "调拨单");

        for (Serializable id : ids)
        {
            StockTransfer one = referenceGuard.requireExists(baseMapper, id, "调拨单");
            if (!Objects.equals(one.getStatus(), StockTransfer.STATUS_DRAFT))
            {
                throw BizErrors.ruleViolated("调拨单「" + one.getTransferNo()
                        + "」不是草稿状态，不能删除；已调拨的单据请改用作废"
                        + "（作废会把书挪回原库位）");
            }
        }

        // 主从级联：**先删明细再删头**，顺序不可颠倒（docs/db/README.md §6.1 第 3 类）
        itemMapper.deleteByTransferIds(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 写：确认调拨（**真库存挪动的动作**）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockTransferConfirmResult confirm(Long transferId, LocalDateTime transferTime)
    {
        StockTransfer transfer = referenceGuard.requireExists(baseMapper, transferId, "调拨单");

        // 防重复提交：已调拨再点一次要给明确的 409，而不是静默把书再挪一遍
        if (Objects.equals(transfer.getStatus(), StockTransfer.STATUS_TRANSFERRED))
        {
            throw BizErrors.conflict("调拨单「" + transfer.getTransferNo() + "」已是已调拨状态，不能重复调拨");
        }
        if (Objects.equals(transfer.getStatus(), StockTransfer.STATUS_VOIDED))
        {
            throw BizErrors.ruleViolated("调拨单「" + transfer.getTransferNo() + "」已作废，不能再调拨");
        }

        Long fromId = nzl(transfer.getFromLocationId());
        Long toId = nzl(transfer.getToLocationId());
        requireDistinctLocations(fromId, toId);

        List<StockTransferItem> items = selectItems(transferId);
        requireItems(items);
        validateBeforeConfirm(items);

        // ---------- 先整体校验：任一行的调出库位库存不足即拦下，一条流水都不产生 ----------
        for (StockTransferItem it : items)
        {
            int have = availableOf(it.getBookId(), fromId);
            if (have < nzi(it.getQty()))
            {
                throw BizErrors.notEnough("调出库位「" + locationLabel(fromId) + "」的可售库存",
                        "「" + it.getTitle() + "」当前 " + have + " 册，本单要调出 " + nzi(it.getQty()) + " 册");
            }
        }

        // ---------- 逐行挪库存 + 写两条流水 ----------
        LocalDateTime effectiveTime = (transferTime == null) ? LocalDateTime.now() : transferTime;
        List<StockTransferConfirmResult.Row> rows = new ArrayList<>(items.size());
        for (StockTransferItem it : items)
        {
            int qty = nzi(it.getQty());
            Long bookId = it.getBookId();

            int[] fromBeforeHolder = new int[1];
            int fromAfter = applyDelta(bookId, fromId, -qty, fromBeforeHolder);

            int[] toBeforeHolder = new int[1];
            int toAfter = applyDelta(bookId, toId, qty, toBeforeHolder);

            // 两条流水：方向语义是「10 = 离开该库位 / 9 = 进入该库位」
            writeFlow(bookId, fromId, BIZ_TYPE_TRANSFER_OUT, -qty,
                    fromBeforeHolder[0], fromAfter, transfer.getTransferNo(), "调拨出");
            writeFlow(bookId, toId, BIZ_TYPE_TRANSFER_IN, qty,
                    toBeforeHolder[0], toAfter, transfer.getTransferNo(), "调拨入");

            StockTransferConfirmResult.Row row = new StockTransferConfirmResult.Row();
            row.setItemId(it.getItemId());
            row.setBookId(bookId);
            row.setTitle(it.getTitle());
            row.setQty(qty);
            row.setFromLocationId(fromId);
            row.setFromStockBefore(fromBeforeHolder[0]);
            row.setFromStockAfter(fromAfter);
            row.setToLocationId(toId);
            row.setToStockBefore(toBeforeHolder[0]);
            row.setToStockAfter(toAfter);
            rows.add(row);
        }

        // ---------- 单据置已调拨 ----------
        StockTransfer patch = new StockTransfer();
        patch.setTransferId(transferId);
        patch.setStatus(StockTransfer.STATUS_TRANSFERRED);
        patch.setTransferTime(effectiveTime);
        patch.setBookKind(transfer.getBookKind());
        patch.setTotalQty(transfer.getTotalQty());
        referenceGuard.requireAffected(baseMapper.updateById(patch), "调拨单");

        StockTransferConfirmResult result = new StockTransferConfirmResult();
        result.setTransferId(transferId);
        result.setTransferNo(transfer.getTransferNo());
        result.setBookKind(transfer.getBookKind());
        result.setTotalQty(transfer.getTotalQty());
        result.setStatus(StockTransfer.STATUS_TRANSFERRED);
        result.setTransferTime(effectiveTime);
        result.setRows(rows);
        return result;
    }

    // ==================================================================
    // 写：作废（已调拨的要反向挪回）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidTransfer(Long transferId, String reason)
    {
        StockTransfer transfer = referenceGuard.requireExists(baseMapper, transferId, "调拨单");
        if (Objects.equals(transfer.getStatus(), StockTransfer.STATUS_VOIDED))
        {
            throw BizErrors.conflict("调拨单「" + transfer.getTransferNo() + "」已是已作废状态");
        }

        if (Objects.equals(transfer.getStatus(), StockTransfer.STATUS_TRANSFERRED))
        {
            Long fromId = nzl(transfer.getFromLocationId());
            Long toId = nzl(transfer.getToLocationId());
            List<StockTransferItem> items = selectItems(transferId);

            // 先整体校验：不要改到一半才发现某一行扣不回来
            for (StockTransferItem it : items)
            {
                int have = availableOf(it.getBookId(), toId);
                if (have < nzi(it.getQty()))
                {
                    throw BizErrors.ruleViolated("「" + it.getTitle() + "」在调入库位「"
                            + locationLabel(toId) + "」当前可售 " + have + " 册，不足以扣回本单的 "
                            + nzi(it.getQty()) + " 册 —— 该单调拨后这批书已部分出库"
                            + "（销售 / 报损 / 再次调拨），不能直接作废；请走退货或报损流程");
                }
            }

            for (StockTransferItem it : items)
            {
                int qty = nzi(it.getQty());
                Long bookId = it.getBookId();

                int[] toBeforeHolder = new int[1];
                int toAfter = applyDelta(bookId, toId, -qty, toBeforeHolder);

                int[] fromBeforeHolder = new int[1];
                int fromAfter = applyDelta(bookId, fromId, qty, fromBeforeHolder);

                // 红字流水：方向语义与正向一致 —— 10 = 离开该库位 / 9 = 进入该库位。
                // 故冲减时「调入库位」是 10（书离开它）、「调出库位」是 9（书回到它）。
                writeFlow(bookId, toId, BIZ_TYPE_TRANSFER_OUT, -qty,
                        toBeforeHolder[0], toAfter, transfer.getTransferNo(), "作废冲减");
                writeFlow(bookId, fromId, BIZ_TYPE_TRANSFER_IN, qty,
                        fromBeforeHolder[0], fromAfter, transfer.getTransferNo(), "作废冲减");
            }
        }

        StockTransfer patch = new StockTransfer();
        patch.setTransferId(transferId);
        patch.setStatus(StockTransfer.STATUS_VOIDED);
        patch.setRemark(appendReason(transfer.getRemark(), reason));
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "调拨单");
        return rows;
    }

    /**
     * 把作废原因并入备注。
     *
     * <p>⚠️ {@code biz_stock_transfer} **没有作废原因列**，而契约把 {@code reason} 定为**必填**。
     * 一个必填字段无处安放是不可接受的，故并入 {@code remark} 并加可识别前缀
     * （与 {@code StockInServiceImpl#appendReason} 同一处置）。
     */
    private String appendReason(String remark, String reason)
    {
        String base = (remark == null) ? "" : remark.trim();
        String note = "【作废】" + ((reason == null || reason.isBlank()) ? "未填原因" : reason.trim());
        return base.isEmpty() ? note : base + " | " + note;
    }

    /** 按需求 §3.2 的编码形态生成调拨单号：{@code DB} + yyyyMMdd + 6 位流水 */
    private String buildTransferNo(Long transferId)
    {
        return TRANSFER_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", transferId);
    }

    // ==================================================================
    // 内部：明细的准备与聚合
    // ==================================================================

    /** 按单据头主键取明细（走 XML 的列表查询，字段显式列出） */
    private List<StockTransferItem> selectItems(Long transferId)
    {
        StockTransferItem query = new StockTransferItem();
        query.setTransferId(transferId);
        return itemMapper.selectStockTransferItemList(query);
    }

    private void requireItems(List<StockTransferItem> items)
    {
        if (items == null || items.isEmpty())
        {
            throw BizErrors.ruleViolated("调拨单至少要有一行明细");
        }
    }

    /**
     * 调出与调入库位必须不同。
     *
     * <p>相同则「减了又加」回到原点，却凭空产生两条流水，破坏
     * 「流水加起来等于结存」的对账规则。契约把它列为 422。
     */
    private void requireDistinctLocations(Long fromLocationId, Long toLocationId)
    {
        if (Objects.equals(nzl(fromLocationId), nzl(toLocationId)))
        {
            throw BizErrors.ruleViolated("调出库位与调入库位不能相同 —— 调拨是把书从一个库位挪到另一个库位");
        }
    }

    /**
     * 校验外键引用、回填冗余列、校验同一本书不重复。
     *
     * <p>零物理外键（{@code docs/db/README.md} §6.1），引用完整性靠这一层：
     * {@code bookId} 必须存在（404），两个库位也必须是真实库位（404）。
     *
     * <p><b>同一本书不允许出现多行</b>：单据头只有一个调出 / 一个调入库位，
     * 拆成两行只会产生两条重复流水，对业务没有任何增量（与
     * {@code StockInServiceImpl#validateBeforeConfirm} 的查重同一理由）。
     */
    private void prepareItems(List<StockTransferItem> items)
    {
        Set<Long> seenBooks = new HashSet<>();
        for (StockTransferItem it : items)
        {
            if (it.getBookId() == null)
            {
                throw BizErrors.badRequest("调拨明细的图书不能为空");
            }
            if (it.getQty() == null)
            {
                throw BizErrors.badRequest("调拨明细的数量不能为空");
            }
            if (it.getQty() <= 0)
            {
                throw BizErrors.ruleViolated("调拨数量必须大于 0（当前为 " + it.getQty() + "）");
            }
            Book book = referenceGuard.requireExists(bookMapper, it.getBookId(), "图书");
            it.setIsbn(book.getIsbn());
            it.setTitle(book.getTitle());
            if (!seenBooks.add(it.getBookId()))
            {
                throw BizErrors.ruleViolated("「" + book.getTitle()
                        + "」在明细里出现了多行，请合并为一行后再提交");
            }
        }
    }

    /** 聚合单据头的统计列（品种数 / 总数量），两个都是服务端维护 */
    private void summarize(StockTransfer transfer)
    {
        Set<Long> books = new HashSet<>();
        int qty = 0;
        for (StockTransferItem it : transfer.getItems())
        {
            books.add(it.getBookId());
            qty += nzi(it.getQty());
        }
        transfer.setBookKind(books.size());
        transfer.setTotalQty(qty);
    }

    private void persistItems(Long transferId, List<StockTransferItem> items)
    {
        for (StockTransferItem it : items)
        {
            it.setTransferId(transferId);
            it.setItemId(null);
            itemMapper.insert(it);
        }
    }

    // ==================================================================
    // 内部：确认前的业务校验
    // ==================================================================

    /**
     * 确认前的逐行校验。
     *
     * <p>只查「行本身是否成立」；「库位里够不够」在 {@link #confirm} 里单独查一遍
     * （那一步需要读库存表，与这里的数据来源不同）。
     */
    private void validateBeforeConfirm(List<StockTransferItem> items)
    {
        Set<Long> seenBooks = new HashSet<>();
        for (StockTransferItem it : items)
        {
            if (nzi(it.getQty()) <= 0)
            {
                throw BizErrors.ruleViolated("「" + it.getTitle() + "」的调拨数量为 "
                        + nzi(it.getQty()) + "，请改成大于 0 的数量，或删除该行");
            }
            if (!seenBooks.add(it.getBookId()))
            {
                throw BizErrors.ruleViolated("「" + it.getTitle()
                        + "」在明细里出现了多行，请合并为一行后再调拨");
            }
        }
    }

    // ==================================================================
    // 内部：库存与流水的写
    // ==================================================================

    /** 某本书在某库位的可售数（无库存行时视为 0） */
    private int availableOf(Long bookId, Long locationId)
    {
        Stock row = findStock(bookId, locationId);
        return (row == null) ? 0 : nzi(row.getQtyAvailable());
    }

    /** 库位的可读标签（编码 + 名称），用于错误文案 */
    private String locationLabel(Long locationId)
    {
        Location location = locationMapper.selectById(locationId);
        if (location == null)
        {
            return String.valueOf(locationId);
        }
        return location.getLocationCode() + " " + location.getLocationName();
    }

    private Stock findStock(Long bookId, Long locationId)
    {
        return stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .eq(Stock::getLocationId, locationId));
    }

    /**
     * 把 {@code delta}（可正可负）加到该「图书 + 库位」的可售库存上。
     *
     * <p>{@code outBefore[0]} 回传变动前的可售数（调用方要写进流水与返回结果）。
     *
     * <p><b>加</b>（{@code delta > 0}）：没有库存行就建一行 —— 调入一个从未有过库存的
     * 库位是常见情形（新库位第一次收货）。
     *
     * <p><b>减</b>（{@code delta < 0}）：库存行必须存在且扣完不为负，否则 422。
     * 这里只是**兜底**：正常路径上 {@link #confirm} / {@link #voidTransfer} 已先整体
     * 校验过一遍并给了带书名的错误文案，本方法再拦一道是为了防止「校验与执行之间
     * 状态变了」这类漏网情形把库存做成负数。
     *
     * <p><b>并发</b>：已有行时走 {@code @Version} 乐观锁 —— MP 在影响 0 行时**不抛异常**，
     * 故必须 {@code requireAffected} 转成 409。没有行时插入；若并发下别人先插了，
     * 会撞 {@code uk_book_location} → 全局处理器转 409（不会静默覆盖）。
     */
    private int applyDelta(Long bookId, Long locationId, int delta, int[] outBefore)
    {
        Stock current = findStock(bookId, locationId);
        if (current == null)
        {
            if (delta < 0)
            {
                outBefore[0] = 0;
                throw BizErrors.notEnough("可售库存",
                        "库位 " + locationId + " 没有该图书（图书Id=" + bookId + "）的库存行，无法调出 "
                                + (-delta) + " 册");
            }
            Stock fresh = new Stock();
            fresh.setBookId(bookId);
            fresh.setLocationId(locationId);
            fresh.setQtyAvailable(delta);
            fresh.setQtyLocked(0);
            fresh.setQtyBorrowed(0);
            fresh.setQtyDamaged(0);
            fresh.setSafetyQty(0);
            fresh.setVersion(0);
            stockMapper.insert(fresh);
            outBefore[0] = 0;
            return delta;
        }

        int before = nzi(current.getQtyAvailable());
        int after = before + delta;
        if (after < 0)
        {
            outBefore[0] = before;
            throw BizErrors.notEnough("可售库存",
                    "库位 " + locationId + " 当前只有 " + before + " 册，无法变动 " + delta + " 册");
        }

        Stock patch = new Stock();
        patch.setStockId(current.getStockId());
        patch.setVersion(current.getVersion());
        patch.setQtyAvailable(after);
        referenceGuard.requireAffected(stockMapper.updateById(patch), "库存");
        outBefore[0] = before;
        return after;
    }

    /**
     * 写一条库存流水。
     *
     * <p>{@code qtyBefore} / {@code qtyAfter} 一定要记 —— 只记 {@code qtyChange} 的话，
     * 事后无法校验账面连续性（「流水加起来等于结存」这条对账规则就失效了）。
     *
     * <p>{@code bizType} 的方向语义：{@code 10 调拨出} = 书**离开**本流水所指的库位，
     * {@code 9 调拨入} = 书**进入**该库位。正向与作废冲减都遵循这一条，
     * 因此作废时「调入库位」写的是 10、「调出库位」写的是 9。
     */
    private void writeFlow(Long bookId, Long locationId, int bizType, int change,
            int before, int after, String bizNo, String remark)
    {
        StockFlow flow = new StockFlow();
        flow.setBookId(bookId);
        flow.setLocationId(locationId);
        flow.setStockType(STOCK_TYPE_AVAILABLE);
        flow.setBizType(bizType);
        flow.setBizNo(bizNo);
        flow.setQtyChange(change);
        flow.setQtyBefore(before);
        flow.setQtyAfter(after);
        flow.setRemark(remark);
        stockFlowMapper.insert(flow);
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
}
