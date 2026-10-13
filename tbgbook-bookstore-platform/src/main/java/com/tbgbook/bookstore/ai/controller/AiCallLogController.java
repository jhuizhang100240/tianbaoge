package com.tbgbook.bookstore.ai.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.ai.domain.AiCallLog;
import com.tbgbook.bookstore.ai.service.IAiCallLogService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * AI 调用明细 Controller（只读，`/ai/call-log`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/call-log")
public class AiCallLogController extends BaseController
{
    private final IAiCallLogService aiCallLogService;

    @PreAuthorize("@ss.hasPermi('ai:quota:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCallLog aiCallLog)
    {
        startPage();
        return getDataTable(aiCallLogService.selectAiCallLogList(aiCallLog));
    }

    @PreAuthorize("@ss.hasPermi('ai:quota:list')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(aiCallLogService.getById(logId));
    }
}
