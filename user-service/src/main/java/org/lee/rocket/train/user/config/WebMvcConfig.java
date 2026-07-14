package org.lee.rocket.train.user.config;

import jakarta.annotation.Resource;
import org.lee.rocket.train.common.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 用户服务 Web MVC 配置类
 * 配置 JWT 认证拦截器，设置白名单（不需要鉴权的接口）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 拦截所有以 /user-service/ 开头的请求
                .addPathPatterns("/user-service/**")
                // 白名单：以下接口不需要鉴权
                .excludePathPatterns(
                        "/user-service/user/login",   // 用户登录
                        "/user-service/user/refresh"  // 刷新 Token
                );
    }
}
