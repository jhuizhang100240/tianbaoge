package com.tbgbook.bookstore.stock.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.CostChangeLog;
import com.tbgbook.bookstore.stock.service.ICostChangeLogService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 成本变更流水 Controller（页面 A-18 成本变更流水）。
 *
 * <p><b>只读资源：只有 2 个端点</b>（流水表只增不删，写入由移动加权平均重算时完成）。
 *
 * <p>⚠️ <b>权限串是 {@code inventory:cost:view}，没有 {@code :list} 形态</b> ——
 * 这是契约明写的（接口文档把本资源的权限串直接定成按钮级的成本权限），
 * 与其它资源一律 {@code {前缀}:list} 的惯例不同。原因是本资源**整个都是成本金额**，
 * 按 FR-4.6 规则 5 只对店长及以上开放，不需要再分「能看列表」与「能看成本」两级。
 *
 * @author tbgbook
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/cost-log")
public class CostChangeLogController extends BaseController
{
    private final ICostChangeLogService costChangeLogService;

    /**
     * 查询成本变更流水列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:cost:view')")
    @GetMapping("/list")
    public TableDataInfo list(CostChangeLog log)
    {
        startPage();
        List<CostChangeLog> list = costChangeLogService.selectCostChangeLogList(log);
        return getDataTable(list);
    }

    /**
     * 获取成本变更流水详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:cost:view')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(costChangeLogService.selectCostChangeLogById(logId));
    }
}
