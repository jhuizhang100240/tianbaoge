package com.tbgbook.bookstore.ai.core;

/**
 * AI 依赖（百炼 / Qdrant）不可用。
 *
 * <p>调用方捕获后按场景转降级（503/兜底），**不得直接把堆栈暴露给前端**。
 *
 * @author tbgbook
 */
public class AiUnavailableException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AiUnavailableException(String message)
    {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
