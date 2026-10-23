package com.tbgbook.bookstore.app.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.ai.core.AiChatCore;
import com.tbgbook.bookstore.ai.core.AiGuard;
import com.tbgbook.bookstore.ai.core.ChatDeltaEvent;
import com.tbgbook.bookstore.ai.core.ChatOutcome;
import com.tbgbook.bookstore.ai.core.ChatRequest;
import com.tbgbook.bookstore.ai.core.QuotaGuard;
import com.tbgbook.bookstore.ai.domain.AiMessage;
import com.tbgbook.bookstore.ai.domain.AiSession;
import com.tbgbook.bookstore.ai.domain.SessionTransferRequest;
import com.tbgbook.bookstore.ai.mapper.AiMessageMapper;
import com.tbgbook.bookstore.ai.mapper.AiSessionMapper;
import com.tbgbook.bookstore.ai.service.IAiSessionService;
import com.tbgbook.bookstore.app.domain.request.AppAiChatRequest;
import com.tbgbook.bookstore.app.domain.request.AppAiFeedbackRequest;
import com.tbgbook.bookstore.app.domain.request.AppAiTransferRequest;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.service.IAppAiService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.service.IMemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 会员端 AI 客服 Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppAiServiceImpl implements IAppAiService
{
    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiChatCore aiChatCore;

    private final AiGuard aiGuard;

    private final QuotaGuard quotaGuard;

    private final AiSessionMapper aiSessionMapper;

    private final AiMessageMapper aiMessageMapper;

    private final IAiSessionService aiSessionService;

    private final IMemberService memberService;

    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter chat(AppAiChatRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        Member member = memberService.selectMemberById(memberId);
        if (member == null)
        {
            throw BizErrors.notFound("会员", memberId);
        }

        AiSession session = null;
        if (request.getSessionId() != null)
        {
            session = requireOwnedSession(request.getSessionId(), memberId);
            if (Integer.valueOf(2).equals(session.getStatus()))
            {
                throw BizErrors.conflict("会话已结束");
            }
            if (Integer.valueOf(3).equals(session.getStatus()))
            {
                throw BizErrors.conflict("会话已转人工，请等待店员回复");
            }
        }

        // SSE 建连前同步校验，保证 429/503 仍走普通 HTTP 错误响应。
        aiGuard.ensureUsable(AiGuard.AiChannel.CHAT);
        quotaGuard.checkMember(memberId, "chat");
        if (session == null)
        {
            session = createSession(member, request.getEntryContext());
        }

        Map<String, Object> realtimeData = realtimeData(member);
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setSessionId(session.getSessionId());
        chatRequest.setMemberId(memberId);
        chatRequest.setQuestion(request.getQuestion().trim());
        chatRequest.setRealtimeData(realtimeData);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Long sessionId = session.getSessionId();
        CompletableFuture.runAsync(() -> stream(emitter, chatRequest, sessionId, realtimeData));
        return emitter;
    }

    @Override
    public Map<String, Object> history(Long sessionId, int limit)
    {
        Long memberId = AppContext.requireMemberId();
        AiSession session = requireOwnedSession(sessionId, memberId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<AiMessage> messages = new ArrayList<>(aiMessageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getSessionId, sessionId)
                        .orderByDesc(AiMessage::getMessageId)
                        .last("limit " + safeLimit)));
        Collections.reverse(messages);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session", session);
        data.put("messages", messages);
        return data;
    }

    @Override
    public Map<String, Object> feedback(Long sessionId, AppAiFeedbackRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        requireOwnedSession(sessionId, memberId);
        AiMessage message = aiMessageMapper.selectById(request.getMessageId());
        if (message == null)
        {
            throw BizErrors.notFound("AI 消息", request.getMessageId());
        }
        if (!sessionId.equals(message.getSessionId()))
        {
            throw BizErrors.forbidden("消息不属于当前会话");
        }
        if (!Integer.valueOf(2).equals(message.getRole()))
        {
            throw BizErrors.badRequest("只能对 AI 回答进行反馈");
        }
        AiMessage update = new AiMessage();
        update.setMessageId(message.getMessageId());
        update.setFeedback(request.getFeedback());
        aiMessageMapper.updateById(update);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", message.getMessageId());
        data.put("feedback", request.getFeedback());
        return data;
    }

    @Override
    public Map<String, Object> transfer(Long sessionId, AppAiTransferRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        requireOwnedSession(sessionId, memberId);
        SessionTransferRequest transferRequest = new SessionTransferRequest();
        transferRequest.setTransferReason(request == null || !StringUtils.hasText(request.getReason())
                ? "会员主动请求转人工"
                : request.getReason().trim());
        return aiSessionService.transfer(sessionId, transferRequest);
    }

    private AiSession createSession(Member member, String entryContext)
    {
        AiSession session = new AiSession();
        session.setScene(1);
        session.setSource(1);
        session.setMemberId(member.getMemberId());
        session.setMemberName(memberName(member));
        session.setEntryContext(entryContext == null ? "" : entryContext.trim());
        if (aiSessionService.addAiSession(session) != 1)
        {
            throw new IllegalStateException("创建 AI 会话失败");
        }
        return session;
    }

    private AiSession requireOwnedSession(Long sessionId, Long memberId)
    {
        AiSession session = aiSessionMapper.selectById(sessionId);
        if (session == null)
        {
            throw BizErrors.notFound("会话", sessionId);
        }
        if (!memberId.equals(session.getMemberId()))
        {
            throw BizErrors.forbidden("会话不属于当前会员");
        }
        return session;
    }

    private Map<String, Object> realtimeData(Member member)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", money(member.getBalancePrincipal()).add(money(member.getBalanceGift())));
        data.put("principalBalance", money(member.getBalancePrincipal()));
        data.put("giftBalance", money(member.getBalanceGift()));
        data.put("points", member.getPoints() == null ? 0 : member.getPoints());
        data.put("depositBalance", money(member.getDepositBalance()));
        data.put("memberLevel", member.getLevelName() == null ? "" : member.getLevelName());
        data.put("dataTime", LocalDateTime.now().format(TIME_FORMAT));
        return data;
    }

    private BigDecimal money(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }

    private String memberName(Member member)
    {
        if (StringUtils.hasText(member.getNickname()))
        {
            return member.getNickname();
        }
        if (StringUtils.hasText(member.getRealName()))
        {
            return member.getRealName();
        }
        return member.getMemberNo() == null ? "会员" : member.getMemberNo();
    }

    private void stream(SseEmitter emitter, ChatRequest request, Long sessionId,
            Map<String, Object> realtimeData)
    {
        try
        {
            ChatOutcome outcome = aiChatCore.answerAfterQuotaCheck(request, event -> {
                if ("delta".equals(event.getType()) && StringUtils.hasText(event.getContent()))
                {
                    send(emitter, "delta", Map.of("delta", event.getContent()));
                }
            });
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sessionId", sessionId);
            metadata.put("messageId", outcome.getMessageId());
            metadata.put("confidence", outcome.getConfidence());
            metadata.put("isLowConfidence", outcome.isLowConfidence());
            metadata.put("isFallback", outcome.isFallback());
            metadata.put("citations", parseCitations(outcome.getCitations()));
            metadata.put("realtimeData", realtimeData);
            metadata.put("firstTokenMs", outcome.getFirstTokenMs());
            metadata.put("latencyMs", outcome.getLatencyMs());
            send(emitter, "done", metadata);
            emitter.complete();
        }
        catch (Exception e)
        {
            log.warn("Member AI stream failed: {}", e.getMessage());
            try
            {
                send(emitter, "error", Map.of(
                        "code", 503,
                        "msg", "AI 服务暂时不可用，请稍后重试或转人工"));
            }
            catch (Exception ignored)
            {
                // The client may have disconnected before the terminal event.
            }
            emitter.complete();
        }
    }

    private void send(SseEmitter emitter, String event, Object data)
    {
        try
        {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("SSE send failed", e);
        }
    }

    private Object parseCitations(String citations)
    {
        if (!StringUtils.hasText(citations))
        {
            return List.of();
        }
        try
        {
            return objectMapper.readValue(citations, Object.class);
        }
        catch (Exception ignored)
        {
            return List.of();
        }
    }
}
