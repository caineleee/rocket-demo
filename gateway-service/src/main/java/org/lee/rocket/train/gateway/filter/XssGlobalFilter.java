package org.lee.rocket.train.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
 * 【本过滤器的防御方式】
 * 对 query 参数值做 HTML 转义，将恶意脚本转换为安全文本（如 <script> → &lt;script&gt;）。
 *
 * 【执行顺序】
 * order = -150，在 CORS 之后、JWT 认证之前执行。
 * - CORS 之后：OPTIONS 预检请求不需要 XSS 过滤
 * - JWT 之前：确保 Token 中的特殊字符不会被误转义
 *
 * 【过滤范围】
 * 只过滤请求参数（Query Parameter），不过滤请求头和请求体。
 *
 * 【实现要点（修复 URI 破坏 bug）】
 * 原实现用 {@code getQuery()}（返回已解码值）直接 HTML 转义后拼回 URI，会导致：
 * 1. {@code htmlEscape(')} = {@code &#39;}，其中 # 是 URI fragment 分隔符，破坏 query 解析
 * 2. {@code htmlEscape(&)} = {@code &amp;}，& 是参数分隔符，把一个参数拆成两个
 * 3. 解码值直接拼回 URI 遇非法字符可能抛 IllegalArgumentException
 * 正确流程：原始 query（URL 编码）→ URL 解码 → HTML 转义 → URL 编码拼回，
 * 既保证 URI 结构合法，又把参数值里的特殊字符 HTML 转义掉。
 *
 * @author lihongliang
 */
@Slf4j
@Component
public class XssGlobalFilter implements GlobalFilter, Ordered {

    @SuppressWarnings("null")
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI originalUri = request.getURI();

        // 用 getRawQuery() 取原始（未解码）query，避免对解码值二次处理
        String rawQuery = originalUri.getRawQuery();

        ServerHttpRequest mutatedRequest;
        if (rawQuery != null && !rawQuery.isEmpty()) {
            String escapedQuery = escapeQuery(rawQuery);
            URI escapedUri = rebuildUri(originalUri, escapedQuery);
            mutatedRequest = request.mutate().uri(escapedUri).build();
            log.debug("XSS 过滤: {} -> {}", originalUri.getRawQuery(), escapedQuery);
        } else {
            // 没有查询参数，无需转义
            mutatedRequest = request;
        }

        // 使用转义后的请求继续执行后续 Filter
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 对 query string 做 XSS 转义
     * 流程：原始 query（URL 编码）→ URL 解码 → HTML 转义 → URL 编码拼回
     *
     * @param rawQuery 原始未解码的 query（如 "q=it%27s&page=1"）
     * @return 转义后重新编码的 query（如 "q=it%26%2339%3Bs&page=1"）
     */
    private String escapeQuery(String rawQuery) {
        StringBuilder sb = new StringBuilder();
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length > 1 ? urlDecode(kv[1]) : "";
            // HTML 转义（防 XSS）
            @SuppressWarnings("null")
            String escapedKey = HtmlUtils.htmlEscape(key);
            @SuppressWarnings("null")
            String escapedValue = HtmlUtils.htmlEscape(value);
            // URL 编码回拼，确保转义产生的 & # ; 等字符不破坏 URI 结构
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(escapedKey, StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(escapedValue, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * URL 解码，解码失败时回退原值（容错）
     */
    private String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 重建 URI：保留原 scheme/host/port/path，仅替换 query 部分
     */
    private URI rebuildUri(URI original, String query) {
        StringBuilder base = new StringBuilder();
        base.append(original.getScheme()).append("://").append(original.getHost());
        if (original.getPort() > 0) {
            base.append(":").append(original.getPort());
        }
        base.append(original.getPath());
        return URI.create(base.toString() + "?" + query);
    }

    @Override
    public int getOrder() {
        // XSS 过滤器应该在 CORS 之后、JWT 认证之前执行
        return -150;
    }
}
