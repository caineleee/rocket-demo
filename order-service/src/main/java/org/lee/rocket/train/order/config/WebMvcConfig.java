package org.lee.rocket.train.order.config;

import jakarta.annotation.Resource;
import org.lee.rocket.train.common.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 订单服务 Web MVC 配置类
 * 配置 JWT 认证拦截器，所有接口都需要鉴权（无白名单）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 拦截所有以 /order-service/ 开头的请求
                // 订单操作都需要用户登录后才能进行，无白名单
                .addPathPatterns("/order-service/**");
    }
}
