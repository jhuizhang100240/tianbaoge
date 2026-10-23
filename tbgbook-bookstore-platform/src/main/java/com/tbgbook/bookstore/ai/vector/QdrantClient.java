package com.tbgbook.bookstore.ai.vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.ai.config.AiProperties;

/**
 * Qdrant REST 客户端。
 *
 * <p><b>刻意不引 Qdrant Java SDK / gRPC</b>：本客户端只用 Qdrant v1.x REST 的最小协议子集
 * （建集合 / upsert / query / count / payload / delete），依赖面仅 spring-web 的 {@code RestClient}。
 *
 * <p>协议约定（AI 域设计稿 §4.2）：
 * <ul>
 *   <li>集合 {@code tbgbook_kb_chunks}：1024 维、Cosine；</li>
 *   <li>point id = UUID（即 {@code biz_kb_chunk.vector_id}）；</li>
 *   <li>payload = {@code storeId/docId/chunkId/gen/active}，正文不存 Qdrant。</li>
 * </ul>
 *
 * @author tbgbook
 */
@Component
public class QdrantClient
{
    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

    /** 响应体截断长度（防止把大段 HTML 错误页带进异常消息） */
    private static final int ERROR_BODY_LIMIT = 300;

    private final AiProperties properties;

    private final ObjectMapper objectMapper;

    private final RestClient restClient;

    public QdrantClient(AiProperties properties, ObjectMapper objectMapper)
    {
        this.properties = properties;
        this.objectMapper = objectMapper;

        AiProperties.Qdrant qdrant = properties.getQdrant();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(qdrant.getTimeoutMs());
        factory.setReadTimeout(qdrant.getTimeoutMs());

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (StringUtils.hasText(qdrant.getBaseUrl()))
        {
            builder.baseUrl(qdrant.getBaseUrl());
        }
        if (StringUtils.hasText(qdrant.getApiKey()))
        {
            builder.defaultHeader("api-key", qdrant.getApiKey());
        }
        this.restClient = builder.build();
    }

    /**
     * 确保集合存在（404 时创建 1024 维 Cosine 集合）。
     */
    public void ensureCollection()
    {
        String collection = collection();
        try
        {
            restClient.get().uri("/collections/{collection}", collection)
                    .retrieve().toBodilessEntity();
            log.info("qdrant collection exists: {}", collection);
        }
        catch (RestClientResponseException e)
        {
            if (e.getStatusCode().value() == 404)
            {
                Map<String, Object> body = Map.of(
                        "vectors", Map.of("size", 1024, "distance", "Cosine"));
                try
                {
                    restClient.put().uri("/collections/{collection}", collection)
                            .contentType(MediaType.APPLICATION_JSON).body(body)
                            .retrieve().toBodilessEntity();
                }
                catch (RestClientResponseException createError)
                {
                    throw toQdrantException("create collection", createError);
                }
                log.info("qdrant collection created: {}", collection);
            }
            else
            {
                throw toQdrantException("ensure collection", e);
            }
        }
    }

