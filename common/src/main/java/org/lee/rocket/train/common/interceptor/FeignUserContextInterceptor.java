package org.lee.rocket.train.common.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.lee.rocket.train.common.context.UserContext;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器 - 自动传递用户上下文
 * 
 * 【作用】
 * 当微服务 A 通过 OpenFeign 调用微服务 B 时，自动将当前用户信息（userId、userName）
 * 从 UserContext（ThreadLocal）中读取，并添加到 Feign 请求头中。
 * 
 * 【执行流程】
 * 1. 用户请求 → Gateway → order-service（UserInfoInterceptor 设置 UserContext）
 * 2. order-service 调用 coupon-service（FeignUserContextInterceptor 读取 UserContext）
 * 3. Feign 请求头携带 X-User-Id、X-User-Name
 * 4. coupon-service 接收请求（UserInfoInterceptor 再次设置 UserContext）
 * 【注意事项】
 * 1. UserContext 必须在当前线程中存在（异步调用需要手动传递）
 * 2. 被调用方服务通过 UserInfoInterceptor 自动从请求头读取用户信息
 * 
 * @see org.lee.rocket.train.common.context.UserContext
 * @see org.lee.rocket.train.common.interceptor.UserInfoInterceptor
 */
@Component
public class FeignUserContextInterceptor implements RequestInterceptor {
    
    @Override
    public void apply(RequestTemplate template) {
        // 从 UserContext 中获取当前用户信息
        Long userId = UserContext.getUserId();
        String userName = UserContext.getUserName();
        
        // 如果用户信息存在，添加到请求头中
        if (userId != null) {
            template.header("X-User-Id", String.valueOf(userId));
        }
        if (userName != null) {
            template.header("X-User-Name", userName);
        }
    }
}
