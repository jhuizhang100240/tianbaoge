package com.tbgbook.bookstore.ai.index;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.ai.core.AiRedisKeys;

import lombok.RequiredArgsConstructor;

/**
 * 索引上下文存取（Redisson RBucket + JSON 字符串，可读、可按时过期）。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class IndexContextStore
{
    private static final long TTL_HOURS = 24L;

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    public IndexContext begin(long docId, long gen, List<Long> chunkIds)
    {
        IndexContext context = new IndexContext(gen, chunkIds, System.currentTimeMillis());
        save(docId, context);
        return context;
    }

    public Optional<IndexContext> get(long docId)
    {
        String raw = bucket(docId).get();
        if (!StringUtils.hasText(raw))
        {
            return Optional.empty();
        }
        try
        {
            return Optional.of(objectMapper.readValue(raw, IndexContext.class));
        }
        catch (JsonProcessingException e)
        {
            return Optional.empty();
        }
    }

    public void touch(long docId)
    {
        bucket(docId).expire(java.time.Duration.ofHours(TTL_HOURS));
    }

    public void clear(long docId)
    {
        bucket(docId).delete();
    }

    private void save(long docId, IndexContext context)
    {
        try
        {
            bucket(docId).set(objectMapper.writeValueAsString(context), TTL_HOURS, TimeUnit.HOURS);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("serialize index context failed", e);
        }
    }

    private RBucket<String> bucket(long docId)
    {
        return redissonClient.getBucket(AiRedisKeys.indexCtx(docId));
    }
}
