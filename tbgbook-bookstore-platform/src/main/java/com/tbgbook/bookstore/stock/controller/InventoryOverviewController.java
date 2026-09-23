package com.tbgbook.bookstore.stock.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.CategoryStock;
import com.tbgbook.bookstore.stock.service.IInventoryOverviewService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 库存总览 Controller（页面 A-14 顶部指标卡 + 饼图）。
 *
 * <p>本类只放 {@code /inventory/overview/*} 两个动作端点；扫码查书（{@code /inventory/stock/scan}）
 * 放在 {@code StockController} —— 它与 {@code /inventory/stock/{stockId}} 同段位，
 * 放同一个控制器里能让「字面量模式优先于路径变量」这条规则有单一出处。
 *
 * @author tbgbook
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/overview")
public class InventoryOverviewController extends BaseController
{
    private final IInventoryOverviewService overviewService;

    /**
     * 库存总览统计。
     *
     * <p>库存总金额是成本口径：无 {@code inventory:cost:view} 时该字段为 {@code null}
     * 而非报错（FR-4.6 规则 5），前端据此隐藏指标卡。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:list')")
    @GetMapping("/summary")
    public AjaxResult summary(@RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "locationId", required = false) Long locationId)
    {
        return success(overviewService.summary(categoryId, locationId));
    }

    /**
     * 分类库存分布（饼图）。
     *
     * <p>占比按金额算；金额不可见时退化为按实物总量算，保证饼图仍能画出来。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:list')")
    @GetMapping("/by-category")
    public AjaxResult byCategory(@RequestParam(value = "locationId", required = false) Long locationId)
    {
        List<CategoryStock> list = overviewService.byCategory(locationId);
        return success(list);
    }
}
