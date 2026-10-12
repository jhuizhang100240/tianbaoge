package com.tbgbook.bookstore.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 域配置（{@code tbgbook.ai.*}）。
 *
 * <p>只承载**环境级**配置（地址、超时、集合名）；可运营调整的阈值/开关一律走
 * {@code sys_config}（见根 {@code CLAUDE.md} §6.2 与 AI 域设计稿 §4.3），不得写在这里。
 *
 * <p><b>api-key 为空不是错误</b>：开发环境允许无 Qdrant 鉴权；百炼 key 缺失时由
 * {@code AiGuard} 判定为「不可用」走降级矩阵，不得在启动期抛异常。
 *
 * @author tbgbook
 */
@Data
@Component
@ConfigurationProperties(prefix = "tbgbook.ai")
public class AiProperties
{
    /** Qdrant 连接配置 */
    private Qdrant qdrant = new Qdrant();

    /** 知识库文件存储配置 */
    private Kb kb = new Kb();

    @Data
    public static class Kb
    {
        /**
         * 非空 = 原始文档落本机目录（开发环境无 OSS 凭据时用，url 形如 {@code local://kb/...}）；
         * 为空 = 走 OSS（生产/测试）。
         */
        private String storageRoot = "";
    }

    @Data
    public static class Qdrant
    {
        /** Qdrant REST 地址，如 http://192.168.100.168:6333 */
        private String baseUrl = "";

        /** 生产必填（QDRANT__SERVICE__API_KEY）；开发为空 */
        private String apiKey = "";

        /** 知识库切片集合名 */
        private String collection = "tbgbook_kb_chunks";

        /** 单次 REST 请求超时（毫秒） */
        private int timeoutMs = 3000;
    }
}
