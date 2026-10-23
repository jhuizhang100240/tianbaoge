package com.tbgbook.bookstore.ai.controller;

import java.util.List;

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
import com.tbgbook.common.utils.SecurityUtils;

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
        List<AiCallLog> list = aiCallLogService.selectAiCallLogList(aiCallLog);
        if (!costVisible())
        {
            list.forEach(this::maskCost);
        }
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('ai:quota:list')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        AiCallLog log = aiCallLogService.getById(logId);
        if (!costVisible())
        {
            maskCost(log);
        }
        return success(log);
    }

    /**
     * 费用与供应商错误明细仅店长（或 admin）可见（FR-4.7.10；NFR-S2 接口级保护）。
     */
    private boolean costVisible()
    {
        return SecurityUtils.hasRole("store_manager");
    }

    private void maskCost(AiCallLog log)
    {
        if (log != null)
        {
            log.setCost(null);
            log.setErrorMsg(null);
        }
    }
}
