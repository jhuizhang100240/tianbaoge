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

import com.tbgbook.bookstore.ai.domain.AiPromptTemplate;
import com.tbgbook.bookstore.ai.domain.GrayAdjustRequest;
import com.tbgbook.bookstore.ai.service.IAiPromptTemplateService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * AI 提示词模板 Controller（`/ai/prompt`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/prompt")
public class AiPromptController extends BaseController
{
    private final IAiPromptTemplateService aiPromptTemplateService;

    @PreAuthorize("@ss.hasPermi('ai:prompt:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiPromptTemplate aiPromptTemplate)
    {
        startPage();
        return getDataTable(aiPromptTemplateService.selectAiPromptTemplateList(aiPromptTemplate));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:list')")
    @GetMapping("/{templateId}")
    public AjaxResult getInfo(@PathVariable("templateId") Long templateId)
    {
        return success(aiPromptTemplateService.getById(templateId));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:add')")
    @Log(title = "AI 提示词模板", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiPromptTemplate aiPromptTemplate)
    {
        return toAjax(aiPromptTemplateService.addTemplate(aiPromptTemplate));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:edit')")
    @Log(title = "AI 提示词模板", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiPromptTemplate aiPromptTemplate)
    {
        return toAjax(aiPromptTemplateService.updateTemplate(aiPromptTemplate));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:remove')")
    @Log(title = "AI 提示词模板", businessType = BusinessType.DELETE)
    @DeleteMapping("/{templateIds}")
    public AjaxResult remove(@PathVariable("templateIds") Long[] templateIds)
    {
        return toAjax(aiPromptTemplateService.removeTemplates(templateIds));
    }

    /**
     * 调整灰度方式/比例/会员群（不改变版本内容）。
     */
    @PreAuthorize("@ss.hasPermi('ai:prompt')")
    @Log(title = "AI 提示词模板", businessType = BusinessType.UPDATE)
    @PostMapping("/{templateId}/gray")
    public AjaxResult gray(@PathVariable("templateId") Long templateId, @RequestBody GrayAdjustRequest request)
    {
        return toAjax(aiPromptTemplateService.adjustGray(templateId, request));
    }
}
