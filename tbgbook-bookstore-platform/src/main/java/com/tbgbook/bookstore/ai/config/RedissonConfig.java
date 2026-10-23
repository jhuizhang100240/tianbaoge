package com.tbgbook.bookstore.ai.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 手工装配（AI 域治理用：RRateLimiter 限流、RLock 索引锁、RBucket 索引上下文）。
 *
 * <p><b>刻意不用 {@code redisson-spring-boot-starter}</b>：starter 会用自己的自动配置
 * 接管 {@code RedisConnectionFactory}，与项目现有 Lettuce 配置（RuoYi {@code RedisConfig}）
 * 形成双客户端冗余与冲突面；核心包只多 netty 依赖，连接参数复用 {@code spring.data.redis.*}。
 *
 * @author tbgbook
 */
@Configuration
public class RedissonConfig
{
    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient()
    {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setTimeout(3000)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(8);
        if (StringUtils.hasText(password))
        {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
