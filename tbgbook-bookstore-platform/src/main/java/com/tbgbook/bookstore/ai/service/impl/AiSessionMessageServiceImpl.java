package com.tbgbook.bookstore.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.domain.AiMessage;
import com.tbgbook.bookstore.ai.mapper.AiMessageMapper;
import com.tbgbook.bookstore.ai.service.IAiSessionMessageService;

/**
 * AI 会话消息 Service 实现（只读）。
 *
 * @author tbgbook
 */
@Service
public class AiSessionMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage>
        implements IAiSessionMessageService
{
    @Override
    public List<AiMessage> selectAiMessageList(AiMessage query)
    {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        if (query != null)
        {
            wrapper.eq(query.getSessionId() != null, AiMessage::getSessionId, query.getSessionId());
            wrapper.eq(query.getRole() != null, AiMessage::getRole, query.getRole());
            wrapper.eq(query.getIsLowConfidence() != null, AiMessage::getIsLowConfidence, query.getIsLowConfidence());
            wrapper.eq(query.getIsFallback() != null, AiMessage::getIsFallback, query.getIsFallback());
            wrapper.eq(query.getFeedback() != null, AiMessage::getFeedback, query.getFeedback());
        }
        wrapper.orderByDesc(AiMessage::getMessageId);
        return list(wrapper);
    }
}
