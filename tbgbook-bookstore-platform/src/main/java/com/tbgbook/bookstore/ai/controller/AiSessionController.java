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

import com.tbgbook.bookstore.ai.domain.AiSession;
import com.tbgbook.bookstore.ai.domain.SessionTransferRequest;
import com.tbgbook.bookstore.ai.service.IAiSessionService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * AI 会话 Controller（`/ai/session`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/session")
public class AiSessionController extends BaseController
{
    private final IAiSessionService aiSessionService;

    @PreAuthorize("@ss.hasPermi('ai:session:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiSession aiSession)
    {
        startPage();
        return getDataTable(aiSessionService.selectAiSessionList(aiSession));
    }

    @PreAuthorize("@ss.hasPermi('ai:session:list')")
    @GetMapping("/{sessionId}")
    public AjaxResult getInfo(@PathVariable("sessionId") Long sessionId)
    {
        return success(aiSessionService.getById(sessionId));
    }

    @PreAuthorize("@ss.hasPermi('ai:session:add')")
    @Log(title = "AI 会话", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiSession aiSession)
    {
        return toAjax(aiSessionService.addAiSession(aiSession));
    }

    @PreAuthorize("@ss.hasPermi('ai:session:edit')")
    @Log(title = "AI 会话", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiSession aiSession)
    {
        return toAjax(aiSessionService.updateAiSession(aiSession));
    }

    @PreAuthorize("@ss.hasPermi('ai:session:remove')")
    @Log(title = "AI 会话", businessType = BusinessType.DELETE)
    @DeleteMapping("/{sessionIds}")
    public AjaxResult remove(@PathVariable("sessionIds") Long[] sessionIds)
    {
        return toAjax(aiSessionService.removeAiSessions(sessionIds));
    }

    /**
     * 转人工（低置信度一键转人工 / 用户主动 / 兜底）。
     */
    @PreAuthorize("@ss.hasPermi('ai:session:transfer')")
    @Log(title = "AI 会话", businessType = BusinessType.UPDATE)
    @PostMapping("/{sessionId}/transfer")
    public AjaxResult transfer(@PathVariable("sessionId") Long sessionId,
            @RequestBody(required = false) SessionTransferRequest request)
    {
        return success(aiSessionService.transfer(sessionId, request));
    }

    /**
     * 结束会话。
     */
    @PreAuthorize("@ss.hasPermi('ai:session:close')")
    @Log(title = "AI 会话", businessType = BusinessType.UPDATE)
    @PostMapping("/{sessionId}/close")
    public AjaxResult close(@PathVariable("sessionId") Long sessionId)
    {
        return success(aiSessionService.close(sessionId));
    }
}
