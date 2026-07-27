package org.lee.rocket.train.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lee.rocket.train.common.constant.JwtConstants;
import org.lee.rocket.train.common.util.JwtUtil;
import org.lee.rocket.train.common.result.Result;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证全局过滤器
 *
 * 【作用】
 * 在 Gateway 层统一验证 JWT Token，避免每个微服务重复实现认证逻辑。
 *
 * 【执行流程】
 * 1. 检查请求路径是否在白名单中（如登录、注册、商品列表等）
 * 2. 如果在白名单，直接放行
 * 3. 如果不在白名单，从请求头中获取 Token
 * 4. 验证 Token 签名和过期时间
 * 5. 检查 Token 是否在 Redis 黑名单中
 * 6. 验证通过，将用户信息放入请求头，传递给下游服务
 * 7. 验证失败，返回 401 未授权
 *
 * 【注意事项】
 * - Gateway 基于 WebFlux，不能用 Servlet Filter，必须用 GlobalFilter
 * - Redis 操作要用 ReactiveStringRedisTemplate（响应式）
 * - 不能用 JwtInterceptor（那是 Servlet 体系的）
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * AntPath 路径匹配器
     * 支持 AntPath 模式匹配，如 /goods/**、/coupon/*/detail 等
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 白名单路径（不需要认证的接口）
     *
     * 【说明】
     * 这些接口允许匿名访问，不需要 JWT Token。
     * 包括：登录、注册、商品列表、优惠券列表等公开接口。
     */
    private static final List<String> WHITE_LIST = List.of(
            "/user/login",
            "/user/register",
            "/goods/list",
            "/coupon/list"
    );

    public JwtAuthGlobalFilter(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ===== 1. 检查是否在白名单中 =====
        if (isWhiteListed(path)) {
            // 白名单接口，直接放行
            return chain.filter(exchange);
        }

        // ===== 2. 从请求头中获取 Token =====
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            // 没有 Token 或格式不正确
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());

        // ===== 3. 验证 Token 签名和过期时间 =====
        String userId;
        String userName;
        try {
            userId = JwtUtil.getUserIdFromToken(token);
            userName = JwtUtil.getUserNameFromToken(token);
            if (userId == null) {
                return unauthorizedResponse(exchange, "Invalid token");
            }
        } catch (Exception e) {
            return unauthorizedResponse(exchange, "Token verification failed: " + e.getMessage());
        }

        // ===== 4. 检查 Token 是否在 Redis 黑名单中 =====
        String blacklistKey = JwtConstants.REDIS_BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(blacklistKey)
                .flatMap(isBlacklisted -> {
                    if (Boolean.TRUE.equals(isBlacklisted)) {
                        // Token 已被加入黑名单（用户已登出）
                        return unauthorizedResponse(exchange, "Token has been blacklisted");
                    }

                    // ===== 5. 验证通过，将用户信息放入请求头 =====
                    // 下游服务可以通过请求头获取用户信息，不需要再次解析 JWT
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Name", userName != null ? userName : "")
                            .header("X-Token", token)
                            .build();

                    // 继续执行过滤器链中下一个过滤器，传递修改后的请求头
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    @Override
    public int getOrder() {
        // 过滤器执行顺序（数字越小越先执行）
        // JWT 认证应该在 CORS 之后、业务逻辑之前执行
        return -100;
    }

    /**
     * 检查路径是否在白名单中
     * 使用 AntPathMatcher 支持 AntPath 模式匹配（如 /goods/**、/user/*/detail 等）
     *
     * @param path 请求路径
     * @return true 表示在白名单中，false 表示不在
     */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 返回 401 未授权响应
     */
    /**
     * 返回 401 未授权响应
     * @param exchange 服务器Web交换对象，包含请求和响应信息
     * @param message 错误提示信息
     * @return Mono<Void> 响应式编程中的空返回，表示响应已完成
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        // 1. 从 exchange 中获取 ServerHttpResponse 对象，用于构建HTTP响应
        ServerHttpResponse response = exchange.getResponse();
        
        // 2. 设置HTTP状态码为 401 Unauthorized，表示未授权访问
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        
        // 3. 设置响应头的 Content-Type 为 application/json，告知客户端响应体是JSON格式
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 4. 创建统一的错误结果对象，包含状态码和错误信息
        Result<?> result = Result.error(HttpStatus.UNAUTHORIZED.value(), message);

        try {
            // 5. 使用 ObjectMapper 将 Result 对象序列化为 JSON 字符串
            String json = objectMapper.writeValueAsString(result);
            
            // 6. 将 JSON 字符串转换为 UTF-8 编码的字节数组
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            
            // 7. 使用响应的 bufferFactory 创建 DataBuffer，包装字节数组
            // DataBuffer 是 WebFlux 中用于处理二进制数据的抽象
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            
            // 8. 将 DataBuffer 写入响应体，并返回 Mono<Void> 表示响应写入完成
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            // 9. 如果 JSON 序列化失败（理论上不会发生），直接完成响应，不返回任何内容
            return response.setComplete();
        }
    }
}
