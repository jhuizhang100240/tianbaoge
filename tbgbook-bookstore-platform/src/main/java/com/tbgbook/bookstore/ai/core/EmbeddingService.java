package com.tbgbook.bookstore.ai.core;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tbgbook.system.service.ISysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 向量化服务（百炼 text-embedding-v4，经 dashscope starter 的 {@link EmbeddingModel}）。
 *
 * <p>批量按 {@code biz.ai.embedding.batch-size}（默认 10，供应商单请求上限）切批；
 * 单批对网络/5xx 做 ≤3 次退避重试；最终失败抛 {@link AiUnavailableException}。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class EmbeddingService
{
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private static final int DEFAULT_BATCH_SIZE = 10;

    private static final int MAX_ATTEMPTS = 3;

    private final EmbeddingModel embeddingModel;

    private final ISysConfigService configService;

    /**
     * 批量嵌入；返回顺序与入参一致。
     */
    public EmbeddingResult embedBatch(List<String> texts)
    {
        if (texts == null || texts.isEmpty())
        {
            return new EmbeddingResult(new ArrayList<>(), 0, "");
        }
        int batchSize = Math.max(1, intConfig("biz.ai.embedding.batch-size", DEFAULT_BATCH_SIZE));
        List<float[]> vectors = new ArrayList<>(texts.size());
        int promptTokens = 0;
        String model = "";
        for (int from = 0; from < texts.size(); from += batchSize)
        {
            List<String> batch = texts.subList(from, Math.min(from + batchSize, texts.size()));
            EmbeddingResponse response = callWithRetry(batch);
            for (Embedding embedding : response.getResults())
            {
                vectors.add(embedding.getOutput());
            }
            if (response.getMetadata() != null)
            {
                if (StringUtils.hasText(response.getMetadata().getModel()))
                {
                    model = response.getMetadata().getModel();
                }
                Usage usage = response.getMetadata().getUsage();
                if (usage != null && usage.getPromptTokens() != null)
                {
                    promptTokens += usage.getPromptTokens();
                }
            }
        }
        if (vectors.size() != texts.size())
        {
            throw new AiUnavailableException("向量化返回条数与入参不一致：" + vectors.size() + "/" + texts.size());
        }
        return new EmbeddingResult(vectors, promptTokens, model);
    }

    private EmbeddingResponse callWithRetry(List<String> batch)
    {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)
        {
            try
            {
                return embeddingModel.call(new EmbeddingRequest(batch, EmbeddingOptions.builder().build()));
            }
            catch (RuntimeException e)
            {
                last = e;
                log.warn("embedding attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS)
                {
                    try
                    {
                        Thread.sleep(500L * attempt);
                    }
                    catch (InterruptedException interrupted)
                    {
                        Thread.currentThread().interrupt();
                        throw new AiUnavailableException("向量化被中断", interrupted);
                    }
                }
            }
        }
        throw new AiUnavailableException("向量化调用失败：" + (last == null ? "unknown" : last.getMessage()), last);
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
