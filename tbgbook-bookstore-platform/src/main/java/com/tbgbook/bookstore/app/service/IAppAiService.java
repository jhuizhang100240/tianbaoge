package com.tbgbook.bookstore.app.service;

import java.util.Map;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.tbgbook.bookstore.app.domain.request.AppAiChatRequest;
import com.tbgbook.bookstore.app.domain.request.AppAiFeedbackRequest;
import com.tbgbook.bookstore.app.domain.request.AppAiTransferRequest;

/**
 * 会员端 AI 客服 Service（B-19）。
 */
public interface IAppAiService
{
    SseEmitter chat(AppAiChatRequest request);

    Map<String, Object> history(Long sessionId, int limit);

    Map<String, Object> feedback(Long sessionId, AppAiFeedbackRequest request);

    Map<String, Object> transfer(Long sessionId, AppAiTransferRequest request);
}
