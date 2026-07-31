package org.lee.rocket.train.common.config;

import java.util.EventListener;
import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.listener.AppStartupListener;
import org.lee.rocket.train.common.listener.SessionListener;
import org.lee.rocket.train.common.listener.ShutdownListener;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Listener 注册配置类
 *
 * 【为什么需要这个配置类？】
 * 在 Spring Boot 中，Listener 有两种注册方式：
 * 1. 在 Listener 类上加 @Component 注解（简单但不灵活）
 * 2. 使用 ServletListenerRegistrationBean 注册（灵活，可以控制注册顺序）
 *
 * 【为什么不使用 @Component？】
 * 虽然 @Component 可以自动注册 Listener，但无法控制注册顺序。
 * 某些 Listener 之间有依赖关系，需要按特定顺序注册。
 *
 * 【Listener 的类型】
 * 1. ServletContextListener：监听应用启动和关闭事件
 * 2. HttpSessionListener：监听 Session 创建和销毁事件
 * 3. ServletRequestListener：监听请求创建和销毁事件
 * 4. ServletRequestAttributeListener：监听请求属性变化事件
 *
 * 【本项目的 Listener】
 * 1. AppStartupListener：应用启动时预热字典数据
 * 2. SessionListener：统计在线人数
 * 3. ShutdownListener：应用关闭时清理资源
 *
 * 【注册顺序】
 * Listener 的注册顺序由 Bean 的定义顺序决定（在 @Configuration 类中的顺序）。
 * 但实际执行顺序由 Listener 的类型决定：
 * - ServletContextListener 在应用启动/关闭时执行
 * - HttpSessionListener 在 Session 创建/销毁时执行
 * - ServletRequestListener 在请求创建/销毁时执行
 */
@Configuration
@Slf4j
public class ListenerConfig {

    /**
     * 注册应用启动预热监听器
     *
     * 【用途】应用启动时加载字典数据到内存
     * 【执行时机】应用启动时（contextInitialized）和应用关闭时（contextDestroyed）
     *
     * @return ServletListenerRegistrationBean 对象
     */
    @Bean
    public ServletListenerRegistrationBean<EventListener> appStartupListenerRegistration() {
        ServletListenerRegistrationBean<EventListener> registration = new ServletListenerRegistrationBean<>();
        
        // 设置 Listener 实例
        registration.setListener(new AppStartupListener());
        
        log.info("[ListenerConfig] 注册 AppStartupListener");
        
        return registration;
    }

    /**
     * 注册在线人数统计监听器
     *
     * 【用途】统计当前在线用户数
     * 【执行时机】Session 创建时（sessionCreated）和 Session 销毁时（sessionDestroyed）
     *
     * @return ServletListenerRegistrationBean 对象
     */
    @Bean
    public ServletListenerRegistrationBean<EventListener> sessionListenerRegistration() {
        ServletListenerRegistrationBean<EventListener> registration = new ServletListenerRegistrationBean<>();
        
        registration.setListener(new SessionListener());
        
        log.info("[ListenerConfig] 注册 SessionListener");
        
        return registration;
    }

    /**
     * 注册优雅停机清理监听器
     *
     * 【用途】应用关闭时清理资源（数据库连接、线程池、缓存等）
     * 【执行时机】应用关闭时（contextDestroyed）
     *
     * @return ServletListenerRegistrationBean 对象
     */
    @Bean
    public ServletListenerRegistrationBean<EventListener> shutdownListenerRegistration() {
        ServletListenerRegistrationBean<EventListener> registration = new ServletListenerRegistrationBean<>();
        
        registration.setListener(new ShutdownListener());
        
        log.info("[ListenerConfig] 注册 ShutdownListener");
        
        return registration;
    }
}
