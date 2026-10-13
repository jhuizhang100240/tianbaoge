package com.tbgbook.bookstore.ai.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

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

import com.tbgbook.bookstore.ai.domain.AiQuotaConfig;
import com.tbgbook.bookstore.ai.service.IAiCallLogService;
import com.tbgbook.bookstore.ai.service.IAiQuotaConfigService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * AI 配额 Controller（`/ai/quota` + 成本监控）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/quota")
public class AiQuotaController extends BaseController
{
    private final IAiQuotaConfigService aiQuotaConfigService;

    private final IAiCallLogService aiCallLogService;

    @PreAuthorize("@ss.hasPermi('ai:quota:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiQuotaConfig aiQuotaConfig)
    {
        startPage();
        return getDataTable(aiQuotaConfigService.selectAiQuotaConfigList(aiQuotaConfig));
    }

    @PreAuthorize("@ss.hasPermi('ai:quota:list')")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@PathVariable("configId") Long configId)
    {
        return success(aiQuotaConfigService.getById(configId));
    }

    @PreAuthorize("@ss.hasPermi('ai:quota:add')")
    @Log(title = "AI 配额配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiQuotaConfig aiQuotaConfig)
    {
        return toAjax(aiQuotaConfigService.addQuotaConfig(aiQuotaConfig));
    }

    @PreAuthorize("@ss.hasPermi('ai:quota:edit')")
    @Log(title = "AI 配额配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiQuotaConfig aiQuotaConfig)
    {
        return toAjax(aiQuotaConfigService.updateQuotaConfig(aiQuotaConfig));
    }

    @PreAuthorize("@ss.hasPermi('ai:quota:remove')")
    @Log(title = "AI 配额配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable("configIds") Long[] configIds)
    {
        return toAjax(aiQuotaConfigService.removeQuotaConfigs(configIds));
    }

    /**
     * 成本监控（A-76；费用字段仅店长可见，靠权限串控制）。
     */
    @PreAuthorize("@ss.hasPermi('ai:quota')")
    @GetMapping("/cost-summary")
    public AjaxResult costSummary(@RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "granularity", required = false, defaultValue = "day") String granularity,
            @RequestParam(value = "scene", required = false) Integer scene)
    {
        try
        {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (end.isBefore(start))
            {
                throw BizErrors.badRequest("结束日期不能早于开始日期");
            }
            return success(aiCallLogService.costSummary(start, end, granularity, scene));
        }
        catch (DateTimeParseException e)
        {
            throw BizErrors.badRequest("日期格式应为 yyyy-MM-dd");
        }
    }
}
