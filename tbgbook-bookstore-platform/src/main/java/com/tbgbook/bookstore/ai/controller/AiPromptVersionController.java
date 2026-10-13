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

import com.tbgbook.bookstore.ai.domain.AiPromptVersion;
import com.tbgbook.bookstore.ai.domain.VersionPublishRequest;
import com.tbgbook.bookstore.ai.domain.VersionRollbackRequest;
import com.tbgbook.bookstore.ai.service.IAiPromptVersionService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * AI 提示词版本 Controller（`/ai/prompt/version`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/prompt/version")
public class AiPromptVersionController extends BaseController
{
    private final IAiPromptVersionService aiPromptVersionService;

    @PreAuthorize("@ss.hasPermi('ai:prompt:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiPromptVersion aiPromptVersion)
    {
        startPage();
        return getDataTable(aiPromptVersionService.selectAiPromptVersionList(aiPromptVersion));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:list')")
    @GetMapping("/{versionId}")
    public AjaxResult getInfo(@PathVariable("versionId") Long versionId)
    {
        return success(aiPromptVersionService.getById(versionId));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:add')")
    @Log(title = "AI 提示词版本", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiPromptVersion aiPromptVersion)
    {
        return toAjax(aiPromptVersionService.addVersion(aiPromptVersion));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:edit')")
    @Log(title = "AI 提示词版本", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiPromptVersion aiPromptVersion)
    {
        return toAjax(aiPromptVersionService.updateVersion(aiPromptVersion));
    }

    @PreAuthorize("@ss.hasPermi('ai:prompt:remove')")
    @Log(title = "AI 提示词版本", businessType = BusinessType.DELETE)
    @DeleteMapping("/{versionIds}")
    public AjaxResult remove(@PathVariable("versionIds") Long[] versionIds)
    {
        return toAjax(aiPromptVersionService.removeVersions(versionIds));
    }

    /**
     * 发布版本（不带灰度参数 = 全量；带 = 灰度）。
     */
    @PreAuthorize("@ss.hasPermi('ai:prompt')")
    @Log(title = "AI 提示词版本", businessType = BusinessType.UPDATE)
    @PostMapping("/{versionId}/publish")
    public AjaxResult publish(@PathVariable("versionId") Long versionId,
            @RequestBody(required = false) VersionPublishRequest request)
    {
        return success(aiPromptVersionService.publish(versionId, request));
    }

    /**
     * 回滚到指定历史版本（被回滚的原当前版本置 4 已回滚）。
     */
    @PreAuthorize("@ss.hasPermi('ai:prompt')")
    @Log(title = "AI 提示词版本", businessType = BusinessType.UPDATE)
    @PostMapping("/{versionId}/rollback")
    public AjaxResult rollback(@PathVariable("versionId") Long versionId,
            @RequestBody(required = false) VersionRollbackRequest request)
    {
        return success(aiPromptVersionService.rollback(versionId, request));
    }
}
