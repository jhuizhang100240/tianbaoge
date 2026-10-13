package com.tbgbook.bookstore.ai.controller;

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

import com.tbgbook.bookstore.ai.domain.AiAnnotation;
import com.tbgbook.bookstore.ai.service.IAiQaService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * AI 问答质量标注 Controller（`/ai/qa`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/qa")
public class AiQaController extends BaseController
{
    private final IAiQaService aiQaService;

    @PreAuthorize("@ss.hasPermi('ai:qa:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiAnnotation aiAnnotation)
    {
        startPage();
        return getDataTable(aiQaService.selectAiAnnotationList(aiAnnotation));
    }

    @PreAuthorize("@ss.hasPermi('ai:qa:list')")
    @GetMapping("/{annotationId}")
    public AjaxResult getInfo(@PathVariable("annotationId") Long annotationId)
    {
        return success(aiQaService.getById(annotationId));
    }

    @PreAuthorize("@ss.hasPermi('ai:qa:add')")
    @Log(title = "AI 问答标注", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiAnnotation aiAnnotation)
    {
        return toAjax(aiQaService.addAnnotation(aiAnnotation));
    }

    @PreAuthorize("@ss.hasPermi('ai:qa:edit')")
    @Log(title = "AI 问答标注", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiAnnotation aiAnnotation)
    {
        return toAjax(aiQaService.updateAnnotation(aiAnnotation));
    }

    @PreAuthorize("@ss.hasPermi('ai:qa:remove')")
    @Log(title = "AI 问答标注", businessType = BusinessType.DELETE)
    @DeleteMapping("/{annotationIds}")
    public AjaxResult remove(@PathVariable("annotationIds") Long[] annotationIds)
    {
        return toAjax(aiQaService.removeAnnotations(annotationIds));
    }

    /**
     * 标注问答质量（A-73 一键标注入口；category=1 时可带 relatedDocId）。
     */
    @PreAuthorize("@ss.hasPermi('ai:qa')")
    @Log(title = "AI 问答标注", businessType = BusinessType.UPDATE)
    @PostMapping("/annotate")
    public AjaxResult annotate(@RequestBody AiAnnotation request)
    {
        return success(aiQaService.annotate(request));
    }
}
