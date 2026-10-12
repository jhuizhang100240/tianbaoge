package com.tbgbook.bookstore.ai.core;

/**
 * AI 域 Redis 键约定（集中一处，避免各组件拼串漂移）。
 *
 * <p>键用途见 AI 域设计稿：索引锁/索引上下文/当前代次（§5.1/§5.2）、熔断（§5.7）、限流（§5.6）。
 *
 * @author tbgbook
 */
public final class AiRedisKeys
{
    private static final String PREFIX = "tbgbook:ai:";

    private AiRedisKeys()
    {
    }

    /** 索引处理锁（文档级，防重入） */
    public static String indexLock(long docId)
    {
        return PREFIX + "index:lock:" + docId;
    }

    /** 索引上下文（gen + chunkIds，进度计数与切换清理的唯一依据） */
    public static String indexCtx(long docId)
    {
        return PREFIX + "index:ctx:" + docId;
    }

    /** 文档当前生效代次（激活时写入；无 TTL，丢失时由检索回表复核兜底） */
    public static String currentGen(long docId)
    {
        return PREFIX + "kb:currentgen:" + docId;
    }

    /** 熔断器（channel = chat / embedding） */
    public static String circuit(String channel)
    {
        return PREFIX + "circuit:" + channel;
    }

    /** 限流器键（scope/key/window） */
    public static String rate(String scope, String key, String window)
    {
        return PREFIX + "rate:" + scope + ":" + key + ":" + window;
    }
}
