package com.tbgbook.bookstore.sale.support;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * 写操作的幂等支持（NFR-P2 约束 4，C5 已定的实现方式：**Redis 存 requestId → 首次响应体，带 TTL**）。
 *
 * <p>⚠️ <b>这不是 {@code @RepeatSubmit}</b>：那个注解只是短时间窗内防重复点击，
 * 既不落库也不返回首次结果 —— 重复提交仍会真的执行第二遍。本类保证的是
 * 「同 requestId 重复提交**返回首次的结果**、业务只执行一次」。
 *
 * <p><b>三态设计</b>：
 * <ol>
 *   <li>首次调用 → {@code setIfAbsent} 抢到键，写入「处理中」占位 → 正常执行</li>
 *   <li>并发重复（首次还在跑）→ 抢不到键且值是占位 → <b>409</b>（契约错误码原文）</li>
 *   <li>重复提交（首次已完成）→ 抢不到键但值是真结果 → <b>直接返回它</b>，不再执行业务</li>
 * </ol>
 * 执行失败时必须 {@link #release} 释放占位，否则客户端重试会被误判成「处理中」而永远拿不到结果。
 *
 * <p><b>TTL 取 24 小时</b>（C5 只定了「带 TTL」没给值）：要覆盖收银员误点、网络重试、
 * 以及跨班次补录的场景，一天足够；再长则 Redis 里堆无用键。
 */
@Component
@RequiredArgsConstructor
public class IdempotencySupport
{
    /** 键前缀（按业务分域，便于排查与清理） */
    private static final String KEY_PREFIX = "sale:checkout:req:";

    /** 「处理中」占位值 */
    private static final String PROCESSING = "__PROCESSING__";

    /** 幂等记录 TTL（小时） */
    private static final long TTL_HOURS = 24;

    /**
     * ⚠️ 必须是 {@code RedisTemplate<Object, Object>} —— 本项目的 {@code RedisConfig}
     * 就是这个签名（与 {@code RateLimiterAspect} 的注入一致）。
     * 写成 {@code RedisTemplate<String, Object>} 会因**泛型不匹配**找不到 Bean、
     * 应用直接起不来（实测踩到：`required a bean of type RedisTemplate that could not be found`）。
     */
    private final RedisTemplate<Object, Object> redisTemplate;

    /**
     * 尝试认领一个 requestId。
     *
     * @param requestId 幂等键
     * @return {@code null} 表示认领成功（本次要真正执行）；
     *         非 null 表示**重复提交**，其值就是首次的结果对象，直接返回给客户端即可
     * @throws com.tbgbook.common.exception.ServiceException 409 首次请求仍在处理中
     */
    public Object claim(String requestId)
    {
        String key = KEY_PREFIX + requestId;
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, PROCESSING, TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.TRUE.equals(ok))
        {
            return null;
        }
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null || PROCESSING.equals(cached))
        {
            // 抢不到键但读不到结果 → 首次请求还在事务里跑
            throw BizErrors.conflict("该 requestId（" + requestId + "）的首次请求仍在处理中，"
                    + "请勿重复提交；稍后可用同一 requestId 重试以取回结果");
        }
        return cached;
    }

    /** 执行成功后写入真实结果，覆盖占位（后续重复提交会拿到它）。 */
    public void complete(String requestId, Object result)
    {
        redisTemplate.opsForValue().set(KEY_PREFIX + requestId, result, TTL_HOURS, TimeUnit.HOURS);
    }

    /** 执行失败时释放占位，让客户端能用同一 requestId 重试。 */
    public void release(String requestId)
    {
        redisTemplate.delete(KEY_PREFIX + requestId);
    }
}
