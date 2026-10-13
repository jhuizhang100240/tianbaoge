package com.tbgbook.bookstore.ai.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.ai.domain.AiMessage;
import com.tbgbook.bookstore.ai.domain.ManualReplyRequest;
import com.tbgbook.bookstore.ai.service.IAiSessionMessageService;
import com.tbgbook.bookstore.ai.service.IAiSessionService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * AI 会话消息 Controller（只读列表/详情 + 人工回复写入）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/session/message")
public class AiSessionMessageController extends BaseController
{
    private final IAiSessionMessageService aiSessionMessageService;

    private final IAiSessionService aiSessionService;

    @PreAuthorize("@ss.hasPermi('ai:session:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiMessage aiMessage)
    {
        startPage();
        return getDataTable(aiSessionMessageService.selectAiMessageList(aiMessage));
    }

    @PreAuthorize("@ss.hasPermi('ai:session:list')")
    @GetMapping("/{messageId}")
    public AjaxResult getInfo(@PathVariable("messageId") Long messageId)
    {
        return success(aiSessionMessageService.getById(messageId));
    }

    /**
     * 人工客服回复（role=3，须已转人工且未结束）。
     */
    @PreAuthorize("@ss.hasPermi('ai:session')")
    @Log(title = "AI 会话消息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult sendManual(@RequestBody ManualReplyRequest request)
    {
        return success(aiSessionService.sendManual(request));
    }
}
