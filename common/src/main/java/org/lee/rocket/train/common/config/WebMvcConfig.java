package org.lee.rocket.train.common.config;

import jakarta.annotation.Resource;
import org.lee.rocket.train.common.interceptor.UserInfoInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 通用 Web MVC 配置类
 * 统一为所有微服务注册 UserInfoInterceptor
 *
 * 【设计说明】
 * 将此配置放在 common 模块中，所有微服务通过 @ComponentScan 扫描到它，
 * 无需在每个微服务中重复编写 WebMvcConfig。
 *
 * 【条件加载】
 * 使用 @ConditionalOnWebApplication(type = SERVLET) 确保只在 Servlet Web 应用中加载。
 * Gateway 基于 WebFlux（Reactive Web），不会加载此配置，避免 ClassNotFoundException。
 *
 * 【职责】
 * 注册 UserInfoInterceptor，从 Gateway 传递的请求头中读取用户信息，存入 UserContext（ThreadLocal）
 *
 * @see org.lee.rocket.train.common.interceptor.UserInfoInterceptor
 * @see org.lee.rocket.train.common.context.UserContext
 */
@Configuration
// gateway 响应式 Web 应用，不加载此配置，避免 ClassNotFoundException
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private UserInfoInterceptor userInfoInterceptor;

    /**
     * 注册 UserInfoInterceptor
     * 拦截所有请求，从 Gateway 传递的请求头中读取用户信息，存入 UserContext
     */
    @SuppressWarnings("null")
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(userInfoInterceptor)
                .addPathPatterns("/**");  // 拦截所有请求
    }
}
