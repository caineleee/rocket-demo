package org.lee.rocket.train.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;

/**
 * XSS 安全全局过滤器
 *
 * 【迁移说明】
 * 原逻辑位置：common 模块的 XssFilter
 * 迁移原因：
 * 1. 统一入口：所有请求都经过 Gateway，在这里处理一次即可
 * 2. 避免重复：如果每个服务都处理 XSS，会导致重复转义
 * 3. 大厂做法：阿里、美团、字节都在 Gateway 层统一处理 XSS
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
 * 在 Gateway 层对请求参数进行 HTML 转义，将恶意脚本转换为安全的文本。
 * 例如：<script> 转换为 &lt;script&gt;
 *
 * 【执行顺序】
 * 在 CORS 之后、JWT 认证之前执行（order = -150）。
 * 原因：
 * - 必须在 CORS 之后：OPTIONS 预检请求不需要 XSS 过滤
 * - 必须在 JWT 认证之前：确保 Token 中的特殊字符不会被误转义
 *
 * 【过滤范围】
 * 只过滤请求参数（Query Parameter），不过滤请求头（Header）和请求体（Body）。
 * 原因：
 * - 请求头大部分由浏览器自动设置，用户无法直接篡改
 * - 请求体（如 JSON）需要在业务层做格式校验，不适合在 Gateway 层过滤
 * - 过滤 Header 和 Body 可能破坏功能（如 Authorization 中的 JWT Token 包含特殊字符）
 *
 * 【大厂做法】
 * - 阿里推荐使用 OWASP Java Encoder 库
 * - 美团在网关层统一做 XSS 过滤
 * - 字节跳动在前后端都做防御（前端转义 + 后端过滤）
 */
@Component
public class XssGlobalFilter implements GlobalFilter, Ordered {

    @SuppressWarnings("null")
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // ===== 1. 获取请求参数 =====
        // 只过滤 Query Parameter，不过滤 Header 和 Body
        // 例如：/search?q=<script>alert('xss')</script>
        // 参数 q 的值会被转义为 &lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;

        // ===== 2. 创建新的请求，替换参数 =====
        // 遍历所有参数，对值进行 HTML 转义
        java.net.URI originalUri = request.getURI();
        String query = originalUri.getQuery();

        ServerHttpRequest mutatedRequest;
        if (query != null && !query.isEmpty()) {
            // 解析并转义参数
            StringBuilder escapedQuery = new StringBuilder();
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                String key = keyValue[0];
                String value = keyValue.length > 1 ? keyValue[1] : "";

                // 对值进行 HTML 转义
                String escapedValue = HtmlUtils.htmlEscape(value);

                if (escapedQuery.length() > 0) {
                    escapedQuery.append("&");
                }
                escapedQuery.append(key).append("=").append(escapedValue);
            }

            // 构建新的 URI：scheme://host:port/path?escapedQuery
            String baseUri = originalUri.getScheme() + "://" + originalUri.getHost()
                    + (originalUri.getPort() > 0 ? ":" + originalUri.getPort() : "")
                    + originalUri.getPath();
            java.net.URI escapedUri = java.net.URI.create(baseUri + "?" + escapedQuery);

            mutatedRequest = request.mutate().uri(escapedUri).build();
        } else {
            // 没有查询参数，无需转义
            mutatedRequest = request;
        }

        System.out.println("[XssGlobalFilter] 请求参数已转义");

        // ===== 3. 放行请求 =====
        // 使用转义后的请求继续执行后续 Filter
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // XSS 过滤器应该在 CORS 之后、JWT 认证之前执行
        return -150;
    }
}
