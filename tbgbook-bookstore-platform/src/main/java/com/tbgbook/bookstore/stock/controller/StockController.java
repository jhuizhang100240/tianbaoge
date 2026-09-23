package com.tbgbook.bookstore.stock.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.ScanResult;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.service.IInventoryOverviewService;
import com.tbgbook.bookstore.stock.service.IStockService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 库存 Controller（页面 A-14 库存总览的表格区）。
 *
 * <p>⚠️ <b>{@code /scan} 与 {@code /{stockId}} 同段位</b>：Spring 的字面量模式优先于路径变量，
 * 所以 {@code GET /inventory/stock/scan} 命中本类的 {@code scan} 方法而不是
 * {@code getInfo(stockId="scan")}。两个映射**放在同一个控制器里**正是为了让这条优先级
 * 有单一出处 —— 拆到两个控制器会让「谁赢」取决于注册顺序，不可依赖。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：① 删掉 export；② 详情权限串 {@code :query} → {@code :list}；
 * ③ 删除走带删除保护的 Service 方法；④ 补校验；⑤ 补 {@code /scan} 动作端点。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/stock")
public class StockController extends BaseController
{
    private final IStockService stockService;

    private final IInventoryOverviewService overviewService;

    /**
     * 查询库存列表。
     *
     * <p>响应含「书名 / ISBN / 分类 / 库位」四个 JOIN 出来的字段，供 A-14 表格直接渲染
     * （否则前端只能按行回查，N+1）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:list')")
    @GetMapping("/list")
    public TableDataInfo list(Stock stock)
    {
        startPage();
        List<Stock> list = stockService.selectStockList(stock);
        return getDataTable(list);
    }

    /**
     * 扫码查书：扫 ISBN 或图书编号即查库存与库位，逐库位展开。
     *
     * <p>⚠️ 必须声明在 {@code /{stockId}} 之前语义才清晰（Spring 按模式特异性选，不按声明顺序）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:list')")
    @GetMapping("/scan")
    public AjaxResult scan(@RequestParam("code") String code)
    {
        return success(overviewService.scan(code));
    }

    /**
     * 获取库存详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:list')")
    @GetMapping("/{stockId}")
    public AjaxResult getInfo(@PathVariable("stockId") Long stockId)
    {
        return success(stockService.selectStockById(stockId));
    }

    /**
     * 新增库存行（为某个「图书 + 库位」建档库存位）。
     *
     * <p>四类数量一律置 0 —— 数量只能由入库/出库/盘点等单据改动（FR-4.2 规则 1）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:add')")
    @Log(title = "库存", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Stock stock)
    {
        return toAjax(stockService.addStock(stock));
    }

    /**
     * 修改库存行 —— 只允许改库位，数量与版本号不接受传入。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:edit')")
    @Log(title = "库存", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Stock stock)
    {
        return toAjax(stockService.updateStock(stock));
    }

    /**
     * 删除库存行。
     *
     * <p>该「图书 + 库位」上已有库存流水时返回 422（流水只增不删，删行会让历史失去主语）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:overview:remove')")
    @Log(title = "库存", businessType = BusinessType.DELETE)
    @DeleteMapping("/{stockIds}")
    public AjaxResult remove(@PathVariable Long[] stockIds)
    {
        return toAjax(stockService.removeStocks(stockIds));
    }
}
