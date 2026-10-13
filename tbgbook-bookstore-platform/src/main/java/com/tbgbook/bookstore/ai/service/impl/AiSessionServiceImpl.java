package com.tbgbook.bookstore.ai.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.core.SensitiveMasker;
import com.tbgbook.bookstore.ai.domain.AiAnnotation;
import com.tbgbook.bookstore.ai.domain.AiMessage;
import com.tbgbook.bookstore.ai.domain.AiSession;
import com.tbgbook.bookstore.ai.domain.ManualReplyRequest;
import com.tbgbook.bookstore.ai.domain.SessionTransferRequest;
import com.tbgbook.bookstore.ai.mapper.AiAnnotationMapper;
import com.tbgbook.bookstore.ai.mapper.AiMessageMapper;
import com.tbgbook.bookstore.ai.mapper.AiSessionMapper;
import com.tbgbook.bookstore.ai.service.IAiSessionService;
import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * AI 会话 Service 实现（状态机：1进行中 → 3已转人工 → 2已结束）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AiSessionServiceImpl extends ServiceImpl<AiSessionMapper, AiSession> implements IAiSessionService
{
    private static final long STORE_ID = 1L;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiMessageMapper aiMessageMapper;

    private final AiAnnotationMapper aiAnnotationMapper;

    private final SensitiveMasker sensitiveMasker;

    @Override
    public List<AiSession> selectAiSessionList(AiSession query)
    {
        LambdaQueryWrapper<AiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiSession::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.eq(StringUtils.hasText(query.getSessionNo()), AiSession::getSessionNo, query.getSessionNo());
            wrapper.eq(query.getMemberId() != null, AiSession::getMemberId, query.getMemberId());
            wrapper.eq(query.getScene() != null, AiSession::getScene, query.getScene());
            wrapper.eq(query.getSource() != null, AiSession::getSource, query.getSource());
            wrapper.eq(query.getHasTransfer() != null, AiSession::getHasTransfer, query.getHasTransfer());
            wrapper.eq(query.getStatus() != null, AiSession::getStatus, query.getStatus());
            wrapper.eq(query.getSatisfaction() != null, AiSession::getSatisfaction, query.getSatisfaction());
            wrapper.eq(query.getIsOfflineMsg() != null, AiSession::getIsOfflineMsg, query.getIsOfflineMsg());
        }
        wrapper.orderByDesc(AiSession::getSessionId);
        return list(wrapper);
    }

    @Override
    public int addAiSession(AiSession session)
    {
        if (session == null || session.getScene() == null || session.getScene() < 1 || session.getScene() > 5)
        {
            throw BizErrors.badRequest("场景取值 1-5");
        }
        if (session.getSource() != null && (session.getSource() < 1 || session.getSource() > 2))
        {
            throw BizErrors.badRequest("来源取值 1会员端 / 2管理后台");
        }
        session.setSessionId(null);
        session.setStoreId(STORE_ID);
        session.setSessionNo(nextSessionNo());
        session.setMemberId(session.getMemberId() == null ? 0L : session.getMemberId());
        session.setMemberName(session.getMemberName() == null ? "" : session.getMemberName());
        session.setSource(session.getSource() == null ? 2 : session.getSource());
        session.setEntryContext(session.getEntryContext() == null ? "" : session.getEntryContext());
        session.setTurnCount(0);
        session.setHasTransfer(0);
        session.setTransferReason("");
        session.setAgentBy("");
        session.setSatisfaction(0);
        session.setIsOfflineMsg(0);
        session.setStatus(1);
        session.setStartTime(LocalDateTime.now());
        return save(session) ? 1 : 0;
    }

    @Override
    public int updateAiSession(AiSession session)
    {
        if (session == null || session.getSessionId() == null)
        {
            throw BizErrors.badRequest("会话ID不能为空");
        }
        if (getById(session.getSessionId()) == null)
        {
            throw BizErrors.notFound("会话", session.getSessionId());
        }
        if (session.getScene() != null && (session.getScene() < 1 || session.getScene() > 5))
        {
            throw BizErrors.badRequest("场景取值 1-5");
        }
        if (session.getSource() != null && (session.getSource() < 1 || session.getSource() > 2))
        {
            throw BizErrors.badRequest("来源取值 1会员端 / 2管理后台");
        }
        // 服务端管理字段不允许客户端直接改（状态机走 transfer/close 动作）
        session.setStoreId(null);
        session.setSessionNo(null);
        session.setTurnCount(null);
        session.setHasTransfer(null);
        session.setTransferTime(null);
        session.setSatisfaction(null);
        session.setIsOfflineMsg(null);
        session.setStatus(null);
        session.setStartTime(null);
        session.setEndTime(null);
        return updateById(session) ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeAiSessions(Long[] sessionIds)
    {
        if (sessionIds == null || sessionIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的会话");
        }
        int count = 0;
        for (Long sessionId : sessionIds)
        {
            if (getById(sessionId) == null)
            {
                throw BizErrors.notFound("会话", sessionId);
            }
            aiAnnotationMapper.delete(new LambdaQueryWrapper<AiAnnotation>().eq(AiAnnotation::getSessionId, sessionId));
            aiMessageMapper.delete(new LambdaQueryWrapper<AiMessage>().eq(AiMessage::getSessionId, sessionId));
            removeById(sessionId);
            count++;
        }
        return count;
    }

    @Override
    public Map<String, Object> transfer(Long sessionId, SessionTransferRequest request)
    {
        AiSession session = getById(sessionId);
        if (session == null)
        {
            throw BizErrors.notFound("会话", sessionId);
        }
        if (Integer.valueOf(2).equals(session.getStatus()))
        {
            throw BizErrors.conflict("会话已结束");
        }
        if (Integer.valueOf(1).equals(session.getHasTransfer()))
        {
            throw BizErrors.conflict("会话已转人工");
        }
        if (request == null || !StringUtils.hasText(request.getTransferReason()))
        {
            throw BizErrors.badRequest("转人工原因不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        AiSession update = new AiSession();
        update.setSessionId(sessionId);
        update.setHasTransfer(1);
        update.setStatus(3);
        update.setTransferReason(request.getTransferReason());
        update.setTransferTime(now);
        update.setAgentBy(request.getAgentBy() == null ? "" : request.getAgentBy());
        updateById(update);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("status", 3);
        data.put("hasTransfer", 1);
        data.put("transferTime", now.format(TIME_FORMAT));
        data.put("agentBy", update.getAgentBy());
        return data;
    }

    @Override
    public Map<String, Object> close(Long sessionId)
    {
        AiSession session = getById(sessionId);
        if (session == null)
        {
            throw BizErrors.notFound("会话", sessionId);
        }
        if (Integer.valueOf(2).equals(session.getStatus()))
        {
            throw BizErrors.conflict("会话已结束");
        }
        AiSession update = new AiSession();
        update.setSessionId(sessionId);
        update.setStatus(2);
        update.setEndTime(LocalDateTime.now());
        updateById(update);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("status", 2);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> sendManual(ManualReplyRequest request)
    {
        if (request == null || request.getSessionId() == null)
        {
            throw BizErrors.badRequest("会话ID不能为空");
        }
        if (!StringUtils.hasText(request.getContent()))
        {
            throw BizErrors.badRequest("回复内容不能为空");
        }
        AiSession session = getById(request.getSessionId());
        if (session == null)
        {
            throw BizErrors.notFound("会话", request.getSessionId());
        }
        if (Integer.valueOf(2).equals(session.getStatus()))
        {
            throw BizErrors.conflict("会话已结束");
        }
        if (!Integer.valueOf(1).equals(session.getHasTransfer()) || !Integer.valueOf(3).equals(session.getStatus()))
        {
            throw BizErrors.ruleViolated("该会话未转人工，不能人工回复");
        }
        AiMessage message = new AiMessage();
        message.setSessionId(session.getSessionId());
        message.setRole(3);
        message.setContent(sensitiveMasker.mask(request.getContent()));
        message.setConfidence(BigDecimal.ZERO);
        message.setIsLowConfidence(0);
        message.setIsFallback(0);
        message.setModelName("");
        message.setPromptTokens(0);
        message.setCompletionTokens(0);
        message.setLatencyMs(0);
        message.setFirstTokenMs(0);
        message.setFeedback(0);
        aiMessageMapper.insert(message);

        AiSession update = new AiSession();
        update.setSessionId(session.getSessionId());
        update.setTurnCount((session.getTurnCount() == null ? 0 : session.getTurnCount()) + 1);
        updateById(update);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", message.getMessageId());
        data.put("role", 3);
        data.put("content", message.getContent());
        data.put("createTime", message.getCreateTime() == null ? "" : message.getCreateTime().format(TIME_FORMAT));
        return data;
    }

    private String nextSessionNo()
    {
        AiSession last = getOne(new LambdaQueryWrapper<AiSession>()
                .orderByDesc(AiSession::getSessionId).last("limit 1"), false);
        long next = last == null || last.getSessionId() == null ? 1L : last.getSessionId() + 1L;
        return String.format("AS%s%04d",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")), next % 10000L);
    }
}
