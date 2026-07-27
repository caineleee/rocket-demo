package org.lee.rocket.train.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * CORS 跨域全局过滤器
 *
 * 【迁移说明】
 * 原逻辑位置：common 模块的 CorsFilter
 * 迁移原因：
 * 1. 统一入口：所有请求都经过 Gateway，在这里处理一次即可
 * 2. 避免重复：如果每个服务都处理 CORS，会导致重复设置响应头
 * 3. 大厂做法：阿里、美团、字节都在 Gateway 层统一处理 CORS
 *
 * 【什么是 CORS？】
 * CORS（Cross-Origin Resource Sharing，跨域资源共享）是浏览器的安全机制。
 * 当前端（如 http://localhost:3000）请求后端（如 http://localhost:8080）时，
 * 浏览器会先发一个 OPTIONS 预检请求，询问服务器是否允许跨域。
 * 如果服务器返回正确的 CORS 响应头，浏览器才会发送实际请求。
 *
 * 【执行顺序】
 * 在 JWT 认证之前执行（order = -200），因为 OPTIONS 预检请求不需要 JWT Token。
 *
 * 【关键响应头】
 * - Access-Control-Allow-Origin：允许哪些域名访问
 * - Access-Control-Allow-Methods：允许哪些 HTTP 方法
 * - Access-Control-Allow-Headers：允许哪些请求头
 * - Access-Control-Max-Age：预检请求的缓存时间（秒）
 */
@Component
public class CorsGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // ===== 1. 设置 CORS 响应头 =====

        // Access-Control-Allow-Origin: 允许访问的域名
        // * 表示允许所有域名（开发环境）
        // 【生产环境】应该设置为具体的域名，如 "https://www.example.com"
        response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

        // Access-Control-Allow-Methods: 允许的 HTTP 方法
        response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                "GET, POST, PUT, DELETE, OPTIONS");

        // Access-Control-Allow-Headers: 允许的请求头
        // 【重要】必须包含前端实际使用的所有请求头
        // Authorization 是 JWT Token，Content-Type 是 POST JSON 时浏览器自动添加的
        response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                "Content-Type, Authorization, X-Requested-With");

        // Access-Control-Allow-Credentials: 是否允许携带凭证（Cookie、HTTP 认证信息）
        // 【注意】如果设置为 true，Access-Control-Allow-Origin 不能为 *，必须指定具体域名
        // 本项目使用 JWT（放在 Header 中），不需要 Cookie，所以设置为 false
        response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "false");

        // Access-Control-Max-Age: 预检请求的缓存时间（秒）
        // 设置为 3600 秒（1 小时），意味着 1 小时内相同的跨域请求不需要再发 OPTIONS 预检
        // 【大厂做法】通常设置为 86400（24 小时）甚至更大，减少 OPTIONS 请求次数
        response.getHeaders().add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

        // ===== 2. 处理 OPTIONS 预检请求 =====
        // OPTIONS 请求只需要返回 CORS 响应头，不需要执行业务逻辑
        // 直接返回 200 OK，不调用 chain.filter()
        if (request.getMethod() == HttpMethod.OPTIONS) {
            response.setStatusCode(HttpStatus.OK);
            System.out.println("[CorsGlobalFilter] OPTIONS 预检请求，直接返回（不继续执行后续 Filter）");
            return response.setComplete();
        }

        // ===== 3. 非 OPTIONS 请求，放行 =====
        // 实际请求（GET/POST/PUT/DELETE 等）继续执行后续 Filter
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // CORS 过滤器应该在 JWT 认证之前执行
        // 因为 OPTIONS 预检请求不需要 JWT Token
        return -200;
    }
}
