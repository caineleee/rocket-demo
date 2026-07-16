package org.lee.rocket.train.common.config;

import jakarta.servlet.Servlet;
import org.lee.rocket.train.common.servlet.FileDownloadServlet;
import org.lee.rocket.train.common.servlet.HealthServlet;
import org.lee.rocket.train.common.servlet.ThirdPartyServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet 注册配置类
 *
 * 【为什么需要这个配置类？】
 * 在 Spring Boot 中，原生 Servlet 不能直接加 @Component 注解。
 * 如果直接加 @Component，Spring Boot 会自动注册这个 Servlet，并映射到 /*（所有请求），
 * 这会导致 Spring MVC 的 DispatcherServlet 失效，所有请求都被这个 Servlet 拦截。
 *
 * 【正确的注册方式】
 * 使用 ServletRegistrationBean 来注册原生 Servlet，并精确指定 URL 映射。
 * 这样可以让原生 Servlet 和 Spring MVC 共存，各司其职。
 *
 * 【URL 映射规则】
 * - /health：健康检查接口，用于负载均衡器探活
 * - /download/*：文件下载接口，支持大文件流式下载
 * - /third-party/*：第三方 Servlet 接口，演示如何集成外部 Servlet
 *
 * 【初始化参数】
 * 可以通过 addInitParameter() 方法给 Servlet 传递初始化参数。
 * Servlet 内部通过 getServletConfig().getInitParameter() 获取这些参数。
 *
 * 【加载顺序】
 * 可以通过 setLoadOnStartup() 方法指定 Servlet 的加载顺序。
 * 正数表示启动时加载，数字越小优先级越高。
 * 负数或 0 表示延迟加载（第一次访问时才加载）。
 */
@Configuration
public class ServletConfig {

    /**
     * 注册健康检查 Servlet
     *
     * 【URL 映射】/health
     * 【用途】负载均衡器探活，返回 JSON 格式的健康状态
     *
     * @return ServletRegistrationBean 对象
     */
    @Bean
    public ServletRegistrationBean<Servlet> healthServletRegistration() {
        // 创建 ServletRegistrationBean，传入 Servlet 实例
        ServletRegistrationBean<Servlet> registration = new ServletRegistrationBean<>(new HealthServlet());
        
        // 设置 URL 映射
        // 只有访问 /health 时才会触发这个 Servlet
        registration.addUrlMappings("/health");
        
        // 设置 Servlet 名称（可选）
        registration.setName("healthServlet");
        
        // 设置启动顺序（可选）
        // 正数表示启动时加载，数字越小优先级越高
        registration.setLoadOnStartup(1);
        
        System.out.println("[ServletConfig] 注册 HealthServlet，URL: /health");
        
        return registration;
    }

    /**
     * 注册文件下载 Servlet
     *
     * 【URL 映射】/download/*
     * 【用途】大文件流式下载，支持边读边写，不占用大量内存
     *
     * 【URL 模式说明】
     * /download/* 表示匹配所有以 /download/ 开头的请求
     * 例如：
     * - /download/file1.txt
     * - /download/file2.zip
     * - /download/2024/report.pdf
     *
     * @return ServletRegistrationBean 对象
     */
    @Bean
    public ServletRegistrationBean<Servlet> fileDownloadServletRegistration() {
        ServletRegistrationBean<Servlet> registration = new ServletRegistrationBean<>(new FileDownloadServlet());
        
        // 设置 URL 映射
        // /* 表示通配符，匹配所有以 /download/ 开头的请求
        registration.addUrlMappings("/download/*");
        
        registration.setName("fileDownloadServlet");
        registration.setLoadOnStartup(2);
        
        System.out.println("[ServletConfig] 注册 FileDownloadServlet，URL: /download/*");
        
        return registration;
    }

    /**
     * 注册第三方 Servlet
     *
     * 【URL 映射】/third-party/*
     * 【用途】演示如何集成外部 Servlet（如 H2 控制台、Swagger UI 等）
     *
     * 【初始化参数】
     * 通过 addInitParameter() 传递初始化参数，模拟第三方 Servlet 的配置需求。
     * Servlet 内部通过 getServletConfig().getInitParameter() 获取这些参数。
     *
     * @return ServletRegistrationBean 对象
     */
    @Bean
    public ServletRegistrationBean<Servlet> thirdPartyServletRegistration() {
        ServletRegistrationBean<Servlet> registration = new ServletRegistrationBean<>(new ThirdPartyServlet());
        
        // 设置 URL 映射
        registration.addUrlMappings("/third-party/*");
        
        registration.setName("thirdPartyServlet");
        registration.setLoadOnStartup(3);
        
        // 添加初始化参数（模拟第三方 Servlet 的配置需求）
        registration.addInitParameter("configFile", "/config/third-party.properties");
        registration.addInitParameter("debugMode", "true");
        
        System.out.println("[ServletConfig] 注册 ThirdPartyServlet，URL: /third-party/*");
        System.out.println("[ServletConfig] 初始化参数: configFile=/config/third-party.properties, debugMode=true");
        
        return registration;
    }
}
