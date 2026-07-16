package org.lee.rocket.train.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 全局跨域过滤器（CORS - Cross-Origin Resource Sharing）
 *
 * 【什么是跨域？】
 * 浏览器的"同源策略"规定：一个网页只能请求相同协议、相同域名、相同端口的资源。
 * 例如：http://localhost:3000 的页面请求 http://localhost:8080 的接口，就是跨域请求。
 * 浏览器会先发一个 OPTIONS 预检请求（Preflight），询问服务器是否允许跨域。
 * 如果服务器没有返回正确的 CORS 响应头，浏览器会阻止实际请求。
 *
 * 【为什么需要这个过滤器？】
 * 前后端分离项目中，前端（如 Vue/React）和后端（Spring Boot）通常运行在不同端口。
 * 如果不配置 CORS，前端无法调用后端接口。
 *
 * 【执行顺序】
 * 第二个执行（order = 2），在 EncodingFilter 之后。
 * 原因：跨域请求的 OPTIONS 预检请求需要立即返回，不应被后续 Filter 处理。
 *
 * 【大厂做法】
 * - Spring 提供了 @CrossOrigin 注解和 CorsConfiguration 配置类
 * - 但在微服务架构中，通常在网关层（如 Spring Cloud Gateway）统一处理 CORS
 * - 本项目在 common 层实现，便于理解原理
 *
 * 【关键响应头】
 * Access-Control-Allow-Origin:      允许哪些域名访问（* 表示所有）
 * Access-Control-Allow-Methods:     允许哪些 HTTP 方法
 * Access-Control-Allow-Headers:     允许哪些请求头
 * Access-Control-Allow-Credentials: 是否允许携带 Cookie
 * Access-Control-Max-Age:           预检请求的缓存时间（秒），减少 OPTIONS 请求次数
 *
 * 【安全考量】
 * 生产环境不应该使用 * 作为 Allow-Origin，应该配置具体的域名白名单。
 * 因为 * 意味着任何网站都可以调用你的接口，存在 CSRF 攻击风险。
 */
public class CorsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[CorsFilter] init() 被调用 —— 跨域过滤器初始化完成");
    }

    /**
     * 跨域过滤核心逻辑
     *
     * 【处理流程】
     * 1. 设置 CORS 响应头（所有请求都设置，包括预检和实际请求）
     * 2. 如果是 OPTIONS 预检请求，直接返回 200，不继续执行后续 Filter/Servlet
     * 3. 如果是实际请求（GET/POST 等），调用 chain.doFilter() 放行
     *
     * 【为什么 OPTIONS 请求要直接返回？】
     * OPTIONS 预检请求只是浏览器在问："服务器是否允许跨域？"
     * 它不需要执行业务逻辑，只需要返回 CORS 响应头即可。
     * 如果不拦截 OPTIONS，它会继续走到 Controller，但 Controller 通常不处理 OPTIONS，
     * 可能返回 405 Method Not Allowed，导致跨域失败。
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // ===== 1. 设置 CORS 响应头 =====

        // Access-Control-Allow-Origin: 允许访问的域名
        // * 表示允许所有域名（开发环境）
        // 【生产环境】应该设置为具体的域名，如 "https://www.example.com"
        // 或者从请求头中获取 Origin，判断是否在白名单中
        httpResponse.setHeader("Access-Control-Allow-Origin", "*");

        // Access-Control-Allow-Methods: 允许的 HTTP 方法
        // 多个方法用逗号分隔
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");

        // Access-Control-Allow-Headers: 允许的请求头
        // 【重要】必须包含前端实际使用的所有请求头
        // 例如：前端发送 Authorization 头（JWT Token），这里必须允许
        // Content-Type 是 POST JSON 时浏览器自动添加的，也必须允许
        httpResponse.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Requested-With");

        // Access-Control-Allow-Credentials: 是否允许携带凭证（Cookie、HTTP 认证信息）
        // 【注意】如果设置为 true，Access-Control-Allow-Origin 不能为 *，必须指定具体域名
        // 本项目使用 JWT（放在 Header 中），不需要 Cookie，所以设置为 false
        httpResponse.setHeader("Access-Control-Allow-Credentials", "false");

        // Access-Control-Max-Age: 预检请求的缓存时间（秒）
        // 设置为 3600 秒（1 小时），意味着 1 小时内相同的跨域请求不需要再发 OPTIONS 预检
        // 【大厂做法】通常设置为 86400（24 小时）甚至更大，减少 OPTIONS 请求次数
        httpResponse.setHeader("Access-Control-Max-Age", "3600");

        // ===== 2. 处理 OPTIONS 预检请求 =====
        // OPTIONS 请求只需要返回 CORS 响应头，不需要执行业务逻辑
        // 直接返回 200 OK，不调用 chain.doFilter()
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            System.out.println("[CorsFilter] OPTIONS 预检请求，直接返回（不继续执行后续 Filter/Servlet）");
            return; // 直接返回，不调用 chain.doFilter()
        }

        // ===== 3. 非 OPTIONS 请求，放行 =====
        // 实际请求（GET/POST/PUT/DELETE 等）继续执行后续 Filter 或 Servlet
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        System.out.println("[CorsFilter] destroy() 被调用 —— 跨域过滤器即将被销毁");
    }
}
