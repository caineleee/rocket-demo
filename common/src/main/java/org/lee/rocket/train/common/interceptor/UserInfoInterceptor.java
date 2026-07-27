package org.lee.rocket.train.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lee.rocket.train.common.context.UserContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户信息拦截器
 * 从 Gateway 传递的请求头中读取用户信息，存入 UserContext（ThreadLocal）
 *
 * 【执行时机】
 * 在 Controller 方法执行前（preHandle）设置用户信息
 * 在请求完成后（afterCompletion）清理 ThreadLocal
 *
 * 【与 JwtInterceptor 的区别】
 * - JwtInterceptor：解析 JWT Token、验证签名、检查黑名单（已迁移到 Gateway）
 * - UserInfoInterceptor：只负责从请求头读取用户信息，不做任何验证
 *
 * 【使用方式】
 * 在各微服务的 WebMvcConfig 中注册此拦截器
 *
 * @author rocket-demo
 * @since 1.0.0
 */
@Component
public class UserInfoInterceptor implements HandlerInterceptor {

    /**
     * 请求头：用户 ID（由 Gateway 传递）
     */
    private static final String HEADER_USER_ID = "X-User-Id";

    /**
     * 请求头：用户名（由 Gateway 传递）
     */
    private static final String HEADER_USER_NAME = "X-User-Name";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // 非 Controller 请求（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 从请求头中获取 Gateway 传递的用户信息
        String userId = request.getHeader(HEADER_USER_ID);
        String userName = request.getHeader(HEADER_USER_NAME);

        // 存入 UserContext（ThreadLocal）
        if (userId != null && !userId.isEmpty()) {
            UserContext.setUserId(Long.parseLong(userId));
        }
        if (userName != null && !userName.isEmpty()) {
            UserContext.setUserName(userName);
        }

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @org.springframework.lang.Nullable Exception ex) {
        // 请求完成后必须清理 ThreadLocal，防止线程复用导致数据串线
        UserContext.clear();
    }
}
