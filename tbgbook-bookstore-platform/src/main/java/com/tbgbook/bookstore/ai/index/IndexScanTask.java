package com.tbgbook.bookstore.ai.index;

import java.time.LocalDateTime;
import java.util.List;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.ai.core.AiRedisKeys;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.mapper.KbDocMapper;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 索引扫描任务：每 30s 领取待处理/可接续文档交给管线；并对超时（>5 分钟处理中）告警。
 *
 * <p>串行处理即并发 1，天然 ≤ {@code biz.ai.index.max-parallel}；单轮领取 ≤
 * {@code biz.ai.index.scan-batch}。文档级锁在管线内（{@code IndexingPipeline.process}）。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class IndexScanTask
{
    private static final Logger log = LoggerFactory.getLogger(IndexScanTask.class);

    private final KbDocMapper kbDocMapper;

    private final IndexingPipeline indexingPipeline;

    private final RedissonClient redissonClient;

    private final ISysConfigService configService;

    private final com.tbgbook.bookstore.ai.core.AiGuard aiGuard;

    @Scheduled(fixedDelay = 30000L, initialDelay = 15000L)
    public void scan()
    {
        int scanBatch = Math.max(1, intConfig("biz.ai.index.scan-batch", 3));
        List<KbDoc> candidates = kbDocMapper.selectList(new LambdaQueryWrapper<KbDoc>()
                .select(KbDoc::getDocId, KbDoc::getIndexStatus)
                .in(KbDoc::getIndexStatus, 1, 2)
                .orderByAsc(KbDoc::getIndexStatus)
                .orderByAsc(KbDoc::getDocId)
                .last("limit " + scanBatch));
        if (!aiGuard.enabled())
        {
            for (KbDoc candidate : candidates)
            {
                KbDoc update = new KbDoc();
                update.setDocId(candidate.getDocId());
                update.setIndexStatus(4);
                update.setFailReason("AI 已全局停用");
                kbDocMapper.updateById(update);
            }
            warnTimeoutDocs();
            return;
        }
        for (KbDoc candidate : candidates)
        {
            if (Integer.valueOf(2).equals(candidate.getIndexStatus())
                    && redissonClient.getBucket(AiRedisKeys.indexLock(candidate.getDocId())).isExists())
            {
                continue;
            }
            try
            {
                indexingPipeline.process(candidate.getDocId());
            }
            catch (Exception e)
            {
                log.error("index scan process failed, docId={}", candidate.getDocId(), e);
            }
        }
        warnTimeoutDocs();
    }

    private void warnTimeoutDocs()
    {
        int timeoutMinutes = Math.max(1, intConfig("biz.kb.index.timeout.minutes", 5));
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<KbDoc> stuck = kbDocMapper.selectList(new LambdaQueryWrapper<KbDoc>()
                .select(KbDoc::getDocId, KbDoc::getUpdateTime)
                .eq(KbDoc::getIndexStatus, 2)
                .lt(KbDoc::getUpdateTime, threshold));
        for (KbDoc doc : stuck)
        {
            log.warn("kb index timeout: docId={}, updateTime={}", doc.getDocId(), doc.getUpdateTime());
        }
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
