package com.tbgbook.bookstore.stock.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.StockSnapshotItem;
import com.tbgbook.bookstore.stock.service.IStockSnapshotItemService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 库存快照明细 Controller（A-27 快照详情）。
 *
 * <p><b>只有两个 GET</b>：明细是历史回溯的原始凭证，与主表一样不提供写入口。
 * 原契约把本资源误标为可增删改（`kind=child` → 5 个端点），
 * 与它自己的「只读」业务约束及父资源的 `readonly` 自相矛盾，已修正（2026-09-23）。
 *
 * <p>权限串全部复用 {@code inventory:snapshot:list}（契约如此），不为明细另设前缀。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/snapshot/item")
public class StockSnapshotItemController extends BaseController
{
    private final IStockSnapshotItemService stockSnapshotItemService;

    /**
     * 查询库存快照明细列表（逐行 = 某图书在某库位）。
     *
     * <p>成本类字段 `costPrice` / `amount` 无 `inventory:cost:view` 时返回 null。
     */
    @PreAuthorize("@ss.hasPermi('inventory:snapshot:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockSnapshotItem item)
    {
        startPage();
        List<StockSnapshotItem> list = stockSnapshotItemService.selectStockSnapshotItemList(item);
        return getDataTable(list);
    }

    /**
     * 获取库存快照明细详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:snapshot:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(stockSnapshotItemService.selectStockSnapshotItemById(itemId));
    }
}
