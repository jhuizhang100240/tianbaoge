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

import com.tbgbook.bookstore.stock.domain.StockIn;
import com.tbgbook.bookstore.stock.domain.StockInConfirmRequest;
import com.tbgbook.bookstore.stock.domain.StockInVoidRequest;
import com.tbgbook.bookstore.stock.service.IStockInService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 采购入库单 Controller（页面 A-16 采购入库开单 / A-15 入库单列表）。
 *
 * <p><b>两个动作端点是本单据的全部意义所在</b>：
 * {@code POST /{inId}/confirm} 是库存真正变动的唯一入口（FR-4.2 规则 1），
 * {@code POST /{inId}/void} 反向冲减。
 *
 * <p>⚠️ <b>字面量路径与 {@code /{inId}} 同段位</b>：明细子资源挂在
 * {@code /inventory/stockin/item}，与 {@code GET /inventory/stockin/{inId}} 竞争同一段位。
 * Spring 的字面量模式优先于路径变量，故 {@code /item/list} 不会被解析成
 * {@code inId="item"}（那还会因 {@code Long} 转换失败报 400）。
 * 与 {@code StockController} 的 {@code /scan} 是同一个坑 —— 处理方式一样：
 * **让优先级只有单一出处**，故两个控制器都放在 {@code stock/controller} 下并列声明。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/stockin")
public class StockInController extends BaseController
{
    private final IStockInService stockInService;

    /**
     * 查询采购入库单列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockIn stockIn)
    {
        startPage();
        List<StockIn> list = stockInService.selectStockInList(stockIn);
        return getDataTable(list);
    }

    /**
     * 获取采购入库单详情（**含明细**）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:list')")
    @GetMapping("/{inId}")
    public AjaxResult getInfo(@PathVariable("inId") Long inId)
    {
        return success(stockInService.selectStockInById(inId));
    }

    /**
     * 新增采购入库单（草稿，**不动库存**）。
     *
     * <p>请求体是「单据头 + {@code items} 明细数组」，两者同事务落库。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:add')")
    @Log(title = "采购入库单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody StockIn stockIn)
    {
        return toAjax(stockInService.addStockIn(stockIn));
    }

    /**
     * 修改采购入库单（**仅草稿**；明细整体替换）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:edit')")
    @Log(title = "采购入库单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody StockIn stockIn)
    {
        return toAjax(stockInService.updateStockIn(stockIn));
    }

    /**
     * 删除采购入库单（**仅草稿**，主从级联）。
     *
     * <p>已入库的单据要用 {@code /void} 作废（会反向冲减库存），不能直接删。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:remove')")
    @Log(title = "采购入库单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{inIds}")
    public AjaxResult remove(@PathVariable Long[] inIds)
    {
        return toAjax(stockInService.removeStockIns(inIds));
    }

    /**
     * 确认入库 —— <b>库存真正变动的唯一动作</b>。
     *
     * <p>请求体可省略（{@code inTime} 是唯一字段且选填）。
     * 重复提交返回 <b>409</b>，不会静默把库存加两遍。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:confirm')")
    @Log(title = "采购入库单", businessType = BusinessType.UPDATE)
    @PostMapping("/{inId}/confirm")
    public AjaxResult confirm(@PathVariable("inId") Long inId,
            @RequestBody(required = false) StockInConfirmRequest request)
    {
        return success(stockInService.confirm(inId,
                request == null ? null : request.getInTime()));
    }

    /**
     * 作废入库单。已入库的单据会**反向冲减库存**并写红字流水。
     *
     * <p>{@code reason} 必填（契约如此）。本表没有作废原因列，该值并入 {@code remark}，
     * 见 {@code StockInServiceImpl#appendReason}。
     */
    @PreAuthorize("@ss.hasPermi('inventory:stockin:void')")
    @Log(title = "采购入库单", businessType = BusinessType.UPDATE)
    @PostMapping("/{inId}/void")
    public AjaxResult voidStockIn(@PathVariable("inId") Long inId,
            @Valid @RequestBody StockInVoidRequest request)
    {
        return toAjax(stockInService.voidStockIn(inId, request.getReason()));
    }
}
