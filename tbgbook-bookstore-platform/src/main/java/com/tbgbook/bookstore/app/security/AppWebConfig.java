package com.tbgbook.bookstore.app.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 注册会员端认证拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class AppWebConfig implements WebMvcConfigurer
{
    private final AppAuthInterceptor appAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(appAuthInterceptor).addPathPatterns("/app/**").order(0);
    }
}
