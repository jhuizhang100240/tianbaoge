package com.tbgbook.bookstore.ai.index;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tbgbook.bookstore.ai.core.AiRedisKeys;
import com.tbgbook.bookstore.ai.core.AiUnavailableException;
import com.tbgbook.bookstore.ai.core.EmbeddingResult;
import com.tbgbook.bookstore.ai.core.EmbeddingService;
import com.tbgbook.bookstore.ai.domain.KbChunk;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.mapper.KbChunkMapper;
import com.tbgbook.bookstore.ai.mapper.KbDocMapper;
import com.tbgbook.bookstore.ai.service.impl.KbDocWriteTxService;
import com.tbgbook.bookstore.ai.vector.QdrantClient;
import com.tbgbook.bookstore.ai.vector.QdrantException;
import com.tbgbook.bookstore.ai.vector.QdrantPoint;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 索引管线（AI 域设计稿 §5.1/§5.2 状态机）。
 *
 * <p>流程：领取锁 → 建/续 {@link IndexContext} → 置处理中 → 按批（≤batch-size）嵌入并
 * upsert（active=false）→ 更新逐片状态与计数 → 全部成功则激活切换（激活新代 → 停用旧代 →
 * 事务删补集行 + 置已完成 → 尽力删旧点）；失败置 4 并记录原因，单切片 retry 可救回。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class IndexingPipeline
{
    private static final Logger log = LoggerFactory.getLogger(IndexingPipeline.class);

    private static final long LOCK_MINUTES = 10L;

    private static final int DEFAULT_BATCH_SIZE = 10;

    private final KbDocMapper kbDocMapper;

    private final KbChunkMapper kbChunkMapper;

    private final EmbeddingService embeddingService;

    private final QdrantClient qdrantClient;

    private final IndexContextStore contextStore;

    private final KbDocWriteTxService writeTxService;

    private final RedissonClient redissonClient;

    private final ISysConfigService configService;

    /**
     * 处理一个文档（文档级锁防重入；异常兜底置 4）。
     */
    public void process(long docId)
    {
        String token = java.util.UUID.randomUUID().toString();
        RBucket<String> lock = redissonClient.getBucket(AiRedisKeys.indexLock(docId));
        if (!lock.trySet(token, LOCK_MINUTES, TimeUnit.MINUTES))
        {
            return;
        }
        try
        {
            doProcess(docId, lock);
        }
        catch (Exception e)
        {
            log.error("index pipeline failed, docId={}", docId, e);
            markFailed(docId, truncate("索引失败：" + e.getMessage()));
        }
        finally
        {
            // 只删自己持有的锁：若锁已过期并被他人取得，不得误删
            lock.compareAndSet(token, null);
        }
    }

    /**
     * 单切片重试：成功后若文档无待处理/失败切片则自动激活。
     */
    public Map<String, Object> retryChunk(long chunkId)
    {
        KbChunk chunk = kbChunkMapper.selectById(chunkId);
        if (chunk == null)
        {
            throw BizErrors.notFound("切片", chunkId);
        }
        if (!Integer.valueOf(2).equals(chunk.getVectorStatus()))
        {
            throw BizErrors.ruleViolated("该切片向量化状态不是失败");
        }
        KbDoc doc = kbDocMapper.selectById(chunk.getDocId());
        if (doc == null)
        {
            throw BizErrors.notFound("文档", chunk.getDocId());
        }

        IndexContext context = contextStore.get(doc.getDocId()).orElseGet(() -> {
            List<Long> pendingIds = pendingChunkIds(doc.getDocId());
            return contextStore.begin(doc.getDocId(), System.currentTimeMillis(), pendingIds);
        });

        EmbeddingResult result;
        try
        {
            result = embeddingService.embedBatch(List.of(chunk.getContent()));
        }
        catch (AiUnavailableException e)
        {
            throw BizErrors.serviceUnavailable("AI 服务暂不可用，请稍后重试");
        }
        try
        {
            qdrantClient.upsert(List.of(new QdrantPoint(chunk.getVectorId(), result.getVectors().get(0),
                    payload(doc, chunk, context.getGen(), false))));
        }
        catch (QdrantException e)
        {
            throw BizErrors.serviceUnavailable("向量库暂不可用，请稍后重试");
        }
        kbChunkMapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                .eq(KbChunk::getChunkId, chunkId)
                .set(KbChunk::getVectorStatus, 1)
                .set(KbChunk::getFailReason, ""));
        contextStore.touch(doc.getDocId());

        long ok = countByStatus(context.getChunkIds(), 1);
        long failed = countByStatus(context.getChunkIds(), 2);
        long pending = context.getChunkIds().size() - ok - failed;
        updateCounters(doc.getDocId(), context.getChunkIds().size(), (int) ok, (int) failed);

        if (failed == 0 && pending == 0)
        {
            activate(doc, context, (int) ok);
        }
        else
        {
            markFailed(doc.getDocId(), truncate("向量化失败 " + (failed + pending) + "/" + context.getChunkIds().size()));
        }
        KbChunk fresh = kbChunkMapper.selectById(chunkId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chunkId", chunkId);
        data.put("vectorStatus", fresh.getVectorStatus());
        data.put("failReason", fresh.getFailReason());
        return data;
    }

    // ------------------------------------------------------------------

    private void doProcess(long docId, RBucket<String> lock)
    {
        KbDoc doc = kbDocMapper.selectById(docId);
        if (doc == null)
        {
            return;
        }
        IndexContext context = contextStore.get(docId).orElse(null);
        if (context == null)
        {
            List<Long> pendingIds = pendingChunkIds(docId);
            if (pendingIds.isEmpty())
            {
                markFailed(docId, "没有待向量化的切片");
                return;
            }
            context = contextStore.begin(docId, System.currentTimeMillis(), pendingIds);
        }
        setStatus(docId, 2);

        int batchSize = Math.max(1, intConfig("biz.ai.embedding.batch-size", DEFAULT_BATCH_SIZE));
        List<Long> ids = context.getChunkIds();
        boolean aborted = false;
        for (int from = 0; from < ids.size() && !aborted; from += batchSize)
        {
            // 续期：长时间索引期间锁不应过期（Important 4）
            lock.expire(LOCK_MINUTES, TimeUnit.MINUTES);
            List<Long> batchIds = ids.subList(from, Math.min(from + batchSize, ids.size()));
            List<KbChunk> batch = kbChunkMapper.selectBatchIds(batchIds);
            batch.removeIf(item -> Integer.valueOf(1).equals(item.getVectorStatus()));
            if (batch.isEmpty())
            {
                contextStore.touch(docId);
                continue;
            }
            aborted = !embedAndUpsertBatch(doc, context, batch);
        }

        int total = ids.size();
        long ok = countByStatus(ids, 1);
        long failed = countByStatus(ids, 2);
        updateCounters(docId, total, (int) ok, (int) failed);
        if (!aborted && failed == 0)
        {
            activate(doc, context, (int) ok);
        }
        else
        {
            markFailed(docId, truncate("向量化失败 " + failed + "/" + total));
        }
    }

    /**
     * 单批嵌入 + upsert；返回是否成功（失败时该批置 2 并中止后续批次）。
     */
    private boolean embedAndUpsertBatch(KbDoc doc, IndexContext context, List<KbChunk> batch)
    {
        List<String> texts = batch.stream().map(KbChunk::getContent).toList();
        EmbeddingResult result;
        try
        {
            result = embeddingService.embedBatch(texts);
        }
        catch (AiUnavailableException e)
        {
            markBatchFailed(batch, truncate("AI 不可用：" + e.getMessage()));
            return false;
        }
        List<QdrantPoint> points = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++)
        {
            points.add(new QdrantPoint(batch.get(i).getVectorId(), result.getVectors().get(i),
                    payload(doc, batch.get(i), context.getGen(), false)));
        }
        try
        {
            qdrantClient.upsert(points);
        }
        catch (QdrantException e)
        {
            markBatchFailed(batch, truncate("向量库不可用：" + e.getMessage()));
            return false;
        }
        List<Long> okIds = batch.stream().map(KbChunk::getChunkId).toList();
        kbChunkMapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                .in(KbChunk::getChunkId, okIds)
                .set(KbChunk::getVectorStatus, 1)
                .set(KbChunk::getFailReason, ""));
        contextStore.touch(doc.getDocId());
        return true;
    }

    private void activate(KbDoc doc, IndexContext context, int ok)
    {
        long docId = doc.getDocId();
        try
        {
            qdrantClient.setPayloadActive(docId, context.getGen(), Integer.valueOf(1).equals(doc.getStatus()));
            qdrantClient.setPayloadActiveExceptGen(docId, context.getGen(), false);
        }
        catch (QdrantException e)
        {
            // 切换分两步，第二步失败会留下新旧两代同时 active；回滚新代可见性，避免错引用
            try
            {
                qdrantClient.setPayloadActive(docId, context.getGen(), false);
            }
            catch (QdrantException rollbackError)
            {
                log.warn("rollback new gen activation failed, docId={}", docId, rollbackError);
            }
            markFailed(docId, truncate("索引切换失败：" + e.getMessage()));
            return;
        }
        writeTxService.activate(docId, context.getChunkIds(), context.getChunkIds().size(), ok, 0);
        redissonClient.getBucket(AiRedisKeys.currentGen(docId)).set(context.getGen());
        try
        {
            qdrantClient.deleteByDocExceptGen(docId, context.getGen());
        }
        catch (QdrantException e)
        {
            log.warn("cleanup old qdrant points failed, docId={}", docId, e);
        }
        contextStore.clear(docId);
        log.info("kb index activated, docId={}, gen={}, chunks={}", docId, context.getGen(), ok);
    }

    private List<Long> pendingChunkIds(long docId)
    {
        return kbChunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                        .select(KbChunk::getChunkId)
                        .eq(KbChunk::getDocId, docId)
                        .in(KbChunk::getVectorStatus, 0, 2))
                .stream().map(KbChunk::getChunkId).toList();
    }

    private long countByStatus(List<Long> ids, int status)
    {
        if (ids.isEmpty())
        {
            return 0;
        }
        return kbChunkMapper.selectCount(new LambdaQueryWrapper<KbChunk>()
                .in(KbChunk::getChunkId, ids).eq(KbChunk::getVectorStatus, status));
    }

    private void markBatchFailed(List<KbChunk> batch, String reason)
    {
        List<Long> ids = batch.stream().map(KbChunk::getChunkId).toList();
        kbChunkMapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                .in(KbChunk::getChunkId, ids)
                .set(KbChunk::getVectorStatus, 2)
                .set(KbChunk::getFailReason, reason));
    }

    private void setStatus(long docId, int status)
    {
        KbDoc update = new KbDoc();
        update.setDocId(docId);
        update.setIndexStatus(status);
        update.setFailReason("");
        kbDocMapper.updateById(update);
    }

    private void updateCounters(long docId, int total, int ok, int failed)
    {
        KbDoc update = new KbDoc();
        update.setDocId(docId);
        update.setChunkTotal(total);
        update.setChunkOk(ok);
        update.setChunkFailed(failed);
        kbDocMapper.updateById(update);
    }

    private void markFailed(long docId, String reason)
    {
        KbDoc update = new KbDoc();
        update.setDocId(docId);
        update.setIndexStatus(4);
        update.setFailReason(reason);
        update.setIndexTime(null);
        kbDocMapper.updateById(update);
    }

    private Map<String, Object> payload(KbDoc doc, KbChunk chunk, long gen, boolean active)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("storeId", doc.getStoreId());
        payload.put("docId", doc.getDocId());
        payload.put("chunkId", chunk.getChunkId());
        payload.put("gen", gen);
        payload.put("active", active);
        return payload;
    }

    private String truncate(String text)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() <= 480 ? text : text.substring(0, 480);
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
