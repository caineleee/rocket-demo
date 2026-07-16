package org.lee.rocket.train.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * XSS 请求包装类（装饰器模式）
 *
 * 【什么是装饰器模式？】
 * 装饰器模式（Decorator Pattern）是一种结构型设计模式。
 * 它允许在不修改原始对象的情况下，动态地给对象添加新的功能。
 * 核心思想：创建一个包装类（Wrapper），持有原始对象的引用，并实现相同的接口。
 *
 * 【为什么需要包装 HttpServletRequest？】
 * XSS（Cross-Site Scripting，跨站脚本攻击）是指攻击者在网页中注入恶意脚本。
 * 例如：用户在评论框中输入 <script>alert('hacked')</script>，
 * 如果不做过滤，这段代码会被保存到数据库，其他用户打开页面时就会执行这段脚本。
 *
 * 【防御思路】
 * 在请求参数到达业务逻辑之前，对所有参数值进行 HTML 转义：
 * - < → &lt;
 * - > → &gt;
 * - " → &quot;
 * - ' → &#x27;
 * - & → &amp;
 *
 * 这样即使攻击者输入了 <script>，也会被转义为 &lt;script&gt;，浏览器会把它当作普通文本显示。
 *
 * 【实现方式】
 * 继承 HttpServletRequestWrapper（它实现了 HttpServletRequest 接口），
 * 重写 getParameter()、getParameterValues()、getParameterMap() 等方法，
 * 在返回值中做 HTML 转义。
 *
 * 【为什么不直接修改请求参数？】
 * HttpServletRequest 的参数是由 Servlet 容器（Tomcat）解析的，你无法修改。
 * 只能通过包装类"拦截"参数的读取，返回转义后的值。
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 构造函数
     *
     * @param request 原始的 HttpServletRequest 对象
     */
    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    /**
     * 重写 getParameter() 方法
     *
     * 【原始行为】返回指定参数的值（未转义）
     * 【重写后】返回转义后的参数值
     *
     * 【调用时机】
     * 当业务代码调用 request.getParameter("name") 时，
     * 实际调用的是这个方法，返回的是转义后的值。
     *
     * @param name 参数名
     * @return 转义后的参数值
     */
    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        if (value != null) {
            // 对参数值进行 HTML 转义
            return escapeXss(value);
        }
        return null;
    }

    /**
     * 重写 getParameterValues() 方法
     *
     * 【原始行为】返回指定参数的所有值（用于多选框等场景，如 checkbox）
     * 【重写后】返回转义后的所有值
     *
     * @param name 参数名
     * @return 转义后的参数值数组
     */
    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values != null) {
            String[] escapedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escapedValues[i] = values[i] != null ? escapeXss(values[i]) : null;
            }
            return escapedValues;
        }
        return null;
    }

    /**
     * 重写 getParameterMap() 方法
     *
     * 【原始行为】返回所有参数的 Map（参数名 → 参数值数组）
     * 【重写后】返回转义后的参数 Map
     *
     * @return 转义后的参数 Map
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> originalMap = super.getParameterMap();
        Map<String, String[]> escapedMap = new HashMap<>();

        for (Map.Entry<String, String[]> entry : originalMap.entrySet()) {
            String[] values = entry.getValue();
            if (values != null) {
                String[] escapedValues = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    escapedValues[i] = values[i] != null ? escapeXss(values[i]) : null;
                }
                escapedMap.put(entry.getKey(), escapedValues);
            } else {
                escapedMap.put(entry.getKey(), null);
            }
        }

        return escapedMap;
    }

    /**
     * XSS 转义方法
     *
     * 【转义规则】
     * 将 HTML 特殊字符替换为对应的 HTML 实体：
     * - < → &lt;    （防止注入 <script> 等标签）
     * - > → &gt;    （防止闭合标签）
     * - " → &quot;  （防止注入属性值中的引号）
     * - ' → &#x27;  （防止注入属性值中的单引号）
     * - & → &amp;   （防止注入 HTML 实体）
     *
     * 【为什么按这个顺序替换？】
     * & 必须最先替换，否则后续替换产生的 & 会被二次替换。
     * 例如：如果先替换 < 为 &lt;，再替换 & 为 &amp;，
     * 那么 &lt; 会变成 &amp;lt;，导致显示错误。
     *
     * 【大厂做法】
     * - 阿里推荐使用 OWASP Java Encoder 库，比手动转义更安全
     * - Spring Security 提供了 HtmlUtils.htmlEscape() 方法
     * - 本项目为了学习目的，手动实现转义逻辑
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeXss(String value) {
        if (value == null) {
            return null;
        }

        // 【重要】& 必须最先替换
        value = value.replace("&", "&amp;");
        value = value.replace("<", "&lt;");
        value = value.replace(">", "&gt;");
        value = value.replace("\"", "&quot;");
        value = value.replace("'", "&#x27;");

        return value;
    }
}
