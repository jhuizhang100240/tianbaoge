package com.tbgbook.bookstore.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 会员端配置。
 *
 * @author tbgbook
 */
@Data
@Component
@ConfigurationProperties(prefix = "tbgbook.app")
public class AppProperties
{
    /** 会员 JWT 有效期（秒），默认 30 分钟 */
    private int tokenExpireSeconds = 1800;

    private Wechat wechat = new Wechat();

    @Data
    public static class Wechat
    {
        /** dev/test 允许 mock 登录；生产必须关闭 */
        private boolean mockEnabled = true;
    }
}
