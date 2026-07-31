package org.lee.rocket.train.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.constant.JwtConstants;
import org.lee.rocket.train.common.util.JwtUtil;
import org.lee.rocket.train.common.model.Result;
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
@Slf4j
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * AntPath 路径匹配器，支持通配符路径匹配（如 goods 多级路径、coupon 单级路径）。
     *
     * 【踩坑】Javadoc 注释里不要写字面量的通配路径（星号紧跟斜杠的形式），
     * 因为编译器会把"星号+斜杠"当成注释结束符，导致注释提前关闭、字段声明变成孤立代码而编译失败。
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
            "/user/refresh",
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

        // 【是否剥离 Authorization 头】
        // 默认剥离：避免下游服务持有可用登录凭证（被日志/链路追踪泄露）。
        // 但 /user/logout 例外：user-service 的 UserController.logout 需要读取 Access Token 才能将其加入
        // Redis 黑名单。若剥离，登出会"假成功"——返回 200 但 Token 仍可用（复测：登出后再请求 /user/info 仍 200）。
        // 因此登出接口保留 Authorization 供下游读取并拉黑。
        boolean stripAuthorization = !"/user/logout".equals(path);

        // ===== 2. 从请求头中获取 Token =====
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            // 没有 Token 或格式不正确
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());

        // ===== 3. 验证 Token 签名和过期时间 =====
        // 【优化】原 getUserIdFromToken + getUserNameFromToken 各解析一次 Token（签名验证+解析跑两遍），
        //        改为只调用一次 parseToken，直接从 Claims 取用户信息，减少一半 HMAC 计算与 JSON 解析
        Long userId;
        String userName;
        try {
            Claims claims = JwtUtil.parseToken(token);
            userId = claims.get(JwtConstants.USER_ID_KEY, Long.class);
            userName = claims.get(JwtConstants.USER_NAME_KEY, String.class);
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
                    // 【安全】移除原 X-Token 透传（下游 UserInfoInterceptor 不消费，却会被日志/链路追踪泄露完整 Token）；
                    //        剥离 Authorization 头，避免下游持有可用登录凭证。
                    //        注意：白名单接口（如 /user/refresh）不走此分支，其 Authorization 保留供下游读取。
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .headers(h -> {
                                h.set("X-User-Id", String.valueOf(userId));
                                h.set("X-User-Name", userName != null ? userName : "");
                                if (stripAuthorization) {
                                    h.remove(HttpHeaders.AUTHORIZATION);
                                }
                            })
                            .build();

                    // 继续执行过滤器链中下一个过滤器，传递修改后的请求头
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                // 【修复】原 Redis 不可用时错误直接传播 → 网关 500，所有非白名单请求失败（单点故障放大）。
                //        改为 fail-open：黑名单查询失败时放行（JWT 签名/过期已验证，风险仅限已登出 Token 短暂可用），
                //        并重新注入用户信息头，避免下游拿不到用户上下文。
                .onErrorResume(e -> {
                    log.warn("Redis 黑名单查询失败, fail-open 放行: {}", e.getMessage());
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .headers(h -> {
                                h.set("X-User-Id", String.valueOf(userId));
                                h.set("X-User-Name", userName != null ? userName : "");
                                if (stripAuthorization) {
                                    h.remove(HttpHeaders.AUTHORIZATION);
                                }
                            })
                            .build();
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
     * 使用 AntPathMatcher 支持 AntPath 模式匹配（如 /goods/**、/user/*）
     */
    @SuppressWarnings("null")
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
    @SuppressWarnings("null")
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
            @SuppressWarnings("null")
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            
            // 8. 将 DataBuffer 写入响应体，并返回 Mono<Void> 表示响应写入完成
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            // 9. 如果 JSON 序列化失败（理论上不会发生），直接完成响应，不返回任何内容
            return response.setComplete();
        }
    }
}
