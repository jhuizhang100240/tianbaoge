package com.tbgbook.bookstore.book.isbn;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * ISBN 书目查询的外部接口配置（FR-4.1.1 规则 1 / 需求 §7.2 部署要点 1）。
 *
 * <p><b>为什么放在 {@code application.yml} 而不是 {@code sys_config}</b>：
 * 根 CLAUDE.md §6.2 要求「阈值进 {@code sys_config}} 的 {@code biz.*} 键」，但本轮**刻意不走**——
 * {@code sys_config} 是运营能在后台改的库表，而这里面装的是
 * <b>出网域名白名单与超时策略</b>。把出网白名单交给运营编辑，等于把需求 §7.2
 * 「不得整体放开出网」这条安全约束交出去。需求本身也写明「白名单域名与超时 / 重试 /
 * 降级策略**在部署文档中固化**」，与 OSS / 百炼 / 微信凭据的处理方式一致。
 *
 * @author tbgbook
 */
@Data
@Component
@ConfigurationProperties(prefix = "isbn-lookup")
public class IsbnLookupProperties
{
    /** 启用的供应商标识，须与某个 {@link IsbnProvider#source()} 一致 */
    private String provider = "neodb";

    /** 查询端点（完整 URL，查询串由实现自行拼接） */
    private String endpoint;

    /**
     * 出网域名白名单。
     *
     * <p>只有 {@link #endpoint} 的 host 落在此列表内才允许发起调用 ——
     * 应用层再兜一道，防的是「配置被误改成任意域名」把出网口子开大。
     */
    private List<String> allowedHosts = new ArrayList<>();

    /** 建连超时（毫秒） */
    private int connectTimeoutMs = 2000;

    /** 读超时（毫秒） */
    private int readTimeoutMs = 4000;

    /** 失败重试次数（不含首次调用） */
    private int maxRetries = 1;
}
