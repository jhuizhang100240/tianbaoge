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
import com.tbgbook.bookstore.stock.domain.LossApproveResult;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockFlow;
import com.tbgbook.bookstore.stock.mapper.LossApplyItemMapper;
import com.tbgbook.bookstore.stock.mapper.LossApplyMapper;
import com.tbgbook.bookstore.stock.mapper.StockFlowMapper;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.service.ILossApplyService;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 报损申请单 Service 实现（阶段三步骤④）。
 *
 * <p>骨架沿用 {@code StockInServiceImpl}，但有三处与其余单据不同，也正是本类要盯住的：
 * <ol>
 *   <li><b>库存变动推迟到审批那一步</b>。开单只落单据与明细 —— 而
 *       {@code biz_loss_apply_item.stock_qty} 存的是**申请时点**的可售快照，
 *       它只是给审批人的参考，不是扣减依据。真正的依据是审批那一刻的实时库存
 *       （申请到审批之间书可能被卖掉），所以 {@link #approve} 会重新校验一遍</li>
 *   <li><b>成本单价由服务端取，客户端传了不认</b>。它决定 {@code totalAmount}，
 *       而后者是「超 500 元需二次确认」的判据 —— 见 {@code LossApplyItem} 类注释</li>
 *   <li><b>三态终局而非两态</b>：驳回与撤销都停在终态且不动库存，
 *       只有 {@code 2已通过} 会产生流水</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class LossApplyServiceImpl extends ServiceImpl<LossApplyMapper, LossApply>
        implements ILossApplyService
{
    /** 报损单号前缀（契约：BS + yyyyMMdd + 流水） */
    private static final String APPLY_NO_PREFIX = "BS";

    /** 阈值兜底值（配置缺失或写坏时用，与 `04_ruoyi_custom.sql` 的种子值一致） */
    private static final BigDecimal DEFAULT_CONFIRM_AMOUNT = new BigDecimal("500");

    /** 金额列一律两位小数（`decimal(12,2)`） */
    private static final int AMOUNT_SCALE = 2;

    /** `biz_stock_flow.stock_type`：可售（报损只动可售） */
    private static final int STOCK_TYPE_AVAILABLE = 1;

    /** `biz_stock_flow.biz_type`：报损 */
    private static final int BIZ_TYPE_LOSS = 6;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final StockMapper stockMapper;

    private final StockFlowMapper stockFlowMapper;

    private final LossApplyItemMapper itemMapper;

    private final ISysConfigService configService;

    @Override
    public BigDecimal confirmAmountThreshold()
    {
        // NFR-M4/R7：阈值必须运营可配，不得写死在 Java 里。走 ISysConfigService（带 Redis 缓存）
        String raw = configService.selectConfigByKey(CFG_CONFIRM_AMOUNT);
        if (raw == null || raw.isBlank())
        {
            return DEFAULT_CONFIRM_AMOUNT;
        }
        try
        {
            return new BigDecimal(raw.trim());
        }
        catch (NumberFormatException e)
        {
            // 运营把参数改坏了不该让报损功能整体不可用 —— 回落默认值并继续
            return DEFAULT_CONFIRM_AMOUNT;
        }
    }

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<LossApply> selectLossApplyList(LossApply apply)
    {
        return baseMapper.selectLossApplyList(apply);
    }

    @Override
    public LossApply selectLossApplyById(Long applyId)
    {
        LossApply apply = referenceGuard.requireExists(baseMapper, applyId, "报损单");
        apply.setItems(selectItems(applyId));
        return apply;
    }

    // ==================================================================
    // 写：待审批阶段的开单 / 改单 / 删单（**都不动库存**）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addLossApply(LossApply apply)
    {
        // 必填性按**操作**判定，不靠字段注解 —— 本实体 POST/PUT 共用，
        // 注解不知道这次是哪种操作（StockInItem#bookId 上踩过同一个坑）
        if (apply.getLossType() == null)
        {
            throw BizErrors.badRequest("报损类型不能为空");
        }
        requireItems(apply.getItems());

        // 服务端接管字段：客户端传了也不认
        apply.setApplyId(null);
        apply.setApplyNo(null);
        apply.setStatus(LossApply.STATUS_PENDING);
        apply.setApplyBy(SecurityUtils.getUsername());
        apply.setApplyTime(LocalDateTime.now());
        apply.setAuditBy(null);
        apply.setAuditTime(null);
        apply.setAuditRemark(null);

        prepareItems(apply.getItems());
        summarize(apply);

        // apply_no 是 NOT NULL 且带唯一索引 —— 不能「先插入再回填」。
        // 与 StockInServiceImpl#addStockIn 同一手法：预取 AUTO_INCREMENT 生成，插入后校正。
        Long predictedId = baseMapper.selectNextApplyId();
        apply.setApplyNo(buildApplyNo(predictedId == null ? 1L : predictedId));

        int rows = baseMapper.insert(apply);
        persistItems(apply.getApplyId(), apply.getItems());

        String actualNo = buildApplyNo(apply.getApplyId());
        if (!actualNo.equals(apply.getApplyNo()))
        {
            LossApply patch = new LossApply();
            patch.setApplyId(apply.getApplyId());
            patch.setApplyNo(actualNo);
            baseMapper.updateById(patch);
            apply.setApplyNo(actualNo);
        }
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateLossApply(LossApply apply)
    {
        if (apply.getApplyId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的报损单");
        }
        LossApply current = referenceGuard.requireExists(baseMapper, apply.getApplyId(), "报损单");
        // 只有待审批可改。已通过的已扣过库存；已驳回 / 已撤销是终态记录，
        // 要重新报损应当新开一单（这样审批链才完整）
        referenceGuard.requireState(current.getStatus(), LossApply.STATUS_PENDING, "报损单", "待审批");

        apply.setApplyNo(null);
        apply.setStatus(null);
        apply.setApplyBy(null);
        apply.setApplyTime(null);
        apply.setAuditBy(null);
        apply.setAuditTime(null);
        apply.setAuditRemark(null);

        if (apply.getItems() != null)
        {
            requireItems(apply.getItems());
            // 重新取一次库存快照与成本单价：申请放了两天再改数量，
            // 沿用它当初的快照会给出误导性的「当前库存」
            prepareItems(apply.getItems());
            summarize(apply);
            itemMapper.deleteByApplyIds(new Long[] { apply.getApplyId() });
            persistItems(apply.getApplyId(), apply.getItems());
        }
        else
        {
            apply.setBookKind(null);
            apply.setTotalQty(null);
            apply.setTotalAmount(null);
            apply.setNeedConfirm(null);
        }

        int rows = baseMapper.updateById(apply);
        referenceGuard.requireAffected(rows, "报损单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeLossApplies(Long[] applyIds)
    {
        List<Long> idList = (applyIds == null) ? Collections.emptyList() : Arrays.asList(applyIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "报损单");

        for (Serializable id : ids)
        {
            LossApply one = referenceGuard.requireExists(baseMapper, id, "报损单");
            if (Objects.equals(one.getStatus(), LossApply.STATUS_APPROVED))
            {
                throw BizErrors.ruleViolated("报损单「" + one.getApplyNo()
                        + "」已审批通过，是库存的实际凭证（流水按单据号引用它），不能删除");
            }
        }

        // 主从级联：**先删明细再删头**，顺序不可颠倒（docs/db/README.md §6.1 第 3 类）
        itemMapper.deleteByApplyIds(idList.toArray(new Long[0]));
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 写：审批（**唯一的库存扣减动作**）
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LossApproveResult approve(Long applyId, String auditRemark)
    {
        LossApply apply = requirePending(applyId);
        List<LossApplyItem> items = selectItems(applyId);
        requireItems(items);

        // ---------- 先整体校验：任一行超过当前可售即拦下，一条流水都不产生 ----------
        // 注意依据是**审批这一刻**的实时库存，不是明细里的 stockQty 快照 ——
        // 快照是申请时点的，申请到审批之间书可能已经卖掉（LossApplyItem 类注释）
        for (LossApplyItem it : items)
        {
            int have = availableOf(it.getBookId(), nzl(it.getLocationId()));
            if (have < nzi(it.getQty()))
            {
                throw BizErrors.ruleViolated("「" + it.getTitle() + "」当前可售 " + have
                        + " 册，不够本单报损的 " + nzi(it.getQty()) + " 册；"
                        + "请修改本单数量后再审批");
            }
        }

        // ---------- 逐行扣减可售库存 + 写流水 ----------
        LocalDateTime auditTime = LocalDateTime.now();
        String auditor = SecurityUtils.getUsername();
        List<LossApproveResult.Row> rows = new ArrayList<>(items.size());
        for (LossApplyItem it : items)
        {
            int qty = nzi(it.getQty());
            Long locationId = nzl(it.getLocationId());
            Stock current = findStock(it.getBookId(), locationId);
            int before = nzi(current.getQtyAvailable());

            // 扣减走 @Version 乐观锁；影响 0 行时 MP 不抛异常，必须 requireAffected 转 409
            Stock patch = new Stock();
            patch.setStockId(current.getStockId());
            patch.setVersion(current.getVersion());
            patch.setQtyAvailable(before - qty);
            referenceGuard.requireAffected(stockMapper.updateById(patch), "库存");

            writeFlow(it.getBookId(), locationId, -qty, before, before - qty, apply.getApplyNo());

            LossApproveResult.Row row = new LossApproveResult.Row();
            row.setItemId(it.getItemId());
            row.setBookId(it.getBookId());
            row.setTitle(it.getTitle());
            row.setLocationId(locationId);
            row.setQtyChange(qty);
            row.setStockBefore(before);
            row.setStockAfter(before - qty);
            row.setUnitCost(it.getUnitCost());
            row.setAmount(it.getAmount());
            rows.add(row);
        }

        // ---------- 单据置已通过 ----------
        LossApply patch = new LossApply();
        patch.setApplyId(applyId);
        patch.setStatus(LossApply.STATUS_APPROVED);
        patch.setAuditBy(auditor);
        patch.setAuditTime(auditTime);
        patch.setAuditRemark(auditRemark);
        referenceGuard.requireAffected(baseMapper.updateById(patch), "报损单");

        LossApproveResult result = new LossApproveResult();
        result.setApplyId(applyId);
        result.setApplyNo(apply.getApplyNo());
        result.setTotalQty(apply.getTotalQty());
        result.setTotalAmount(apply.getTotalAmount());
        result.setStatus(LossApply.STATUS_APPROVED);
        result.setAuditBy(auditor);
        result.setAuditTime(auditTime);
        result.setRows(rows);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(Long applyId, String auditRemark)
    {
        requirePending(applyId);
        // 一个 DTO 服务 approve / reject 两个端点，字段注解分不清这次是哪个端点，
        // 故驳回的必填性在这里判（与 StockInItem 上「必填性按操作判定」同一经验）
        if (auditRemark == null || auditRemark.isBlank())
        {
            throw BizErrors.badRequest("驳回原因不能为空");
        }

        LossApply patch = new LossApply();
        patch.setApplyId(applyId);
        patch.setStatus(LossApply.STATUS_REJECTED);
        patch.setAuditBy(SecurityUtils.getUsername());
        patch.setAuditTime(LocalDateTime.now());
        patch.setAuditRemark(auditRemark);
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "报损单");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revoke(Long applyId)
    {
        LossApply apply = requirePending(applyId);

        // 只能撤销本人提交的单据（契约 403）
        String me = SecurityUtils.getUsername();
        if (!Objects.equals(apply.getApplyBy(), me))
        {
            throw BizErrors.forbidden("报损单「" + apply.getApplyNo() + "」由「"
                    + apply.getApplyBy() + "」提交，只能由本人撤销");
        }

        LossApply patch = new LossApply();
        patch.setApplyId(applyId);
        patch.setStatus(LossApply.STATUS_REVOKED);
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "报损单");
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 单据必须存在且处于待审批状态（否则 409「已被处理」） */
    private LossApply requirePending(Long applyId)
    {
        LossApply apply = referenceGuard.requireExists(baseMapper, applyId, "报损单");
        if (!Objects.equals(apply.getStatus(), LossApply.STATUS_PENDING))
        {
            throw BizErrors.conflict("报损单「" + apply.getApplyNo() + "」当前为「"
                    + statusName(apply.getStatus()) + "」，不能重复处理");
        }
        return apply;
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case LossApply.STATUS_PENDING -> "待审批";
            case LossApply.STATUS_APPROVED -> "已通过";
            case LossApply.STATUS_REJECTED -> "已驳回";
            case LossApply.STATUS_REVOKED -> "已撤销";
            default -> "未知";
        };
    }

    /** 按需求 §3.2 的编码形态生成报损单号：{@code BS} + yyyyMMdd + 6 位流水 */
    private String buildApplyNo(Long applyId)
    {
        return APPLY_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", applyId);
    }

    private List<LossApplyItem> selectItems(Long applyId)
    {
        LossApplyItem query = new LossApplyItem();
        query.setApplyId(applyId);
        return itemMapper.selectLossApplyItemList(query);
    }

    private void requireItems(List<LossApplyItem> items)
    {
        if (items == null || items.isEmpty())
        {
            throw BizErrors.ruleViolated("报损单至少要有一行明细");
        }
    }

    /**
     * 校验引用、回填冗余列、取成本单价与库存快照、计算金额。
     *
     * <p><b>{@code unitCost} 取自 {@code biz_book.cost_price}</b>，不接受客户端传入 ——
     * 理由见 {@code LossApplyItem} 类注释（它决定「超 500 元需二次确认」这道闸门）。
     * 成本价为 0（从未核算过）时金额也为 0，此时闸门不会触发，属数据质量问题而非逻辑问题。
     *
     * <p><b>{@code stockQty} 取该「图书 + 库位」当前的可售数</b>（无库存行时记 0）——
     * 报损扣的是这个库位的库存，快照也应取同一口径，否则审批人会拿一个对不上的数去判断。
     */
    private void prepareItems(List<LossApplyItem> items)
    {
        for (LossApplyItem it : items)
        {
            if (it.getBookId() == null)
            {
                throw BizErrors.badRequest("报损明细的图书不能为空");
            }
            if (it.getQty() == null)
            {
                throw BizErrors.badRequest("报损明细的数量不能为空");
            }
            if (it.getQty() <= 0)
            {
                throw BizErrors.ruleViolated("报损数量必须大于 0（当前为 " + it.getQty() + "）");
            }
            Book book = referenceGuard.requireExists(bookMapper, it.getBookId(), "图书");
            it.setIsbn(book.getIsbn());
            it.setTitle(book.getTitle());

            // 库位为 0（未分配库位）是 `biz_stock.location_id` 允许的合法值
            if (it.getLocationId() == null || it.getLocationId() < 0)
            {
                it.setLocationId(0L);
            }

            it.setUnitCost(nz(book.getCostPrice()));
            it.setAmount(nz(book.getCostPrice())
                    .multiply(BigDecimal.valueOf(it.getQty()))
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
            it.setStockQty(availableOf(it.getBookId(), nzl(it.getLocationId())));
        }
    }

    /**
     * 聚合单据头的统计列，并按阈值置 {@code needConfirm}。
     *
     * <p>阈值比较用 {@code compareTo} 而不是 {@code equals}：
     * {@code BigDecimal("500.00").equals(new BigDecimal("500"))} 是 {@code false}
     * （scale 不同），用 equals 会让「恰好 500 元」漏判 —— 与阶段二那处
     * 「假价格变更」是同一类陷阱（{@code 49.00} vs {@code 49}）。
     */
    private void summarize(LossApply apply)
    {
        Set<Long> books = new HashSet<>();
        int qty = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (LossApplyItem it : apply.getItems())
        {
            books.add(it.getBookId());
            qty += nzi(it.getQty());
            amount = amount.add(nz(it.getAmount()));
        }
        apply.setBookKind(books.size());
        apply.setTotalQty(qty);
        BigDecimal total = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        apply.setTotalAmount(total);
        apply.setNeedConfirm(total.compareTo(confirmAmountThreshold()) > 0 ? 1 : 0);
    }

    private void persistItems(Long applyId, List<LossApplyItem> items)
    {
        for (LossApplyItem it : items)
        {
            it.setApplyId(applyId);
            it.setItemId(null);
            itemMapper.insert(it);
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

    private Stock findStock(Long bookId, Long locationId)
    {
        return stockMapper.selectOne(Wrappers.<Stock>lambdaQuery()
                .eq(Stock::getBookId, bookId)
                .eq(Stock::getLocationId, locationId));
    }

    /**
     * 写一条报损流水。
     *
     * <p>{@code qtyBefore} / {@code qtyAfter} 一定要记 —— 只记 {@code qtyChange} 的话，
     * 事后无法校验账面连续性（「流水加起来等于结存」这条对账规则就失效了）。
     */
    private void writeFlow(Long bookId, Long locationId, int change, int before, int after, String applyNo)
    {
        StockFlow flow = new StockFlow();
        flow.setBookId(bookId);
        flow.setLocationId(locationId);
        flow.setStockType(STOCK_TYPE_AVAILABLE);
        flow.setBizType(BIZ_TYPE_LOSS);
        flow.setBizNo(applyNo);
        flow.setQtyChange(change);
        flow.setQtyBefore(before);
        flow.setQtyAfter(after);
        flow.setRemark("报损出库");
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

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }
}
