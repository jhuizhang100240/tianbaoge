package com.tbgbook.bookstore.stock.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.StockSnapshot;
import com.tbgbook.bookstore.stock.service.IStockSnapshotService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 库存快照 Controller（页面 A-27 库存快照）。
 *
 * <p><b>全只读</b>：快照由定时任务「库存快照生成」（每日 02:00）产出，
 * **没有 POST / PUT / DELETE** —— 若把生成器产出的 CRUD 直接套上来，
 * 就等于允许任何人事后改写「某日的库存账面」，历史回溯立刻失去依据。
 *
 * <p>⚠️ <b>{@code /compare} 与字面量路径</b>：{@code GET /{snapshotId}/compare} 与
 * {@code GET /{snapshotId}} 不在同一段位（前者多一段），无字面量/变量的优先级问题。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/snapshot")
public class StockSnapshotController extends BaseController
{
    private final IStockSnapshotService stockSnapshotService;

    /**
     * 查询库存快照列表（可按快照日期区间筛选）。
     *
     * <p>成本类金额 `totalAmount` 无 `inventory:cost:view` 时返回 null（FR-4.6 规则 5）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:snapshot:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockSnapshot snapshot)
    {
        startPage();
        List<StockSnapshot> list = stockSnapshotService.selectStockSnapshotList(snapshot);
        return getDataTable(list);
    }

    /**
     * 获取库存快照详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:snapshot:list')")
    @GetMapping("/{snapshotId}")
    public AjaxResult getInfo(@PathVariable("snapshotId") Long snapshotId)
    {
        return success(stockSnapshotService.selectStockSnapshotById(snapshotId));
    }

    /**
     * 库存快照对比 —— 与另一份快照逐图书比对数量与金额增减（FR-4.2.11）。
     *
     * <p>对比对象二选一：{@code compareSnapshotId} 优先，否则按 {@code compareDate}。
     * 两个都不给返回 422；给了日期而那天没有快照返回 404。
     */
    @PreAuthorize("@ss.hasPermi('inventory:snapshot:list')")
    @GetMapping("/{snapshotId}/compare")
    public AjaxResult compare(
            @PathVariable("snapshotId") Long snapshotId,
            @RequestParam(value = "compareSnapshotId", required = false) Long compareSnapshotId,
            @RequestParam(value = "compareDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate compareDate)
    {
        return success(stockSnapshotService.compare(snapshotId, compareSnapshotId, compareDate));
    }
}
