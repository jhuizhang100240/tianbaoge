package com.tbgbook.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;

/**
 * MyBatis-Plus 配置
 *
 * 注意：本项目分页统一使用 PageHelper（见需求 §6.3），
 * 因此此处**不注册** PaginationInnerInterceptor。
 * 由此带来一个约束：IPage / selectPage / selectMapsPage 不可用
 * ——未注册分页插件时它们不会分页，会静默返回全表且 total 为 0。
 *
 * @author tbgbook
 */
@Configuration
public class MybatisPlusConfig
{
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor()
    {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁，支撑 biz_stock.version 等 @Version 字段（需求 §4.2 规则 3）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
