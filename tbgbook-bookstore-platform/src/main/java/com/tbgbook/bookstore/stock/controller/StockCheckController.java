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
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.StockCheck;
import com.tbgbook.bookstore.stock.domain.StockCheckFinishRequest;
import com.tbgbook.bookstore.stock.domain.StockCheckGenerateRequest;
import com.tbgbook.bookstore.stock.domain.StockCheckItemsRequest;
import com.tbgbook.bookstore.stock.domain.StockCheckRecheckRequest;
import com.tbgbook.bookstore.stock.service.IStockCheckService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 库存盘点单 Controller（页面 A-19 创建 / A-20 列表 / A-21 录入 / A-22 差异报告）。
 *
 * <p><b>四个动作端点</b>：{@code /generate}（按范围生成并快照账面数）、
 * {@code /{checkId}/items}（录入实盘数，增量）、{@code /{checkId}/finish}（应用差异）、
 * {@code /{checkId}/diff-report}（差异报告）、{@code /{checkId}/recheck}（二次盘点）。
 *
 * <p>⚠️ <b>字面量路径与 {@code /{checkId}} 同段位</b>：明细子资源挂在
 * {@code /inventory/stockcheck/item}，与 {@code GET /inventory/stockcheck/{checkId}} 竞争。
 * Spring 的字面量模式优先于路径变量，故 {@code /item/list} 不会被解析成
 * {@code checkId="item"}（那还会因 {@code Long} 转换失败报 400）。
 * 与 {@code StockController} 的 {@code /scan}、{@code StockInController} 的 {@code /item}
 * 是同一个坑 —— 处理方式一致：两个控制器并列声明，让优先级只有单一出处。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/stockcheck")
public class StockCheckController extends BaseController
{
    private final IStockCheckService stockCheckService;

    /**
     * 查询盘点单列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockCheck stockCheck)
    {
        startPage();
        List<StockCheck> list = stockCheckService.selectStockCheckList(stockCheck);
        return getDataTable(list);
    }

    /**
     * 获取盘点单详情（**含明细**）。
     *
     * <p>⚠️ 盲盘（{@code showBookQty=0}）时明细的账面数相关字段被置 null ——
     * 前端据此隐藏「账面数」列。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:list')")
    @GetMapping("/{checkId}")
    public AjaxResult getInfo(@PathVariable("checkId") Long checkId)
    {
        return success(stockCheckService.selectStockCheckById(checkId));
    }

    /**
     * 按范围生成盘点单并**快照账面数**（A-19 的主入口）。
     *
     * <p>这是日常开单应该走的那条路 —— 标准 CRUD 的 POST 要求客户端自己给明细，
     * 而账面数必须由服务端快照，手工开单容易给错。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:generate')")
    @Log(title = "库存盘点单", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@Valid @RequestBody StockCheckGenerateRequest request)
    {
        return success(stockCheckService.generate(request));
    }

    /**
     * 新增盘点单（客户端给明细）。
     *
     * <p>账面数仍由服务端按当前库存快照，不接受传入。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:add')")
    @Log(title = "库存盘点单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody StockCheck stockCheck)
    {
        return toAjax(stockCheckService.addStockCheck(stockCheck));
    }

    /**
     * 修改盘点单（**仅进行中**）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:edit')")
    @Log(title = "库存盘点单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody StockCheck stockCheck)
    {
        return toAjax(stockCheckService.updateStockCheck(stockCheck));
    }

    /**
     * 删除盘点单（**仅进行中或已作废**，主从级联）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:remove')")
    @Log(title = "库存盘点单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{checkIds}")
    public AjaxResult remove(@PathVariable Long[] checkIds)
    {
        return toAjax(stockCheckService.removeStockChecks(checkIds));
    }

    /**
     * 批量录入实盘数（A-21，**增量提交**，按 {@code itemId} 覆盖）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:items')")
    @Log(title = "库存盘点录入", businessType = BusinessType.UPDATE)
    @PostMapping("/{checkId}/items")
    public AjaxResult entryItems(@PathVariable("checkId") Long checkId,
            @Valid @RequestBody StockCheckItemsRequest request)
    {
        return success(stockCheckService.entryItems(checkId, request));
    }

    /**
     * 结束盘点并**应用差异**（差异率超阈值时进「待复核」且不应用）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:finish')")
    @Log(title = "库存盘点单", businessType = BusinessType.UPDATE)
    @PostMapping("/{checkId}/finish")
    public AjaxResult finish(@PathVariable("checkId") Long checkId,
            @RequestBody(required = false) StockCheckFinishRequest request)
    {
        return success(stockCheckService.finish(checkId,
                request == null ? null : request.getForceFinish()));
    }

    /**
     * 盘点差异报告（A-22）。两类差异分开返回，盘点期差异单独一组。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:list')")
    @GetMapping("/{checkId}/diff-report")
    public AjaxResult diffReport(@PathVariable("checkId") Long checkId)
    {
        return success(stockCheckService.diffReport(checkId));
    }

    /**
     * 对差异品种发起二次盘点（生成只含差异行的新单）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockcheck:recheck')")
    @Log(title = "库存盘点单", businessType = BusinessType.INSERT)
    @PostMapping("/{checkId}/recheck")
    public AjaxResult recheck(@PathVariable("checkId") Long checkId,
            @RequestBody(required = false) StockCheckRecheckRequest request)
    {
        return success(stockCheckService.recheck(checkId,
                request == null ? null : request.getCheckType()));
    }
}
