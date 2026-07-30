package org.lee.rocket.train.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 字符编码过滤器（统一设置 UTF-8 编码）
 *
 * 【为什么需要这个过滤器？】
 * 在 Web 应用中，如果请求和响应的字符编码不一致，会导致中文乱码。
 * 例如：前端发送 "用户名=张三"，后端接收到 "用户名=å¼ ä¸‰"。
 * 
 * 【解决方案】
 * 在所有请求到达业务逻辑之前，统一设置字符编码为 UTF-8。
 * 这样无论前端使用什么编码，后端都能正确解析。
 *
 * 【执行顺序】
 * 必须是第一个执行的 Filter（order = 1）。
 * 原因：如果其他 Filter（如 XSS Filter）先读取了请求参数，而那时编码还未设置，
 * 那么读取到的参数已经是乱码了，后续再设置编码也无济于事。
 *
 * 【大厂做法】
 * 阿里、美团等大厂通常在 Spring Boot 配置中直接设置：
 * spring.http.encoding.charset=UTF-8
 * spring.http.encoding.enabled=true
 * spring.http.encoding.force=true
 * 
 * 但学习项目中，手动实现 Filter 有助于理解原理。
 *
 * 【注册方式】
 * 通过 FilterConfig.java 中的 FilterRegistrationBean 注册，URL 模式为 /*（所有请求）
 */
public class EncodingFilter implements Filter {

    /**
     * 过滤器初始化方法
     *
     * 【执行时机】容器启动时，Filter 第一次被加载时调用，且只调用一次。
     * 【典型用途】
     *   - 读取配置参数
     *   - 初始化资源（如数据库连接、缓存等）
     *
     * 【注意】如果 init() 抛出异常，Filter 将不会被加载，容器启动会失败。
     *
     * @param filterConfig 过滤器配置对象，可以获取在 web.xml 或 FilterRegistrationBean 中配置的初始化参数
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[EncodingFilter] init() 被调用 —— 字符编码过滤器初始化完成");
        
        // 读取初始化参数（如果配置了的话）
        // 例如：在 FilterRegistrationBean 中设置 registration.addInitParameter("encoding", "UTF-8");
        String encoding = filterConfig.getInitParameter("encoding");
        if (encoding != null) {
            System.out.println("[EncodingFilter] 配置的编码: " + encoding);
        } else {
            System.out.println("[EncodingFilter] 使用默认编码: UTF-8");
        }
    }

    /**
     * 过滤器核心逻辑
     *
     * 【执行时机】每次请求都会执行。
     * 【核心逻辑】
     * 1. 设置请求编码为 UTF-8（影响 request.getParameter() 的解析）
     * 2. 设置响应编码为 UTF-8（影响 response.getWriter() 的输出）
     * 3. 调用 chain.doFilter() 放行请求，继续执行下一个 Filter 或 Servlet
     *
     * 【重要概念】
     * - request.setCharacterEncoding("UTF-8")：设置请求体的编码，只影响 POST/PUT 等带有请求体的请求
     * - response.setCharacterEncoding("UTF-8")：设置响应体的编码
     * - response.setContentType("text/html;charset=UTF-8")：同时设置 Content-Type 和编码
     *
     * 【为什么必须在 chain.doFilter() 之前设置？】
     * 因为一旦调用了 request.getParameter() 或 response.getWriter()，编码就被固定了，后续再设置无效。
     * 所以必须在请求参数被读取之前设置编码。
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param chain    过滤器链对象，用于调用下一个过滤器或目标 Servlet
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        System.out.println("[EncodingFilter] doFilter() 被调用 —— 设置字符编码");

        // 将 ServletRequest 转换为 HttpServletRequest
        // 因为只有 HttpServletRequest 才有 setCharacterEncoding() 方法
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // ===== 1. 设置请求编码 =====
        // 【作用】设置请求体的字符编码为 UTF-8
        // 【影响范围】只影响 POST/PUT 等带有请求体的请求
        // 【不影响】URL 参数（?name=张三）的编码，URL 参数的编码由 Tomcat 的 server.xml 配置决定
        //
        // 【为什么需要这个？】
        // 当浏览器发送 POST 请求时，请求体中的中文字符需要正确解码。
        // 如果不设置，Tomcat 默认使用 ISO-8859-1 编码，导致中文乱码。
        httpRequest.setCharacterEncoding("UTF-8");

        // ===== 2. 设置响应编码 =====
        // 只设置字符编码，不强制设置 Content-Type。
        //
        // 【为什么不能 setContentType("text/html;charset=UTF-8")？】
        // REST API 服务的响应体是 JSON，不是 HTML。
        // 如果在 Filter 中强制设置 Content-Type 为 text/html，Spring MVC 的
        // RequestResponseBodyMethodProcessor 在写入 @ResponseBody 返回值时，
        // 会发现 Content-Type 已经是 text/html，而 Jackson 的
        // MappingJackson2HttpMessageConverter 只处理 application/json，
        // 导致 HttpMessageNotWritableException: No converter for [class Result]
        // with preset Content-Type 'text/html;charset=UTF-8'，接口直接 500。
        //
        // 正确做法：只调用 setCharacterEncoding("UTF-8") 设置编码，
        // 让 Spring 的内容协商机制根据 Accept 请求头 / produces 属性
        // 自动选择 application/json;charset=UTF-8 作为响应 Content-Type。
        httpResponse.setCharacterEncoding("UTF-8");

        // ===== 3. 放行请求 =====
        // 【关键】必须调用 chain.doFilter()，否则请求会被拦截，后续的 Filter 和 Servlet 都不会执行
        //
        // 【执行流程】
        // 请求 → EncodingFilter.doFilter() → chain.doFilter() → 下一个 Filter/Servlet
        // 响应 ← EncodingFilter.doFilter() ← chain.doFilter() ← 下一个 Filter/Servlet
        //
        // 【注意】chain.doFilter() 之后的代码会在响应返回时执行（类似 AOP 的后置通知）
        chain.doFilter(request, response);

        // 这里的代码会在响应返回时执行
        System.out.println("[EncodingFilter] 请求处理完成，响应已返回");
    }

    /**
     * 过滤器销毁方法
     *
     * 【执行时机】容器关闭时，Filter 被卸载前调用，且只调用一次。
     * 【典型用途】
     *   - 释放资源（如关闭数据库连接、文件句柄等）
     *   - 保存运行时状态
     *
     * 【注意】destroy() 执行时，所有正在处理的请求已经完成。
     * 所以不需要担心请求中断的问题。
     */
    @Override
    public void destroy() {
        System.out.println("[EncodingFilter] destroy() 被调用 —— 字符编码过滤器即将被销毁");
    }
}