    /**
     * 批量 upsert（point id 为 UUID，重复写入幂等覆盖）。
     */
    public void upsert(List<QdrantPoint> points)
    {
        List<Map<String, Object>> payload = new ArrayList<>(points.size());
        for (QdrantPoint point : points)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", point.getId());
            item.put("vector", point.getVector());
            item.put("payload", point.getPayload());
            payload.add(item);
        }
        // Qdrant 的 upsert 是 PUT /points（POST /points 是「按 ids 取点」，误用会报 missing field `ids`）
        put("/collections/{collection}/points?wait=true", Map.of("points", payload), "upsert");
    }

    /**
     * 向量检索（过滤 storeId + active=true）。
     */
    public List<QdrantHit> query(float[] vector, int limit, double scoreThreshold, int storeId)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", vector);
        body.put("filter", Map.of("must", List.of(
                match("storeId", storeId), match("active", true))));
        body.put("limit", limit);
        body.put("score_threshold", scoreThreshold);
        body.put("with_payload", true);

        JsonNode root = postForJson("/collections/{collection}/points/query", body, "query");
        JsonNode result = root.path("result");
        // 兼容两种响应形态：统一 Query API（result.points）与旧 search API（result 数组）
        JsonNode points = result.has("points") ? result.path("points") : result;

        List<QdrantHit> hits = new ArrayList<>();
        if (points.isArray())
        {
            for (JsonNode point : points)
            {
                Map<String, Object> payload = objectMapper.convertValue(
                        point.path("payload"), new TypeReference<Map<String, Object>>() {});
                hits.add(new QdrantHit(point.path("id").asText(), point.path("score").asDouble(), payload));
            }
        }
        return hits;
    }

    /**
     * 按过滤条件统计点数（count 一律 exact，用于验收断言与索引计数核对）。
     */
    public long countByFilter(int storeId, Long docId, Boolean active)
    {
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(match("storeId", storeId));
        if (docId != null)
        {
            must.add(match("docId", docId));
        }
        if (active != null)
        {
            must.add(match("active", active));
        }
        Map<String, Object> body = Map.of("filter", Map.of("must", must), "exact", true);
        JsonNode root = postForJson("/collections/{collection}/points/count", body, "count");
        return root.path("result").path("count").asLong();
    }

    /**
     * 将某文档某一代次的点置为 active（索引切换的激活/停用步）。
     */
    public void setPayloadActive(long docId, long gen, boolean active)
    {
        setPayload(Map.of("must", List.of(match("docId", docId), match("gen", gen))),
                Map.of("active", active));
    }

    /**
     * 将某文档**除某代次外**的点置为 active=false（切换时下线旧代次）。
     */
    public void setPayloadActiveExceptGen(long docId, long gen, boolean active)
    {
        setPayload(Map.of("must", List.of(match("docId", docId)),
                        "must_not", List.of(match("gen", gen))),
                Map.of("active", active));
    }

    /**
     * 删除某文档全部点（文档删除时调用）。
     */
    public void deleteByDoc(long docId)
    {
        delete(Map.of("must", List.of(match("docId", docId))));
    }

    /**
     * 按 point id 批量删除（切片维护：单切片删除/内容修改时清理旧向量）。
     */
    public void deleteByPointIds(List<String> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return;
        }
        post("/collections/{collection}/points/delete?wait=true",
                Map.of("points", ids), "delete points by id");
    }

    /**
     * 按文档维度整体置 active（文档停用/启用时调用；启用侧由调用方结合当前代次判断）。
     */
    public void setPayloadActiveForDoc(long docId, boolean active)
    {
        setPayload(Map.of("must", List.of(match("docId", docId))), Map.of("active", active));
    }

    /**
     * 删除某文档除某代次外的全部点（切换完成后的尽力清理）。
     */
    public void deleteByDocExceptGen(long docId, long gen)
    {
        delete(Map.of("must", List.of(match("docId", docId)),
                "must_not", List.of(match("gen", gen))));
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private void setPayload(Map<String, Object> filter, Map<String, Object> payload)
    {
        post("/collections/{collection}/points/payload?wait=true",
                Map.of("payload", payload, "filter", filter), "set payload");
    }

    private void delete(Map<String, Object> filter)
    {
        post("/collections/{collection}/points/delete?wait=true",
                Map.of("filter", filter), "delete points");
    }

    private void post(String uri, Object body, String action)
    {
        try
        {
            restClient.post().uri(uri, collection())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        }
        catch (RestClientResponseException e)
        {
            throw toQdrantException(action, e);
        }
        catch (ResourceAccessException e)
        {
            throw toQdrantException(action, e);
        }
    }

    private void put(String uri, Object body, String action)
    {
        try
        {
            restClient.put().uri(uri, collection())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
        }
        catch (RestClientResponseException e)
        {
            throw toQdrantException(action, e);
        }
        catch (ResourceAccessException e)
        {
            throw toQdrantException(action, e);
        }
    }

    private JsonNode postForJson(String uri, Object body, String action)
    {
        try
        {
            JsonNode node = restClient.post().uri(uri, collection())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            return node == null ? objectMapper.createObjectNode() : node;
        }
        catch (RestClientResponseException e)
        {
            throw toQdrantException(action, e);
        }
        catch (ResourceAccessException e)
        {
            throw toQdrantException(action, e);
        }
    }

    private Map<String, Object> match(String key, Object value)
    {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    private QdrantException toQdrantException(String action, RestClientResponseException e)
    {
        String body = e.getResponseBodyAsString();
        if (body != null && body.length() > ERROR_BODY_LIMIT)
        {
            body = body.substring(0, ERROR_BODY_LIMIT) + "...";
        }
        return new QdrantException("qdrant " + action + " failed: HTTP "
                + e.getStatusCode().value() + " " + body, e);
    }

    /**
     * 连接失败 / 读超时等 IO 异常同样归一到 {@link QdrantException}，
     * 否则会以普通 RuntimeException 冒泡成 500，绕过 503 降级映射。
     */
    private QdrantException toQdrantException(String action, ResourceAccessException e)
    {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.length() > ERROR_BODY_LIMIT)
        {
            message = message.substring(0, ERROR_BODY_LIMIT) + "...";
        }
        return new QdrantException("qdrant " + action + " failed: " + message, e);
    }

    private String collection()
    {
        return properties.getQdrant().getCollection();
    }
}
