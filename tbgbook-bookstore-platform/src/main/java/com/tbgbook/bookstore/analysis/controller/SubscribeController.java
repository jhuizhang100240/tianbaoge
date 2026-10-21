package com.tbgbook.bookstore.analysis.controller;

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

import com.tbgbook.bookstore.analysis.domain.ReportPushLog;
import com.tbgbook.bookstore.analysis.domain.ReportSubscribe;
import com.tbgbook.bookstore.analysis.service.IReportSubscribeService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 报表订阅 Controller（A-69）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/analysis/subscribe")
public class SubscribeController extends BaseController
{
    private final IReportSubscribeService subscribeService;

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:list')")
    @GetMapping("/list")
    public TableDataInfo list(ReportSubscribe query)
    {
        startPage();
        return getDataTable(subscribeService.selectSubscribeList(query));
    }

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:list')")
    @GetMapping("/{subscribeId}")
    public AjaxResult detail(@PathVariable("subscribeId") Long subscribeId)
    {
        return success(subscribeService.getSubscribe(subscribeId));
    }

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:add')")
    @Log(title = "报表订阅", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ReportSubscribe subscribe)
    {
        return toAjax(subscribeService.addSubscribe(subscribe));
    }

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:edit')")
    @Log(title = "报表订阅", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ReportSubscribe subscribe)
    {
        return toAjax(subscribeService.updateSubscribe(subscribe));
    }

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:remove')")
    @Log(title = "报表订阅", businessType = BusinessType.DELETE)
    @DeleteMapping("/{subscribeIds}")
    public AjaxResult remove(@PathVariable("subscribeIds") Long[] subscribeIds)
    {
        return toAjax(subscribeService.removeSubscribes(subscribeIds));
    }

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:testPush')")
    @Log(title = "报表试推送", businessType = BusinessType.OTHER)
    @PostMapping("/{subscribeId}/test-push")
    public AjaxResult testPush(@PathVariable("subscribeId") Long subscribeId)
    {
        return success(subscribeService.testPush(subscribeId));
    }

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:list')")
    @GetMapping("/push-log/list")
    public TableDataInfo pushLogList(ReportPushLog query)
    {
        startPage();
        return getDataTable(subscribeService.selectPushLogList(query));
    }

    @PreAuthorize("@ss.hasPermi('analysis:subscribe:list')")
    @GetMapping("/push-log/{logId}")
    public AjaxResult pushLogDetail(@PathVariable("logId") Long logId)
    {
        return success(subscribeService.getPushLog(logId));
    }
}
