package org.lee.rocket.train.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.lee.rocket.train.common.constant.JwtConstants;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.common.service.TokenService;
import org.lee.rocket.train.common.util.JwtUtil;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * 拦截所有 HTTP 请求，验证 Token 的有效性
 * 验证通过后将用户 ID 存入 Request 属性，供后续 Controller 使用
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private TokenService tokenService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        // 非 Controller 请求（如静态资源、Swagger 文档等）直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1. 从请求头中获取 Authorization
        String authHeader = request.getHeader(JwtConstants.AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            writeErrorResponse(response, Result.error("未携带Token"));
            return false;
        }

        // 2. 提取 Token（去除 "Bearer " 前缀）
        String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());

        // 3. 验证 Token 签名是否正确
        if (!JwtUtil.validateToken(token)) {
            writeErrorResponse(response, Result.error("Token无效"));
            return false;
        }

        // 4. 检查 Token 是否已过期（JWT 自身的过期时间）
        if (JwtUtil.isTokenExpired(token)) {
            writeErrorResponse(response, Result.error("Token已过期"));
            return false;
        }

        // 5. 检查 Token 是否在黑名单中（用户已登出）
        if (tokenService.isBlacklisted(token)) {
            writeErrorResponse(response, Result.error("Token已失效，请重新登录"));
            return false;
        }

        // 6. 检查 Token 是否在 Redis 中存在（防止伪造 Token）
        if (!tokenService.isValidAccessToken(token)) {
            writeErrorResponse(response, Result.error("Token已失效"));
            return false;
        }

        // 7. 从 Token 中提取用户 ID
        Long userId = JwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            writeErrorResponse(response, Result.error("Token无效"));
            return false;
        }

        // 8. 刷新 Token 过期时间（滑动过期）
        // 用户每次请求时，将 Token 的过期时间重新设置为 2 小时
        tokenService.refreshAccessToken(userId, token);

        // 9. 将用户 ID 存入 Request 属性，供 Controller 使用
        request.setAttribute(JwtConstants.USER_ID_KEY, userId);

        return true;
    }

    /**
     * 写入错误响应
     * 统一返回 JSON 格式的错误信息
     *
     * @param response HTTP 响应对象
     * @param result   错误结果
     */
    private void writeErrorResponse(HttpServletResponse response, Result<?> result) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 设置 HTTP 状态码为 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // 设置响应类型为 JSON
        response.setCharacterEncoding("UTF-8");                   // 设置字符编码为 UTF-8
        response.getWriter().write(objectMapper.writeValueAsString(result)); // 写入 JSON 响应体
    }
}
