package com.tbgbook.bookstore.borrow.controller;

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

import com.tbgbook.bookstore.borrow.domain.BorrowRule;
import com.tbgbook.bookstore.borrow.service.IBorrowRuleService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 借阅全局规则 Controller（页面 A-57，FR-4.5.1）。
 *
 * <p>单行配置：{@code PUT} 按 storeId 更新已有行而非新增；删除恒 422。
 * 等级相关额度不在此资源，见 {@code /borrow/rule/level-quota}。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/borrow/rule")
public class BorrowRuleController extends BaseController
{
    private final IBorrowRuleService borrowRuleService;

    /**
     * 查询借阅全局规则列表（可按状态与创建时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:rule:list')")
    @GetMapping("/list")
    public TableDataInfo list(BorrowRule rule)
    {
        startPage();
        List<BorrowRule> list = borrowRuleService.selectBorrowRuleList(rule);
        return getDataTable(list);
    }

    /**
     * 等级借阅额度对照（等级额度 + 全局规则 + 相关 sys_config 键值）。
     *
     * @param levelId 限定等级（不传 = 全部等级）
     */
    @PreAuthorize("@ss.hasPermi('borrow:rule:list')")
    @GetMapping("/level-quota")
    public AjaxResult levelQuota(@RequestParam(value = "levelId", required = false) Long levelId)
    {
        return success(borrowRuleService.levelQuota(levelId));
    }

    /**
     * 获取借阅全局规则详情。
     */
    @PreAuthorize("@ss.hasPermi('borrow:rule:list')")
    @GetMapping("/{ruleId}")
    public AjaxResult getInfo(@PathVariable("ruleId") Long ruleId)
    {
        return success(borrowRuleService.selectBorrowRuleById(ruleId));
    }

    /**
     * 新增借阅全局规则（一门店仅一条，重复 409）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:rule:add')")
    @Log(title = "借阅规则", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BorrowRule rule)
    {
        return toAjax(borrowRuleService.addBorrowRule(rule));
    }

    /**
     * 修改借阅全局规则（按 storeId 更新已有行而非新增）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:rule:edit')")
    @Log(title = "借阅规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BorrowRule rule)
    {
        return toAjax(borrowRuleService.updateBorrowRule(rule));
    }

    /**
     * 删除借阅全局规则 —— 恒 422（必须保留一行，改配置走 PUT）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:rule:remove')")
    @Log(title = "借阅规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds)
    {
        return toAjax(borrowRuleService.removeBorrowRules(ruleIds));
    }
}
