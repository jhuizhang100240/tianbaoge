package com.tbgbook.bookstore.app.controller;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.tbgbook.bookstore.app.domain.request.AppAiChatRequest;
import com.tbgbook.bookstore.app.domain.request.AppAiFeedbackRequest;
import com.tbgbook.bookstore.app.domain.request.AppAiTransferRequest;
import com.tbgbook.bookstore.app.service.IAppAiService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端 AI 客服 Controller（B-19）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/ai")
public class AppAiController extends BaseController
{
    private final IAppAiService aiService;

    @PostMapping("/chat")
    public SseEmitter chat(@Validated @RequestBody AppAiChatRequest request)
    {
        return aiService.chat(request);
    }

    @GetMapping("/session/{sessionId}")
    public AjaxResult history(@PathVariable("sessionId") Long sessionId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit)
    {
        return success(aiService.history(sessionId, limit));
    }

    @PostMapping("/session/{sessionId}/feedback")
    public AjaxResult feedback(@PathVariable("sessionId") Long sessionId,
            @Validated @RequestBody AppAiFeedbackRequest request)
    {
        return success(aiService.feedback(sessionId, request));
    }

    @PostMapping("/session/{sessionId}/transfer")
    public AjaxResult transfer(@PathVariable("sessionId") Long sessionId,
            @Validated @RequestBody(required = false) AppAiTransferRequest request)
    {
        return success(aiService.transfer(sessionId, request));
    }
}
