package com.tbgbook.bookstore.stock.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.tbgbook.bookstore.stock.domain.StockWarning;
import com.tbgbook.bookstore.stock.domain.WarningCandidate;
import com.tbgbook.bookstore.stock.domain.WarningHandleRequest;
import com.tbgbook.bookstore.stock.domain.WarningHandleResult;
import com.tbgbook.bookstore.stock.domain.WarningScanResult;
import com.tbgbook.bookstore.stock.mapper.StockMapper;
import com.tbgbook.bookstore.stock.mapper.StockWarningMapper;
import com.tbgbook.bookstore.stock.service.IStockInService;
import com.tbgbook.bookstore.stock.service.IStockWarningService;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 库存预警 Service 实现（阶段三步骤⑤，页面 A-23）。
 *
 * <p><b>核心是一次「全店扫一遍、四类逐条判」</b>。三处容易写错的地方：
 * <ol>
 *   <li><b>阈值一个都不许硬编码</b>（NFR-M4 / R7）：安全系数、滞销天数、补货周期、
 *       临期天数四个值全走 {@code sys_config} 的 {@code biz.*} 键，
 *       运营方不重新发版就能调（第四项是本步骤新增的键，见 {@code docs/db/17_*.sql}）</li>
 *   <li><b>「动销」从库存流水读，不从订单表读</b>：销售域（阶段四）的订单在结算时写
 *       {@code biz_type=2 销售出库} 的库存流水，故流水表已经完整承载了「这本书最近卖没卖出去」。
 *       反过来从 {@code biz_order_item} 取会引入「库存域依赖销售域」的方向，
 *       而依赖方向应当相反（库存是被依赖方）</li>
 *   <li><b>去重只在「待处理」上做</b>：已处理 / 已忽略的预警视为已销项，
 *       扫描命中时**重新生成一条**，而不是把它改回待处理 —— 否则「忽略」这个动作
 *       会被下一次扫描自动推翻，「忽略」就失去了意义</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class StockWarningServiceImpl extends ServiceImpl<StockWarningMapper, StockWarning>
        implements IStockWarningService
{
    /** 补货周期天数的 `sys_config` 键（§3.2 库存规则 2 的因子） */
    private static final String CFG_REPLENISH_DAYS = "biz.stock.replenish.days";

    /** 滞销判定天数的 `sys_config` 键（§3.2 库存规则 3） */
    private static final String CFG_SLOW_MOVING_DAYS = "biz.stock.slowmoving.days";

    /** 安全系数的 `sys_config` 键（§3.2 库存规则 2） */
    private static final String CFG_SAFETY_FACTOR = "biz.stock.safety.factor";

    /** 临期判定天数的 `sys_config` 键（本步骤新增） */
    private static final String CFG_EXPIRING_DAYS = "biz.stock.expiring.days";

    /** 阈值兜底值（配置缺失或写坏时用，与 `04` / `17` 的种子值一致） */
    private static final int DEFAULT_REPLENISH_DAYS = 7;

    private static final int DEFAULT_SLOW_MOVING_DAYS = 60;

    private static final int DEFAULT_EXPIRING_DAYS = 15;

    private static final BigDecimal DEFAULT_SAFETY_FACTOR = new BigDecimal("1.5");

    /** 「近30天」的窗口长度 */
    private static final int SALE_WINDOW_DAYS = 30;

    /** `avg_sale_30d` 是 `decimal(10,2)` */
    private static final int AVG_SCALE = 2;

    /** 补货类预警的类型（可转采购入库单草稿） */
    private static final int[] REPLENISH_TYPES = { StockWarning.TYPE_REPLENISH, StockWarning.TYPE_OUT_OF_STOCK };

    /** 转单生成的草稿统一供应商名（便于运营一眼看出它不是人工开的） */
    private static final String AUTO_SUPPLIER = "补货预警自动生成";

    private final ReferenceGuard referenceGuard;

    private final StockMapper stockMapper;

    private final BookMapper bookMapper;

    private final IStockInService stockInService;

    private final ISysConfigService configService;

    // ==================================================================
    // 读
    // ==================================================================

    @Override
    public List<StockWarning> selectStockWarningList(StockWarning warning)
    {
        return baseMapper.selectStockWarningList(warning);
    }

    @Override
    public StockWarning selectStockWarningById(Long warningId)
    {
        return referenceGuard.requireExists(baseMapper, warningId, "库存预警");
    }

    // ==================================================================
    // 写：扫描
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarningScanResult scan(Integer warningType)
    {
        requireValidType(warningType);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal safetyFactor = cfgDecimal(CFG_SAFETY_FACTOR, DEFAULT_SAFETY_FACTOR);
        int replenishDays = cfgInt(CFG_REPLENISH_DAYS, DEFAULT_REPLENISH_DAYS);
        int slowMovingDays = cfgInt(CFG_SLOW_MOVING_DAYS, DEFAULT_SLOW_MOVING_DAYS);
        int expiringDays = cfgInt(CFG_EXPIRING_DAYS, DEFAULT_EXPIRING_DAYS);

        List<WarningCandidate> candidates = baseMapper.selectScanCandidates(
                now.minusDays(SALE_WINDOW_DAYS), now);

        // 待处理预警的索引：`bookId:warningType` → warningId
        Map<String, Long> pending = new HashMap<>();
        for (StockWarning row : baseMapper.selectPendingWarnings())
        {
            pending.put(row.getBookId() + ":" + row.getWarningType(), row.getWarningId());
        }

        int created = 0;
        int updated = 0;
        for (WarningCandidate c : candidates)
        {
            computeSafety(c, replenishDays, safetyFactor);
            writeBackSafety(c);

            // 四类逐条判；warningType 非空时只判指定的那一类
            for (int type : new int[] { StockWarning.TYPE_REPLENISH, StockWarning.TYPE_SLOW_MOVING,
                    StockWarning.TYPE_EXPIRING, StockWarning.TYPE_OUT_OF_STOCK })
            {
                if (warningType != null && warningType != type)
                {
                    continue;
                }
                if (!hit(c, type, slowMovingDays, expiringDays, now))
                {
                    continue;
                }
                Long existId = pending.get(c.getBookId() + ":" + type);
                if (existId == null)
                {
                    baseMapper.insert(buildRow(c, type));
                    created++;
                }
                else
                {
                    StockWarning patch = buildRow(c, type);
                    patch.setWarningId(existId);
                    baseMapper.updateById(patch);
                    updated++;
                }
            }
        }

        WarningScanResult result = new WarningScanResult();
        result.setScanned(candidates.size());
        result.setCreated(created);
        result.setUpdated(updated);
        return result;
    }

    /**
     * 四类预警的命中判定（口径见 {@link StockWarning} 类注释）。
     *
     * <p>⚠️ <b>补货要求可售 &gt; 0，零库存要求 = 0</b> —— 二者**刻意互斥**，
     * 因为原型 A-23 把「低库存 + 零库存」放在同一个页签里，
     * 让同一本书同时冒两条预警会让那个页签出现重复行。
     */
    private boolean hit(WarningCandidate c, int type, int slowMovingDays, int expiringDays,
            LocalDateTime now)
    {
        int available = nzi(c.getAvailableQty());
        int total = nzi(c.getTotalQty());
        int safety = nzi(c.getSafetyQty());

        switch (type)
        {
            case StockWarning.TYPE_REPLENISH:
                // 补货是为了卖：下架(2) / 停售(4) 的书不该提示补货
                return isSellable(c.getBookStatus())
                        && available > 0 && safety > 0 && available < safety;

            case StockWarning.TYPE_OUT_OF_STOCK:
                // 有动销史（至少有过一条库存流水）才算「断货」；
                // 刚建档、从未进货的书不算 —— 否则每本新书一建档就冒零库存预警
                return isSellable(c.getBookStatus())
                        && available == 0 && c.getFirstFlowTime() != null;

            case StockWarning.TYPE_SLOW_MOVING:
            {
                // 有货才谈得上滞销；从没卖过的按「首次入库」起算，否则它永远不滞销
                if (total <= 0)
                {
                    return false;
                }
                LocalDateTime reference = (c.getLastSaleTime() != null)
                        ? c.getLastSaleTime() : c.getFirstFlowTime();
                return reference != null && !reference.isAfter(now.minusDays(slowMovingDays));
            }

            case StockWarning.TYPE_EXPIRING:
            {
                LocalDateTime end = c.getTextbookEndTime();
                if (end == null || available <= 0)
                {
                    return false;
                }
                // SQL 已过滤掉「结束时间在过去」的清单，故这里只需判「离得够不够近」
                return !end.isAfter(now.plusDays(expiringDays));
            }

            default:
                return false;
        }
    }

    /** 在售(1) 与缺货(3) 属于「要卖的书」；下架(2) / 停售(4) 不属于 */
    private boolean isSellable(Integer bookStatus)
    {
        return Objects.equals(bookStatus, 1) || Objects.equals(bookStatus, 3);
    }

    /**
     * 按 §3.2 库存规则 2 算安全库存与日均销量。
     *
     * <pre>
     *   日均销量   = 近30天销售出库量 ÷ 30
     *   安全库存   = 日均销量 × 补货周期天数 × 安全系数（默认 1.5）
     * </pre>
     *
     * <p>安全库存**向上取整**：宁可多备一点。日均销量为 0 时安全库存为 0，
     * 意味着「没有销量数据可依据」，而非「不需要备货」——
     * 这个区分由响应里的 {@code safetyQty=0} 与 {@code avgSale30d=0} 一起表达。
     */
    private void computeSafety(WarningCandidate c, int replenishDays, BigDecimal safetyFactor)
    {
        BigDecimal avg = BigDecimal.valueOf(nzi(c.getSaleQty30d()))
                .divide(BigDecimal.valueOf(SALE_WINDOW_DAYS), AVG_SCALE, RoundingMode.HALF_UP);
        c.setAvgSale30d(avg);

        BigDecimal safety = avg.multiply(BigDecimal.valueOf(replenishDays)).multiply(safetyFactor);
        c.setSafetyQty(safety.setScale(0, RoundingMode.CEILING).intValue());
    }

    /**
     * 把算出的安全库存写回 {@code biz_stock.safety_qty}（值有变化才写）。
     *
     * <p>不写回的话「安全库存」这一列永远是建档时的 0，补货预警也就无从判定。
     * 写回走 {@code StockMapper#updateSafetyQty} 的**定列 UPDATE**而不是实体更新 ——
     * {@code safety_qty} 是派生值，不该参与 {@code @Version} 竞争
     * （否则一次后台扫描会把并发的库存单据全判成 409）。
     */
    private void writeBackSafety(WarningCandidate c)
    {
        if (Objects.equals(c.getSafetyQtyInDb(), c.getSafetyQty()))
        {
            return;
        }
        stockMapper.updateSafetyQty(c.getBookId(), c.getSafetyQty());
    }

    /** 按候选行构造一条预警（主键由调用方按新增/更新分别处理） */
    private StockWarning buildRow(WarningCandidate c, int type)
    {
        int available = nzi(c.getAvailableQty());
        int safety = nzi(c.getSafetyQty());
        int gap = Math.max(safety - available, 0);

        StockWarning row = new StockWarning();
        row.setBookId(c.getBookId());
        row.setIsbn(c.getIsbn());
        row.setTitle(c.getTitle());
        row.setWarningType(type);
        row.setCurrentQty(available);
        row.setSafetyQty(safety);
        row.setGapQty(gap);
        row.setAvgSale30d(c.getAvgSale30d());
        row.setSaleDays30d(saleDays(c));
        row.setSuggestQty(gap);
        row.setLastSaleTime(c.getLastSaleTime());
        row.setHandleStatus(StockWarning.HANDLE_PENDING);
        return row;
    }

    /**
     * 预计可售天数 = 可售 ÷ 日均销量（向下取整）。
     *
     * <p>日均销量为 0 时填 0，含义是「无销量数据、无法估算」——
     * 不是「马上卖完」。前端据此显示为「—」而不是红色告警。
     */
    private int saleDays(WarningCandidate c)
    {
        BigDecimal avg = c.getAvgSale30d();
        if (avg == null || avg.signum() <= 0)
        {
            return 0;
        }
        return BigDecimal.valueOf(nzi(c.getAvailableQty()))
                .divide(avg, 0, RoundingMode.DOWN).intValue();
    }

    // ==================================================================
    // 写：处置
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarningHandleResult handle(WarningHandleRequest request)
    {
        StockWarning warning = referenceGuard.requireExists(baseMapper, request.getWarningId(), "库存预警");

        // 契约只接受 2已处理 / 3已忽略 ——「1待处理」是扫描生成的初始态，不是可提交的结果
        Integer status = request.getHandleStatus();
        if (!Objects.equals(status, StockWarning.HANDLE_DONE)
                && !Objects.equals(status, StockWarning.HANDLE_IGNORED))
        {
            throw BizErrors.ruleViolated("处理状态只能填 2（已处理）或 3（已忽略），当前为 "
                    + status);
        }
        // 已处置的不允许再处置：否则「谁在什么时候决定忽略的」会被覆盖掉
        if (!Objects.equals(warning.getHandleStatus(), StockWarning.HANDLE_PENDING))
        {
            throw BizErrors.conflict("该预警已" + handleStatusName(warning.getHandleStatus())
                    + "，不能重复处置");
        }

        Boolean createStockIn = request.getCreateStockIn();
        StockIn draft = null;
        if (Boolean.TRUE.equals(createStockIn))
        {
            draft = createReplenishDraft(warning);
        }

        LocalDateTime handleTime = LocalDateTime.now();
        String operator = SecurityUtils.getUsername();

        StockWarning patch = new StockWarning();
        patch.setWarningId(warning.getWarningId());
        patch.setHandleStatus(status);
        patch.setHandleBy(operator);
        patch.setHandleTime(handleTime);
        patch.setHandleRemark(request.getHandleRemark());
        referenceGuard.requireAffected(baseMapper.updateById(patch), "库存预警");

        WarningHandleResult result = new WarningHandleResult();
        result.setWarningId(warning.getWarningId());
        result.setHandleStatus(status);
        result.setHandleBy(operator);
        result.setHandleTime(handleTime);
        if (draft != null)
        {
            result.setStockInId(draft.getInId());
            result.setStockInNo(draft.getInNo());
        }
        return result;
    }

    /**
     * 由补货类预警生成一张**采购入库单草稿**。
     *
     * <p>三处取舍：
     * <ul>
     *   <li><b>只允许补货类</b>（1补货 / 4零库存）：对滞销的书下采购单是反的，
     *       临期书更不该再进货 —— 那两类返回 422</li>
     *   <li><b>不填库位</b>：预警只知道「这本书缺货」，不知道收进哪个库位。
     *       落 {@code locationId=0}（未分配库位，`biz_stock` 允许的合法值），
     *       由 A-16 开单页补齐 —— 猜一个库位比留空更糟</li>
     *   <li><b>数量取 {@code suggestQty}，为 0 时按 1 册</b>：
     *       建议量为 0（安全库存算不出来，即近30天无销量）时仍开一张 1 册的草稿，
     *       让运营有个可改的起点，而不是点了「转采购单」却什么都没发生</li>
     * </ul>
     *
     * <p>成本单价取图书当前 {@code cost_price}（可能是 0，表示从未核算过）——
     * 草稿阶段的单价只是占位，实收时按实际进价覆盖并触发移动加权平均重算。
     */
    private StockIn createReplenishDraft(StockWarning warning)
    {
        boolean allowed = false;
        for (int t : REPLENISH_TYPES)
        {
            if (Objects.equals(warning.getWarningType(), t))
            {
                allowed = true;
                break;
            }
        }
        if (!allowed)
        {
            throw BizErrors.ruleViolated("「" + warningTypeName(warning.getWarningType())
                    + "」预警不支持转采购入库单 —— 只有补货与零库存（都属于「要卖的书不够了」）"
                    + "才需要下采购单；滞销的书应当折价促销或退货，临期书更不该再进货");
        }

        Book book = referenceGuard.requireExists(bookMapper, warning.getBookId(), "图书");
        int qty = nzi(warning.getSuggestQty());
        if (qty <= 0)
        {
            qty = 1;
        }

        StockInItem item = new StockInItem();
        item.setBookId(warning.getBookId());
        item.setLocationId(0L);
        item.setOrderQty(qty);
        item.setUnitCost(nz(book.getCostPrice()));

        StockIn draft = new StockIn();
        draft.setInType(StockIn.TYPE_PURCHASE);
        draft.setSupplierName(AUTO_SUPPLIER);
        draft.setRemark("由库存预警自动生成（预警ID=" + warning.getWarningId()
                + "，类型=" + warningTypeName(warning.getWarningType())
                + "，建议补货 " + nzi(warning.getSuggestQty()) + " 册；库位与实收数量待补齐）");

        List<StockInItem> items = new ArrayList<>(1);
        items.add(item);
        draft.setItems(items);

        stockInService.addStockIn(draft);
        return draft;
    }

    // ==================================================================
    // 内部：小工具
    // ==================================================================

    private void requireValidType(Integer warningType)
    {
        if (warningType == null)
        {
            return;
        }
        if (warningType < StockWarning.TYPE_REPLENISH || warningType > StockWarning.TYPE_OUT_OF_STOCK)
        {
            throw BizErrors.ruleViolated("预警类型只能填 1~4（1补货 2滞销 3临期 4零库存），当前为 "
                    + warningType);
        }
    }

    private String warningTypeName(Integer type)
    {
        if (type == null)
        {
            return "未知";
        }
        return switch (type)
        {
            case StockWarning.TYPE_REPLENISH -> "补货";
            case StockWarning.TYPE_SLOW_MOVING -> "滞销";
            case StockWarning.TYPE_EXPIRING -> "临期";
            case StockWarning.TYPE_OUT_OF_STOCK -> "零库存";
            default -> "未知";
        };
    }

    private String handleStatusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case StockWarning.HANDLE_DONE -> "处理";
            case StockWarning.HANDLE_IGNORED -> "忽略";
            default -> "处于待处理状态";
        };
    }

    /** 读 int 型参数；缺失或写坏时回落默认值并继续（运营改坏参数不该让预警整体不可用） */
    private int cfgInt(String key, int fallback)
    {
        String raw = configService.selectConfigByKey(key);
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private BigDecimal cfgDecimal(String key, BigDecimal fallback)
    {
        String raw = configService.selectConfigByKey(key);
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        try
        {
            return new BigDecimal(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return fallback;
        }
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }
}
