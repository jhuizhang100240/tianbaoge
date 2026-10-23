package com.tbgbook.bookstore.ai.controller;

import java.util.Map;

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

import com.tbgbook.bookstore.ai.domain.AiSummary;
import com.tbgbook.bookstore.ai.service.IAiSummaryService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 图书摘要与书评 Controller（`/ai/summary`；CRUD 权限串为 ai:summary:audit，与种子菜单一致）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/summary")
public class AiSummaryController extends BaseController
{
    private final IAiSummaryService aiSummaryService;

    @PreAuthorize("@ss.hasPermi('ai:summary:audit')")
    @GetMapping("/list")
    public TableDataInfo list(AiSummary aiSummary)
    {
        startPage();
        return getDataTable(aiSummaryService.selectAiSummaryList(aiSummary));
    }

    @PreAuthorize("@ss.hasPermi('ai:summary:audit')")
    @GetMapping("/{summaryId}")
    public AjaxResult getInfo(@PathVariable("summaryId") Long summaryId)
    {
        return success(aiSummaryService.getById(summaryId));
    }

    @PreAuthorize("@ss.hasPermi('ai:summary:audit')")
    @Log(title = "AI 图书摘要", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSummary aiSummary)
    {
        return toAjax(aiSummaryService.addAiSummary(aiSummary));
    }

    @PreAuthorize("@ss.hasPermi('ai:summary:audit')")
    @Log(title = "AI 图书摘要", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSummary aiSummary)
    {
        return toAjax(aiSummaryService.updateAiSummary(aiSummary));
    }

    @PreAuthorize("@ss.hasPermi('ai:summary:audit')")
    @Log(title = "AI 图书摘要", businessType = BusinessType.DELETE)
    @DeleteMapping("/{summaryIds}")
    public AjaxResult remove(@PathVariable("summaryIds") Long[] summaryIds)
    {
        return toAjax(aiSummaryService.removeAiSummaries(summaryIds));
    }

    /**
     * 生成图书摘要/书评（结果一律待审核；AI 不可用 503 且不落行）。
     */
    @PreAuthorize("@ss.hasPermi('ai:summary:generate')")
    @Log(title = "AI 图书摘要", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody Map<String, Object> request)
    {
        Long[] bookIds = toBookIds(request.get("bookIds"));
        Integer genType = request.get("genType") == null ? null
                : Integer.valueOf(String.valueOf(request.get("genType")));
        String style = request.get("style") == null ? null : String.valueOf(request.get("style"));
        return success(aiSummaryService.generate(bookIds, genType, style));
    }

    /**
     * 审核（通过即发布并同步图书主表；驳回必填原因）。
     */
    @PreAuthorize("@ss.hasPermi('ai:summary:audit')")
    @Log(title = "AI 图书摘要", businessType = BusinessType.UPDATE)
    @PostMapping("/{summaryId}/audit")
    public AjaxResult audit(@PathVariable("summaryId") Long summaryId,
            @RequestBody Map<String, Object> request)
    {
        Integer auditResult = request.get("auditResult") == null ? null
                : Integer.valueOf(String.valueOf(request.get("auditResult")));
        String rejectReason = request.get("rejectReason") == null ? null : String.valueOf(request.get("rejectReason"));
        String content = request.get("content") == null ? null : String.valueOf(request.get("content"));
        return success(aiSummaryService.audit(summaryId, auditResult, rejectReason, content));
    }

    @SuppressWarnings("unchecked")
    private Long[] toBookIds(Object value)
    {
        if (!(value instanceof Iterable))
        {
            return new Long[0];
        }
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (Object item : (Iterable<Object>) value)
        {
            ids.add(Long.valueOf(String.valueOf(item)));
        }
        return ids.toArray(new Long[0]);
    }
}
