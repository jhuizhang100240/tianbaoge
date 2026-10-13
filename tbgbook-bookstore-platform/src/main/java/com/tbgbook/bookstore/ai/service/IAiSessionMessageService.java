package com.tbgbook.bookstore.ai.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.AiMessage;

/**
 * AI 会话消息 Service（只读 + 由问答/人工回复链路写入）。
 *
 * @author tbgbook
 */
public interface IAiSessionMessageService extends IService<AiMessage>
{
    List<AiMessage> selectAiMessageList(AiMessage query);
}
