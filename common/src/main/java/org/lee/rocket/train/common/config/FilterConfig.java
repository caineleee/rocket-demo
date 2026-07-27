package org.lee.rocket.train.common.config;

import jakarta.servlet.Filter;
import org.lee.rocket.train.common.filter.EncodingFilter;
import org.lee.rocket.train.common.filter.TimingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filter 注册配置类
 *
 * 【为什么需要这个配置类？】
 * 在 Spring Boot 中，Filter 有两种注册方式：
 * 1. 在 Filter 类上加 @Component 注解（简单但不灵活）
 * 2. 使用 FilterRegistrationBean 注册（灵活，可以控制 URL 映射和执行顺序）
 *
 * 【为什么不使用 @Component？】
 * 如果 Filter 类上加 @Component，Spring Boot 会自动注册这个 Filter，并映射到 /*（所有请求）。
 * 但这样无法控制 Filter 的执行顺序，也无法精确控制 URL 映射。
 *
 * 【FilterRegistrationBean 的优势】
 * 1. 可以精确控制 URL 映射（哪些请求需要这个 Filter）
 * 2. 可以控制执行顺序（setOrder()）
 * 3. 可以传递初始化参数（addInitParameter()）
 * 4. 可以排除某些 URL（setUrlPatterns() 配合 excludeUrlPatterns()）
 *
 * 【当前执行顺序】
 * Filter 的执行顺序由 setOrder() 方法决定，数字越小优先级越高。
 * 迁移后本项目的 Filter 执行顺序：
 * 1. EncodingFilter（order=1）：设置字符编码，必须最先执行
 * 2. TimingFilter（order=2）：接口耗时统计，包裹整个请求链路
 *
 * 【URL 映射】
 * 所有 Filter 都映射到 /*，表示拦截所有请求。
 * 如果需要排除某些 URL，可以使用 setUrlPatterns() 指定具体的 URL 模式。
 */
@Configuration
public class FilterConfig {

    /**
     * 注册字符编码过滤器
     *
     * 【执行顺序】order=1（第一个执行）
     * 【原因】必须在所有 Filter 之前设置字符编码，否则后续 Filter 读取到的参数可能乱码
     *
     * 【URL 映射】/*（所有请求）
     *
     * @return FilterRegistrationBean 对象
     */
    @Bean
    public FilterRegistrationBean<Filter> encodingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        
        // 设置 Filter 实例
        registration.setFilter(new EncodingFilter());
        
        // 设置 URL 映射
        // /* 表示拦截所有请求
        registration.addUrlPatterns("/*");
        
        // 设置 Filter 名称
        registration.setName("encodingFilter");
        
        // 设置执行顺序
        // 数字越小优先级越高，EncodingFilter 必须第一个执行
        registration.setOrder(1);
        
        System.out.println("[FilterConfig] 注册 EncodingFilter，order=1，URL: /*");
        
        return registration;
    }

    /**
     * 【已迁移到 Gateway】
     * 跨域过滤器已迁移到 gateway-service 的 CorsGlobalFilter
     * 原逻辑：处理跨域请求，OPTIONS 预检请求直接返回
     * 迁移后：Gateway 统一处理，微服务不再需要注册 CorsFilter
     *
     * @see org.lee.rocket.train.gateway.filter.CorsGlobalFilter
     */
    // @Bean
    // public FilterRegistrationBean<Filter> corsFilterRegistration() {
    //     FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    //     
    //     registration.setFilter(new CorsFilter());
    //     registration.addUrlPatterns("/*");
    //     registration.setName("corsFilter");
    //     registration.setOrder(2);
    //     
    //     System.out.println("[FilterConfig] 注册 CorsFilter，order=2，URL: /*");
    //     
    //     return registration;
    // }

    /**
     * 【已迁移到 Gateway】
     * XSS 安全过滤器已迁移到 gateway-service 的 XssGlobalFilter
     * 原逻辑：XSS 安全过滤，在编码设置之后执行
     * 迁移后：Gateway 统一处理，微服务不再需要注册 XssFilter
     *
     * @see org.lee.rocket.train.gateway.filter.XssGlobalFilter
     */
    // @Bean
    // public FilterRegistrationBean<Filter> xssFilterRegistration() {
    //     FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    //     
    //     registration.setFilter(new XssFilter());
    //     registration.addUrlPatterns("/*");
    //     registration.setName("xssFilter");
    //     registration.setOrder(3);
    //     
    //     System.out.println("[FilterConfig] 注册 XssFilter，order=3，URL: /*");
    //     
    //     return registration;
    // }

    /**
     * 注册接口耗时统计过滤器
     *
     * 【执行顺序】order=2（第二个执行，迁移后调整）
     * 【原因】需要包裹整个请求链路，统计所有 Filter + Servlet/Controller 的总耗时
     *
     * 【URL 映射】/*（所有请求）
     *
     * @return FilterRegistrationBean 对象
     */
    @Bean
    public FilterRegistrationBean<Filter> timingFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        
        registration.setFilter(new TimingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("timingFilter");
        registration.setOrder(2);  // 迁移后调整为 order=2（原来是 order=4）
        
        System.out.println("[FilterConfig] 注册 TimingFilter，order=2，URL: /*");
        
        return registration;
    }
}
