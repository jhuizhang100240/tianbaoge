package com.tbgbook.bookstore.book.isbn;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.book.domain.IsbnInfo;
import com.tbgbook.bookstore.book.support.IsbnUtils;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.StringUtils;

/**
 * NeoDB 书库适配器（G6 选定：免费、无密钥、中文图书覆盖可用）。
 *
 * <p>端点 {@code GET /api/catalog/search?query=<isbn>&category=book}，响应形如
 * {@code {"data":[{...}]}}，书目字段与 {@link IsbnInfo} 的映射见 {@link #toInfo}。
 *
 * <p><b>为什么不用平台的 {@code HttpUtils}</b>：它是 {@code URLConnection} 裸封装，
 * <b>不设任何超时</b>（只在 catch 里捕 {@code SocketTimeoutException}）。外部书目 API 一旦
 * 挂起就会**无限期占住 Tomcat 线程**，而需求 §7.2 明确要求超时与降级固化。故这里用
 * Spring 的 {@code RestClient} + JDK HttpClient（无需新增依赖，能真正设建连/读超时）。
 *
 * <p><b>降级</b>：失败**抛异常**，由 {@code IsbnLookupServiceImpl} 统一记「信息待补全」并放行 ——
 * FR-4.1.1 规则 1 要求调用失败不阻断建档。
 *
 * @author tbgbook
 */
@Component
public class NeoDbIsbnProvider implements IsbnProvider
{
    /** 供应商标识 */
    public static final String SOURCE = "neodb";

    private static final Logger log = LoggerFactory.getLogger(NeoDbIsbnProvider.class);

    /** 作者 / 译者等多值字段的连接符 */
    private static final String JOINER = "、";

    /** 从 {@code "CNY 89.00"} 这类文本里抓出数字 */
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final IsbnLookupProperties properties;

    private final ObjectMapper objectMapper;

    /** 已配好超时的客户端；端点为空白时为 {@code null}（调用时明确报错，不静默不发） */
    private final RestClient restClient;

    public NeoDbIsbnProvider(IsbnLookupProperties properties, ObjectMapper objectMapper)
    {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = buildClient();
    }

    @Override
    public String source()
    {
        return SOURCE;
    }

