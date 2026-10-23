package com.tbgbook.bookstore.stock.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.StockFlow;
import com.tbgbook.bookstore.stock.service.IStockFlowService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 库存流水 Controller（页面 A-24 库存流水查询）。
 *
 * <p><b>只读资源：本类只有 2 个端点，没有 POST / PUT / DELETE。</b>
 * 流水表只增不改不删（根 CLAUDE.md §6.2），写入由各单据的服务端逻辑在同事务内完成。
 * 反例：把代码生成器产出的 CRUD 直接套上来 —— {@code removeByIds} 会退化成物理删除、
 * {@code updateById} 会写不存在的列（表里没有 {@code update_*}）。
 *
 * @author tbgbook
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/flow")
public class StockFlowController extends BaseController
{
    private final IStockFlowService stockFlowService;

    /**
     * 查询库存流水列表。
     *
     * <p>支持按图书 / ISBN / 库位 / 库存类型 / 业务类型 / 单据号 / 时间区间追溯。
     */
    @PreAuthorize("@ss.hasPermi('inventory:flow:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockFlow flow)
    {
        startPage();
        List<StockFlow> list = stockFlowService.selectStockFlowList(flow);
        return getDataTable(list);
    }

    /**
     * 获取库存流水详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:flow:list')")
    @GetMapping("/{flowId}")
    public AjaxResult getInfo(@PathVariable("flowId") Long flowId)
    {
        return success(stockFlowService.selectStockFlowById(flowId));
    }
}
