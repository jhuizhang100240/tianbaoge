package com.tbgbook.bookstore.ai.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiSession;
import com.tbgbook.bookstore.ai.domain.ManualReplyRequest;
import com.tbgbook.bookstore.ai.domain.SessionTransferRequest;

/**
 * AI 会话 Service。
 *
 * @author tbgbook
 */
public interface IAiSessionService extends IService<AiSession>
{
    List<AiSession> selectAiSessionList(AiSession query);

    int addAiSession(AiSession session);

    int updateAiSession(AiSession session);

    int removeAiSessions(Long[] sessionIds);

    /**
     * 转人工（低置信度一键转人工 / 用户主动 / 兜底）。
     */
    Map<String, Object> transfer(Long sessionId, SessionTransferRequest request);

    /**
     * 结束会话。
     */
    Map<String, Object> close(Long sessionId);

    /**
     * 人工客服回复（role=3；须已转人工且未结束；内容脱敏后落库）。
     */
    Map<String, Object> sendManual(ManualReplyRequest request);
}