    @Override
    public Optional<IsbnInfo> lookup(String isbn)
    {
        requireConfigured();
        requireHostAllowed();

        // 重试：4xx 重试没有意义（请求本身不对），只对 5xx 与网络异常重试
        int attempts = Math.max(0, properties.getMaxRetries()) + 1;
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++)
        {
            try
            {
                String body = restClient.get()
                        .uri(builder -> builder.queryParam("query", isbn).queryParam("category", "book").build())
                        .retrieve()
                        .body(String.class);
                return parse(isbn, body);
            }
            catch (HttpClientErrorException e)
            {
                // 4xx：直接抛，不浪费重试次数
                throw e;
            }
            catch (RuntimeException e)
            {
                lastError = e;
                log.warn("NeoDB 书目查询第 {}/{} 次失败 isbn={} 原因={}", attempt, attempts, isbn, e.getMessage());
            }
        }
        throw lastError;
    }

    // ==================================================================
    // 内部：客户端与出网白名单
    // ==================================================================

    private RestClient buildClient()
    {
        if (StringUtils.isBlank(properties.getEndpoint()))
        {
            return null;
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.getEndpoint())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 端点未配置时不发起任何调用 */
    private void requireConfigured()
    {
        if (restClient == null)
        {
            throw BizErrors.serviceUnavailable("ISBN 书目查询未配置端点（isbn-lookup.endpoint）");
        }
    }

    /**
     * 出网白名单校验（需求 §7.2 部署要点 1：按域名白名单放行，不得整体放开出网）。
     *
     * <p>白名单本该在部署层（egress 规则）落实，这里是**应用层再兜一道** ——
     * 防的是配置被误改成任意域名后悄悄把出网口子开大。
     */
    private void requireHostAllowed()
    {
        String host;
        try
        {
            host = URI.create(properties.getEndpoint()).getHost();
        }
        catch (IllegalArgumentException e)
        {
            throw BizErrors.serviceUnavailable("ISBN 书目查询端点不是合法 URL：" + properties.getEndpoint());
        }
        // ⚠️ URI.create 对 "not-a-url" 这种**相对** URI 是成功的（只是没有 host），
        // 所以「取不到 host」要单独报，否则会得到「白名单未放行「null」」这种没法排查的文案
        if (host == null)
        {
            throw BizErrors.serviceUnavailable(
                    "ISBN 书目查询端点不是合法的绝对 URL（取不到主机名）：" + properties.getEndpoint());
        }
        boolean allowed = properties.getAllowedHosts().stream()
                .anyMatch(h -> h != null && h.equalsIgnoreCase(host));
        if (!allowed)
        {
            // 属基础设施配置问题（503），不是用户输入问题（4xx）
            throw BizErrors.serviceUnavailable(
                    "ISBN 书目查询的出网白名单未放行「" + host + "」，已拒绝调用");
        }
    }

    // ==================================================================
    // 内部：响应映射
    // ==================================================================

    private Optional<IsbnInfo> parse(String isbn, String body)
    {
        JsonNode root;
        try
        {
            root = objectMapper.readTree(body);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("NeoDB 响应不是合法 JSON", e);
        }
        JsonNode data = root.path("data");
        if (!data.isArray())
        {
            return Optional.empty();
        }
        for (JsonNode node : data)
        {
            // ⚠️ 只接受 ISBN 完全一致的命中。搜索接口会返回模糊结果（同名书、近似标题），
            //    取第一条会把**别的书**的作者与封面贴到这本书上 —— 比留空更糟
            if (!isbn.equals(IsbnUtils.normalize(text(node, "isbn"))))
            {
                continue;
            }
            return Optional.of(toInfo(isbn, node));
        }
        return Optional.empty();
    }

    private IsbnInfo toInfo(String isbn, JsonNode node)
    {
        IsbnInfo info = new IsbnInfo();
        info.setIsbn(isbn);
        info.setSource(SOURCE);
        info.setTitle(text(node, "title"));
        info.setSubtitle(text(node, "subtitle"));
        info.setAuthor(join(node.get("author")));
        info.setTranslator(join(node.get("translator")));
        info.setPublisherName(firstNonBlank(text(node, "pub_house"), join(node.get("publisher"))));
        info.setPublishDate(toPublishDate(node));
        // NeoDB 没有「版次」字段。imprint（如「图灵教育」）是**出版品牌**不是版次，不能挪用 ——
        // 留空由人工补，好过塞一个语义不对的值
        info.setEdition(null);
        info.setBinding(text(node, "binding"));
        info.setPageCount(toInt(node.get("pages")));
        info.setPrice(toPrice(text(node, "price")));
        info.setCoverUrl(text(node, "cover_image_url"));
        info.setSummary(firstNonBlank(text(node, "description"), text(node, "brief")));
        return info;
    }

    /**
     * 出版日期：NeoDB 只给 {@code pub_year} + {@code pub_month}，**没有日**。
     *
     * <p>这里补 {@code 01} 是**约定而不是真实数据** —— 比留空多保留了「哪个月出版」的信息，
     * 但读的人不该把它当成精确日期。两处都拿不到就返回 {@code null} 留空。
     */
    private LocalDate toPublishDate(JsonNode node)
    {
        Integer year = toInt(node.get("pub_year"));
        Integer month = toInt(node.get("pub_month"));
        if (year == null || year <= 0 || month == null || month < 1 || month > 12)
        {
            return null;
        }
        return LocalDate.of(year, month, 1);
    }

    /** 取文本字段；缺失、JSON null、空白串一律归一成 {@code null} */
    private String text(JsonNode node, String field)
    {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode())
        {
            return null;
        }
        String s = value.asText(null);
        return StringUtils.isBlank(s) ? null : s.trim();
    }

    /** 多值字段（author / translator / publisher 都是字符串数组）拼成一个串 */
    private String join(JsonNode arrayNode)
    {
        if (arrayNode == null || arrayNode.isNull())
        {
            return null;
        }
        if (!arrayNode.isArray())
        {
            // 少数条目的 author 是单值字符串而非数组，容错处理
            String single = arrayNode.asText(null);
            return StringUtils.isBlank(single) ? null : single.trim();
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : arrayNode)
        {
            String s = item.asText(null);
            if (StringUtils.isBlank(s))
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append(JOINER);
            }
            sb.append(s.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private Integer toInt(JsonNode value)
    {
        return (value == null || value.isNull() || !value.isNumber()) ? null : value.asInt();
    }

    /** 从 {@code "CNY 89.00"} 这类文本里取数字；取不到返回 {@code null} */
    private BigDecimal toPrice(String raw)
    {
        if (StringUtils.isBlank(raw))
        {
            return null;
        }
        Matcher matcher = NUMBER.matcher(raw);
        if (!matcher.find())
        {
            return null;
        }
        try
        {
            return new BigDecimal(matcher.group());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private String firstNonBlank(String first, String second)
    {
        return StringUtils.isBlank(first) ? second : first;
    }
}
