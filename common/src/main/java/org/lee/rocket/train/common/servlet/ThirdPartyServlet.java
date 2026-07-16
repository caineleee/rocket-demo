package org.lee.rocket.train.common.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 模拟第三方原生 Servlet（演示如何集成外部 Servlet）
 *
 * 【场景说明】
 * 假设你从 GitHub 下载了一个开源项目，里面有一个类继承了 HttpServlet。
 * 这个类不是你写的，你不能修改它的代码，但你需要把它集成到你的 Spring Boot 项目中。
 *
 * 【典型场景】
 * 1. H2 数据库控制台：org.h2.server.web.JakartaWebServlet
 * 2. Swagger UI：io.swagger.v3.oas.integration.ui.SwaggerUIServlet
 * 3. 监控面板：com.codahale.metrics.servlets.ThreadDumpServlet
 * 4. 老旧系统的 Servlet 迁移到 Spring Boot
 *
 * 【集成方式】
 * 使用 ServletRegistrationBean 将这个 Servlet 包装并注册到 Spring Boot 中。
 * 具体注册方式见 ServletConfig.java。
 *
 * 【与自定义 Servlet 的区别】
 * - 自定义 Servlet（如 HealthServlet）：你自己写的，可以随意修改
 * - 第三方 Servlet：别人写的，你只能配置 URL 映射和初始化参数，不能改代码
 *
 * 【本类的设计】
 * 为了演示，这个类模拟一个"第三方 Servlet"，它有自己的逻辑，不依赖 Spring。
 * 实际项目中，这个类可能来自第三方 JAR 包，你甚至看不到源码。
 */
public class ThirdPartyServlet extends HttpServlet {

    /**
     * Servlet 初始化方法
     *
     * 【第三方 Servlet 的初始化】
     * 第三方 Servlet 可能在 init() 中加载自己的配置、连接自己的数据库等。
     * 你无法控制这些逻辑，只能通过 ServletRegistrationBean 传递初始化参数。
     *
     * 【初始化参数的传递】
     * 在 ServletConfig.java 中：
     * ServletRegistrationBean registration = new ServletRegistrationBean();
     * registration.addInitParameter("configFile", "/path/to/config.properties");
     * registration.addInitParameter("debugMode", "true");
     *
     * 然后在这个 Servlet 中通过 getServletConfig().getInitParameter("configFile") 获取。
     */
    @Override
    public void init() throws ServletException {
        super.init();

        // 读取初始化参数（如果配置了的话）
        String configFile = getServletConfig().getInitParameter("configFile");
        String debugMode = getServletConfig().getInitParameter("debugMode");

        System.out.println("[ThirdPartyServlet] init() 被调用");
        System.out.println("[ThirdPartyServlet] 初始化参数 configFile: " + configFile);
        System.out.println("[ThirdPartyServlet] 初始化参数 debugMode: " + debugMode);

        // 模拟第三方 Servlet 的初始化逻辑
        // 比如：加载配置文件、初始化连接池、预热缓存等
        System.out.println("[ThirdPartyServlet] 第三方 Servlet 初始化完成（模拟）");
    }

    /**
     * 处理 GET 请求
     *
     * 【第三方 Servlet 的行为】
     * 这个 Servlet 有自己的业务逻辑，与你的 Spring MVC 完全独立。
     * 它不经过 DispatcherServlet，不经过 Spring 的拦截器，直接处理请求。
     *
     * 【典型场景】
     * - H2 控制台：显示数据库管理界面
     * - Swagger UI：显示 API 文档
     * - 监控 Servlet：显示线程 dump、内存使用情况等
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        System.out.println("[ThirdPartyServlet] 处理第三方 Servlet 请求");

        // 设置响应头
        response.setContentType("text/html;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);

        // 构建 HTML 响应（模拟第三方 Servlet 的界面）
        try (PrintWriter writer = response.getWriter()) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head><title>第三方 Servlet 演示</title></head>");
            writer.println("<body>");
            writer.println("<h1>这是一个第三方 Servlet</h1>");
            writer.println("<p>这个 Servlet 是从外部集成的，不经过 Spring MVC。</p>");
            writer.println("<p>它有自己的业务逻辑，与你的 Controller 完全独立。</p>");
            writer.println("<hr>");
            writer.println("<h3>请求信息：</h3>");
            writer.println("<ul>");
            writer.println("<li>请求 URI: " + request.getRequestURI() + "</li>");
            writer.println("<li>请求方法: " + request.getMethod() + "</li>");
            writer.println("<li>客户端 IP: " + request.getRemoteAddr() + "</li>");
            writer.println("</ul>");
            writer.println("<hr>");
            writer.println("<p><strong>注意：</strong>这个 Servlet 不经过 Spring 的拦截器（如 JWT 拦截器）。</p>");
            writer.println("<p>如果需要鉴权，必须在 Servlet 内部自己实现，或者在 Filter 层处理。</p>");
            writer.println("</body>");
            writer.println("</html>");
            writer.flush();
        }
    }

    /**
     * Servlet 销毁方法
     *
     * 【第三方 Servlet 的销毁】
     * 第三方 Servlet 可能在 destroy() 中释放自己的资源（如关闭连接池、保存状态等）。
     * 你无法控制这些逻辑，只能确保容器关闭时 destroy() 会被调用。
     */
    @Override
    public void destroy() {
        super.destroy();
        System.out.println("[ThirdPartyServlet] destroy() 被调用 —— 第三方 Servlet 即将被销毁");

        // 模拟第三方 Servlet 的清理逻辑
        // 比如：关闭连接池、保存运行时状态、释放文件句柄等
        System.out.println("[ThirdPartyServlet] 第三方 Servlet 资源清理完成（模拟）");
    }
}
