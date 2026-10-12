package com.tbgbook.bookstore.ai.vector;

/**
 * Qdrant REST 调用异常。
 *
 * <p>消息里只带状态码与截断后的响应体（长度上限 {@code 300}），**不得包含 api-key**。
 * 上层（Task 5/6 的索引与检索）捕获后统一转降级或失败记录。
 *
 * @author tbgbook
 */
public class QdrantException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public QdrantException(String message)
    {
        super(message);
    }

    public QdrantException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
