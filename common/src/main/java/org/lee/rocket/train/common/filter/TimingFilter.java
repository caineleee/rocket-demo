package org.lee.rocket.train.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.UUID;

/**
 * 接口耗时统计过滤器
 *
 * 【为什么需要接口耗时统计？】
 * 在生产环境中，接口性能是衡量系统健康度的重要指标。
 * 通过统计每个接口的处理耗时，可以：
 * 1. 发现慢接口，优化性能瓶颈
 * 2. 监控系统负载，及时发现异常
 * 3. 为容量规划提供数据支持
 * 4. 配合 APM 工具（如 SkyWalking、Zipkin）做链路追踪
 *
 * 【执行顺序】
 * 最后一个执行（order = 4）。
 * 原因：TimingFilter 需要包裹整个请求处理链路（包括其他 Filter 的耗时）。
 * 根据 Filter 的"洋葱模型"，最后注册的 Filter 最先包装请求、最后拆包，
 * 因此它能统计到所有 Filter + Servlet/Controller 的总耗时。
 *
 * 【洋葱模型图示】
 * 请求 → [Encoding] → [Cors] → [Xss] → [Timing] → 业务处理
 * 响应 ← [Encoding] ← [Cors] ← [Xss] ← [Timing] ← 业务处理
 *                          ↑
 *               Timing 包裹了所有 Filter + 业务的执行时间
 *
 * 【大厂做法】
 * - 阿里：使用 EagleEye 做分布式链路追踪，每个请求生成 TraceId
 * - 美团：使用 CAT（Central Application Tracking）做性能监控
 * - 字节跳动：使用自研的 Slardar 做全链路监控
 * - 共同点：结构化日志 + TraceId + 慢请求告警
 *
 * 【本过滤器的实现】
 * 1. 生成唯一的 TraceId（用于链路追踪）
 * 2. 记录请求开始时间
 * 3. 调用 chain.doFilter() 执行业务逻辑
 * 4. 计算耗时（当前时间 - 开始时间）
 * 5. 输出结构化日志 + 响应头
 */
@Slf4j
public class TimingFilter implements Filter {

    /**
     * 慢请求阈值（毫秒）
     * 超过这个时间的请求会被标记为"慢请求"，以 WARN 级别打印日志
     *
     * 【大厂做法】
     * - 阿里：通常设置为 500ms 或 1000ms
     * - 美团：根据接口类型区分，查询接口 200ms，写入接口 500ms
     * - 本项目统一使用 1000ms 作为阈值
     */
    private static final long SLOW_REQUEST_THRESHOLD = 1000L;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("[TimingFilter] init() 被调用 —— 接口耗时统计过滤器初始化完成");
    }

    /**
     * 耗时统计核心逻辑
     *
     * 【实现原理】
     * 在 chain.doFilter() 前后记录时间戳，差值就是请求处理耗时。
     * 这个耗时包括了：
     * - 后续 Filter 的执行时间
     * - Servlet/Controller 的执行时间
     * - 数据库查询时间
     * - 网络传输时间（不含）
     *
     * 【结构化日志格式】
     * 使用 key=value 的格式，便于日志系统（如 ELK、Loki）解析和检索。
     * 例如：method=GET | path=/api/users | status=200 | cost=15ms | traceId=abc123
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // ===== 1. 生成 TraceId =====
        // TraceId 是分布式链路追踪的核心概念。
        // 每个请求生成一个唯一的 TraceId，贯穿整个调用链。
        // 在微服务架构中，TraceId 会通过 HTTP Header 传递给下游服务，
        // 这样可以将多个服务的日志串联起来，形成完整的调用链路。
        //
        // 【大厂做法】
        // - 通常从上游请求头中获取 TraceId（如果存在），否则生成新的
        // - 使用雪花算法或 UUID 生成唯一 ID
        // - 本项目简化处理，直接使用 UUID
        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 将 TraceId 放入响应头，方便前端/运维在浏览器 Network 面板查看
        httpResponse.setHeader("X-Trace-Id", traceId);

        // ===== 2. 记录请求开始时间 =====
        // System.currentTimeMillis() 返回当前时间的毫秒数
        // 精度足够用于接口耗时统计
        // 【更高精度】如果需要微秒级精度，可以使用 System.nanoTime()
        long startTime = System.currentTimeMillis();

        log.debug("[TimingFilter] 请求开始 | method={} | path={} | traceId={}",
                httpRequest.getMethod(), httpRequest.getRequestURI(), traceId);

        // ===== 3. 放行请求，执行业务逻辑 =====
        try {
            chain.doFilter(request, response);
        } finally {
            // ===== 4. 计算耗时 =====
            // 【重要】耗时计算必须放在 finally 块中
            // 确保即使业务逻辑抛出异常，也能记录耗时
            long endTime = System.currentTimeMillis();
            long cost = endTime - startTime;

            // 获取响应状态码
            int status = httpResponse.getStatus();

            // ===== 5. 输出结构化日志 =====
            // 根据耗时选择不同的日志级别：
            // - 正常请求（< 1000ms）：INFO 级别
            // - 慢请求（>= 1000ms）：WARN 级别，便于快速发现性能问题
            if (cost >= SLOW_REQUEST_THRESHOLD) {
                // 慢请求告警
                log.warn("[TimingFilter] [SLOW] 慢请求告警 | method={} | path={} | status={} | cost={}ms | traceId={} | 阈值={}ms",
                        httpRequest.getMethod(), httpRequest.getRequestURI(), status, cost, traceId, SLOW_REQUEST_THRESHOLD);
            } else {
                // 正常请求
                log.info("[TimingFilter] 请求完成 | method={} | path={} | status={} | cost={}ms | traceId={}",
                        httpRequest.getMethod(), httpRequest.getRequestURI(), status, cost, traceId);
            }

            // ===== 6. 将耗时添加到响应头 =====
            // 前端可以通过 response.headers.get('X-Request-Time') 获取耗时
            // 用于前端性能监控和调试
            httpResponse.setHeader("X-Request-Time", cost + "ms");
        }
    }

    @Override
    public void destroy() {
        log.info("[TimingFilter] destroy() 被调用 —— 接口耗时统计过滤器即将被销毁");
    }
}
