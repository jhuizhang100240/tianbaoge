package com.tbgbook.bookstore.ai.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tbgbook.bookstore.ai.config.AiProperties;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * AI 可用性守卫（总开关 / 配置完整性 / 熔断）。
 *
 * <p>调用顺序：先 ensureUsable，再执行远端调用；失败一律 503（FR-4.7 规则 3）。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class AiGuard
{
    private final ISysConfigService configService;

    private final AiCircuitBreaker aiCircuitBreaker;

    private final AiProperties aiProperties;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    public enum AiChannel
    {
        /** 对话生成 */
        CHAT,
        /** 向量化（含检索用 embedding） */
        EMBEDDING
    }

    public void ensureUsable(AiChannel channel)
    {
        if (!enabled())
        {
            throw BizErrors.serviceUnavailable("AI 功能已在后台停用");
        }
        if (!StringUtils.hasText(dashscopeApiKey))
        {
            throw BizErrors.serviceUnavailable("AI 服务未配置密钥，请联系管理员");
        }
        if (channel == AiChannel.EMBEDDING
                && !StringUtils.hasText(aiProperties.getQdrant().getBaseUrl()))
        {
            throw BizErrors.serviceUnavailable("向量库未配置，请联系管理员");
        }
        if (aiCircuitBreaker.isOpen(channel))
        {
            throw BizErrors.serviceUnavailable("AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 全局总开关（biz.ai.enabled）。
     */
    public boolean enabled()
    {
        String raw = configService.selectConfigByKey("biz.ai.enabled");
        return !StringUtils.hasText(raw) || Boolean.parseBoolean(raw.trim());
    }
}
