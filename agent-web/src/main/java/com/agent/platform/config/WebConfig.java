package com.agent.platform.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册鉴权拦截器
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/sys/auth/login", // 登录接口放行
                        "/api/portal/**",      // 已发布应用对外访问（无需登录）
                        "/api/publish/channels/*/callback", // 渠道回调放行（微信/飞书/钉钉等）
                        "/error"           // 错误页放行
                );
    }
}
