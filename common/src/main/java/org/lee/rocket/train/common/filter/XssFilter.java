package org.lee.rocket.train.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * XSS 安全过滤器
 *
 * 【什么是 XSS 攻击？】
 * XSS（Cross-Site Scripting，跨站脚本攻击）是最常见的 Web 安全漏洞之一。
 * 攻击者通过在网页中注入恶意脚本，当其他用户浏览该页面时，脚本会在用户浏览器中执行。
 *
 * 【XSS 攻击的三种类型】
 * 1. 反射型 XSS：恶意脚本在 URL 参数中，服务器将参数直接返回给浏览器
 *    例如：https://example.com/search?q=<script>alert('xss')</script>
 * 2. 存储型 XSS：恶意脚本被保存到数据库，其他用户访问时从数据库读取并执行
 *    例如：评论区输入 <script>alert('xss')</script>，其他用户打开评论时执行
 * 3. DOM 型 XSS：恶意脚本通过修改页面 DOM 执行，不经过服务器
 *    例如：document.write(location.hash)
 *
 * 【本过滤器的防御方式】
 * 使用装饰器模式（Decorator Pattern），将原始的 HttpServletRequest 包装为
 * XssHttpServletRequestWrapper，在读取参数时自动进行 HTML 转义。
 *
 * 【执行顺序】
 * 第三个执行（order = 3），在 EncodingFilter 和 CorsFilter 之后。
 * 原因：
 * - 必须在 EncodingFilter 之后：确保参数编码正确，转义逻辑基于正确的字符
 * - 必须在 CorsFilter 之后：OPTIONS 预检请求不需要 XSS 过滤
 *
 * 【过滤范围】
 * 只过滤请求参数（getParameter/getParameterValues/getParameterMap）
 * 不过滤请求头（Header）：
 * - 大部分 Header 由浏览器自动设置，用户无法直接篡改
 * - 自定义 Header 应在业务层做格式校验
 * - 过滤 Header 可能破坏功能（如 Authorization 中的 JWT Token 包含特殊字符）
 *
 * 【大厂做法】
 * - 阿里推荐使用 OWASP Java Encoder 库
 * - 美团在网关层统一做 XSS 过滤
 * - 字节跳动在前后端都做防御（前端转义 + 后端过滤）
 */
public class XssFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("[XssFilter] init() 被调用 —— XSS 安全过滤器初始化完成");
    }

    /**
     * XSS 过滤核心逻辑
     *
     * 【核心思路】
     * 将原始的 HttpServletRequest 包装为 XssHttpServletRequestWrapper，
     * 然后传递给后续的 Filter/Servlet。
     * 这样，后续代码调用 request.getParameter() 时，
     * 实际调用的是 XssHttpServletRequestWrapper 的 getParameter()，
     * 返回的是转义后的值。
     *
     * 【装饰器模式的体现】
     * chain.doFilter(wrappedRequest, response);
     * 这里传递的是 wrappedRequest（包装后的请求），而不是原始请求。
     * 后续的 Filter/Servlet 拿到的就是包装后的请求，读取参数时自动转义。
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        System.out.println("[XssFilter] doFilter() 被调用 —— 包装请求对象，启用 XSS 过滤");
        System.out.println("[XssFilter] 请求 URI: " + httpRequest.getRequestURI());

        // ===== 核心：包装请求对象 =====
        // 将原始的 HttpServletRequest 包装为 XssHttpServletRequestWrapper
        // XssHttpServletRequestWrapper 重写了 getParameter() 等方法
        // 在返回参数值时自动进行 HTML 转义
        XssHttpServletRequestWrapper wrappedRequest = new XssHttpServletRequestWrapper(httpRequest);

        // ===== 放行请求 =====
        // 【关键】传递 wrappedRequest 而不是原始 request
        // 这样后续的 Filter/Servlet 拿到的就是包装后的请求
        // 它们调用 request.getParameter() 时，会自动触发 XSS 转义
        chain.doFilter(wrappedRequest, response);

        System.out.println("[XssFilter] 请求处理完成");
    }

    @Override
    public void destroy() {
        System.out.println("[XssFilter] destroy() 被调用 —— XSS 安全过滤器即将被销毁");
    }
}
