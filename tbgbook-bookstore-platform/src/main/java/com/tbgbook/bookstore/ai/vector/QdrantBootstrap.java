package com.tbgbook.bookstore.ai.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tbgbook.bookstore.ai.config.AiProperties;

/**
 * 启动期确保 Qdrant 集合存在。
 *
 * <p><b>失败只 warn，不阻断启动</b>：AI 不可用不得影响主交易链路（FR-4.7 规则 3）；
 * 后续每次索引/检索前由 AiGuard 再次判定（Task 10）。
 *
 * @author tbgbook
 */
@Component
public class QdrantBootstrap implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(QdrantBootstrap.class);

    private final AiProperties properties;

    private final QdrantClient client;

    public QdrantBootstrap(AiProperties properties, QdrantClient client)
    {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        AiProperties.Qdrant qdrant = properties.getQdrant();
        if (!StringUtils.hasText(qdrant.getBaseUrl()))
        {
            log.warn("qdrant base-url is empty; AI vector features will degrade");
            return;
        }
        try
        {
            client.ensureCollection();
            log.info("qdrant collection ready: {}", qdrant.getCollection());
        }
        catch (Exception e)
        {
            log.warn("qdrant collection not ready (AI features will degrade): {}", e.getMessage());
        }
    }
}
