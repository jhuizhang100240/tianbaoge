package com.tbgbook.bookstore.ai.core;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 自研熔断器（T-4：不引 Resilience4j）。
 *
 * <p>按渠道独立（chat / embedding）：连续失败达阈值打开；到期半开放行 1 次试探，
 * 成功关闭、失败重开。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class AiCircuitBreaker
{
    private static final int DEFAULT_THRESHOLD = 5;

    private static final int DEFAULT_OPEN_SECONDS = 60;

    private final RedissonClient redissonClient;

    private final ISysConfigService configService;

    public boolean isOpen(AiGuard.AiChannel channel)
    {
        String key = AiRedisKeys.circuit(channel.name().toLowerCase());
        if ("open".equals(redissonClient.getBucket(key).get()))
        {
            return true;
        }
        if (!redissonClient.getBucket(key + ":tripped").isExists())
        {
            return false;
        }
        RBucket<String> probe = redissonClient.getBucket(key + ":probe");
        return !probe.trySet("1", 10, TimeUnit.SECONDS);
    }

    public void onSuccess(AiGuard.AiChannel channel)
    {
        String key = AiRedisKeys.circuit(channel.name().toLowerCase());
        redissonClient.getBucket(key).delete();
        redissonClient.getBucket(key + ":tripped").delete();
        redissonClient.getBucket(key + ":probe").delete();
        redissonClient.getAtomicLong(key + ":failures").delete();
    }

    public void onFailure(AiGuard.AiChannel channel)
    {
        String key = AiRedisKeys.circuit(channel.name().toLowerCase());
        RBucket<String> state = redissonClient.getBucket(key);
        RBucket<String> tripped = redissonClient.getBucket(key + ":tripped");
        if (tripped.isExists())
        {
            state.set("open", openSeconds(), TimeUnit.SECONDS);
            return;
        }
        RAtomicLong failures = redissonClient.getAtomicLong(key + ":failures");
        long count = failures.incrementAndGet();
        failures.expire(Duration.ofMinutes(10));
        if (count >= threshold())
        {
            tripped.set("1");
            state.set("open", openSeconds(), TimeUnit.SECONDS);
            failures.delete();
        }
    }

    private int threshold()
    {
        return intConfig("biz.ai.circuit.failure-threshold", DEFAULT_THRESHOLD);
    }

    private int openSeconds()
    {
        return intConfig("biz.ai.circuit.open-seconds", DEFAULT_OPEN_SECONDS);
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (!StringUtils.hasText(raw))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }
}
