package com.tbgbook.bookstore.ai.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tbgbook.bookstore.ai.domain.KbChunk;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.mapper.KbChunkMapper;
import com.tbgbook.bookstore.ai.mapper.KbDocMapper;
import com.tbgbook.bookstore.ai.vector.QdrantClient;
import com.tbgbook.bookstore.ai.vector.QdrantHit;
import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 知识库检索内核（AI 域设计稿 §5.3）。
 *
 * <p>流程：问题嵌入 → Qdrant 检索（过滤 storeId + active）→ 按 chunkId 回表取正文 →
 * 复核文档 {@code del_flag=0 && status=1}（Qdrant 同步失败时的最后防线）→ 按分数保序输出 →
 * best-effort 累加 {@code hit_count}（失败仅 warn，不影响回答）。
 *
 * <p>供应商不可用抛 {@link AiUnavailableException}，由场景层转降级；本类不做降级决策。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class RetrievalService
{
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private static final int STORE_ID = 1;

    private static final int DEFAULT_TOP_K = 8;

    private static final double DEFAULT_MIN_SCORE = 0.35D;

    private final EmbeddingService embeddingService;

    private final QdrantClient qdrantClient;

    private final KbChunkMapper kbChunkMapper;

    private final KbDocMapper kbDocMapper;

    private final ISysConfigService configService;

    /**
     * 按 sys_config 默认参数检索（top-k / min-score）。
     */
    public List<RetrievalHit> retrieve(String question)
    {
        return retrieve(question,
                intConfig("biz.ai.retrieval.top-k", DEFAULT_TOP_K),
                doubleConfig("biz.ai.retrieval.min-score", DEFAULT_MIN_SCORE));
    }

    /**
     * 检索：返回按分数降序、已回表复核的命中项。
     */
    public List<RetrievalHit> retrieve(String question, int topK, double minScore)
    {
        if (!StringUtils.hasText(question))
        {
            return new ArrayList<>();
        }
        float[] vector = embeddingService.embedBatch(List.of(question)).getVectors().get(0);
        List<QdrantHit> hits = qdrantClient.query(vector, Math.max(1, topK), minScore, STORE_ID);
        if (hits.isEmpty())
        {
            return new ArrayList<>();
        }

        List<Long> chunkIds = hits.stream()
                .map(hit -> asLong(hit.getPayload().get("chunkId")))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (chunkIds.isEmpty())
        {
            return new ArrayList<>();
        }
        Map<Long, KbChunk> chunkMap = kbChunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(KbChunk::getChunkId, chunk -> chunk));
        Set<Long> docIds = chunkMap.values().stream().map(KbChunk::getDocId).collect(Collectors.toSet());
        Map<Long, KbDoc> docMap = new LinkedHashMap<>();
        if (!docIds.isEmpty())
        {
            kbDocMapper.selectBatchIds(docIds).stream()
                    .filter(doc -> Integer.valueOf(1).equals(doc.getStatus()))
                    .forEach(doc -> docMap.put(doc.getDocId(), doc));
        }

        List<RetrievalHit> result = new ArrayList<>();
        for (QdrantHit hit : hits)
        {
            Long chunkId = asLong(hit.getPayload().get("chunkId"));
            KbChunk chunk = chunkId == null ? null : chunkMap.get(chunkId);
            if (chunk == null)
            {
                continue;
            }
            KbDoc doc = docMap.get(chunk.getDocId());
            if (doc == null)
            {
                continue;
            }
            result.add(new RetrievalHit(chunk.getChunkId(), doc.getDocId(), doc.getDocName(),
                    chunk.getSeqNo() == null ? 0 : chunk.getSeqNo(), chunk.getContent(), hit.getScore()));
        }
        incrementHitCount(result);
        return result;
    }

    private void incrementHitCount(List<RetrievalHit> hits)
    {
        if (hits.isEmpty())
        {
            return;
        }
        List<Long> ids = hits.stream().map(RetrievalHit::getChunkId).distinct().collect(Collectors.toList());
        try
        {
            kbChunkMapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                    .in(KbChunk::getChunkId, ids)
                    .setSql("hit_count = hit_count + 1"));
        }
        catch (Exception e)
        {
            log.warn("increment hit_count failed, chunkIds={}", ids, e);
        }
    }

    private Long asLong(Object value)
    {
        return value instanceof Number ? ((Number) value).longValue() : null;
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

    private double doubleConfig(String key, double defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (!StringUtils.hasText(raw))
        {
            return defaultValue;
        }
        try
        {
            return Double.parseDouble(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }
}
