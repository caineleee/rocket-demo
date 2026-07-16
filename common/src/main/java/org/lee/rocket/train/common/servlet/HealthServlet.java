package org.lee.rocket.train.common.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 健康检查 Servlet（轻量级探活接口）
 *
 * 【为什么不用 Spring MVC Controller？】
 * 健康检查是负载均衡器（如 Nginx、K8s）每隔几秒调用一次的接口。
 * 如果走 Spring MVC 链路：请求 → DispatcherServlet → HandlerMapping → 参数解析 → 拦截器 → Controller
 * 这条链路涉及大量 Bean 查找、注解解析、AOP 代理调用，对于只返回 "OK" 的接口来说是浪费。
 * 原生 Servlet 直接处理请求，跳过 Spring MVC 的复杂链路，响应速度更快、资源消耗更低。
 *
 * 【在请求链路中的位置】
 * 客户端请求 → Filter 链 → HealthServlet（不经过 DispatcherServlet）
 * 注意：Filter 仍然会生效（如 EncodingFilter、TimingFilter），因为 Filter 在 Servlet 之前执行。
 *
 * 【注册方式】
 * 在 Spring Boot 中，原生 Servlet 必须通过 ServletRegistrationBean 注册。
 * 不能直接加 @Component，否则会被注册为 /* 拦截所有请求，导致 Spring MVC 失效。
 * 具体注册方式见 ServletConfig.java。
 *
 * 【生命周期】
 * - init()：容器启动时执行一次，用于初始化资源（如数据库连接、配置加载）
 * - service()：每次请求执行一次，处理具体的 HTTP 请求
 * - destroy()：容器关闭时执行一次，用于释放资源（如关闭连接池）
 */
public class HealthServlet extends HttpServlet {

    /**
     * 时间格式化器
     * 使用 static final 避免每次请求都创建新对象（线程安全，DateTimeFormatter 是不可变的）
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Servlet 初始化方法
     *
     * 【执行时机】容器启动时，Servlet 第一次被加载时调用，且只调用一次。
     * 【典型用途】
     *   - 加载配置文件
     *   - 初始化数据库连接池
     *   - 预热缓存数据
     *
     * 【注意】如果 init() 抛出异常，Servlet 将不会被加载，容器启动会失败。
     * 所以 init() 中的异常处理非常重要。
     */
    @Override
    public void init() throws ServletException {
        super.init();
        // 打印日志，方便观察 Servlet 生命周期
        // 在启动日志中看到这条，说明 HealthServlet 已成功初始化
        System.out.println("[HealthServlet] init() 被调用 —— Servlet 初始化完成（只执行一次）");
    }

    /**
     * 处理 GET 请求
     *
     * 【为什么重写 doGet() 而不是 service()？】
     * service() 方法内部会根据 HTTP 方法（GET/POST/PUT/DELETE）分发到对应的 doGet()/doPost() 等方法。
     * 直接重写 doGet() 更清晰，也符合 HttpServlet 的设计意图。
     * 如果你需要同时处理多种 HTTP 方法，可以重写 service() 或同时重写多个 doXxx() 方法。
     *
     * @param request  HTTP 请求对象，包含请求参数、Header 等信息
     * @param response HTTP 响应对象，用于设置状态码、响应头、响应体
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // 打印日志，观察每次请求的执行情况
        // 对比 init() 只执行一次，doGet() 每次请求都会执行
        System.out.println("[HealthServlet] doGet() 被调用 —— 处理健康检查请求");

        // ===== 设置响应头 =====

        // 设置响应内容类型为 JSON
        // 必须在获取 Writer 之前设置，否则可能不生效
        response.setContentType("application/json;charset=UTF-8");

        // 设置 HTTP 状态码为 200（OK）
        // 健康检查通常返回 200 表示服务正常，负载均衡器据此判断是否继续转发流量
        response.setStatus(HttpServletResponse.SC_OK);

        // ===== 构建响应体 =====

        // 获取当前时间
        String currentTime = LocalDateTime.now().format(FORMATTER);

        // 构建 JSON 响应体
        // 这里手动拼接 JSON，不引入 Jackson 等库
        // 原因：健康检查是最基础的接口，应尽量减少外部依赖，避免依赖故障导致健康检查本身失败
        String json = String.format(
                "{\"status\":\"OK\",\"time\":\"%s\",\"service\":\"rocket-demo\"}",
                currentTime
        );

        // ===== 写入响应 =====

        // 使用 PrintWriter 写入响应体
        // 【重要】try-with-resources 会自动关闭 Writer
        // 但在 Servlet 中，通常不需要手动关闭 response.getWriter() 返回的 Writer
        // 因为 Servlet 容器（Tomcat）会在请求处理完成后自动关闭
        // 这里使用 try-with-resources 是为了演示正确的流关闭习惯
        try (PrintWriter writer = response.getWriter()) {
            writer.write(json);
            // flush() 确保数据被发送到客户端
            // 虽然容器最终会 flush，但显式调用是好习惯
            writer.flush();
        }
    }

    /**
     * Servlet 销毁方法
     *
     * 【执行时机】容器关闭时，Servlet 被卸载前调用，且只调用一次。
     * 【典型用途】
     *   - 关闭数据库连接池
     *   - 释放文件句柄
     *   - 保存运行时状态到持久化存储
     *
     * 【注意】destroy() 执行时，所有正在处理的请求已经完成。
     * 所以不需要担心请求中断的问题。
     *
     * 【与 Spring 的 @PreDestroy 对比】
     * - destroy() 是 Servlet 规范的方法，只作用于当前 Servlet
     * - @PreDestroy 是 Spring 的方法，作用于 Spring 管理的 Bean
     * - 原生 Servlet 不是 Spring Bean，所以 @PreDestroy 不生效
     */
    @Override
    public void destroy() {
        super.destroy();
        // 打印日志，方便观察 Servlet 生命周期
        // 在关闭日志中看到这条，说明 HealthServlet 正在被销毁
        System.out.println("[HealthServlet] destroy() 被调用 —— Servlet 即将被销毁（只执行一次）");
    }
}
