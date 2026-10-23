package com.tbgbook.bookstore.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tbgbook.bookstore.ai.core.AiUnavailableException;
import com.tbgbook.bookstore.ai.vector.QdrantException;
import com.tbgbook.bookstore.common.constant.BizErrorCode;
import com.tbgbook.common.core.domain.AjaxResult;

/**
 * AI 域依赖故障 → 503 映射。
 *
 * <p>{@code GlobalExceptionHandler} 只按 {@link RuntimeException} 兜底 500；
 * 本 advice 以最高优先级排在它前面，把百炼 / Qdrant 运行期不可用统一映射为
 * 业务错误码 503（契约见 docs/api-documentation.md §业务错误码）。
 *
 * @author tbgbook
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AiExceptionAdvice
{
    private static final Logger log = LoggerFactory.getLogger(AiExceptionAdvice.class);

    /**
     * AI 通道不可用（总开关、熔断、模型调用失败）。
     */
    @ExceptionHandler(AiUnavailableException.class)
    public AjaxResult handleAiUnavailable(AiUnavailableException e)
    {
        log.warn("AI dependency unavailable: {}", e.getMessage());
        return AjaxResult.error(BizErrorCode.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /**
     * Qdrant 调用失败：上游细节只进日志，对外返回稳定文案。
     */
    @ExceptionHandler(QdrantException.class)
    public AjaxResult handleQdrantException(QdrantException e)
    {
        log.warn("qdrant unavailable: {}", e.getMessage());
        return AjaxResult.error(BizErrorCode.SERVICE_UNAVAILABLE, "向量检索服务暂时不可用，请稍后重试");
    }
}
