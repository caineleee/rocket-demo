package org.lee.rocket.train.common.listener;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * 优雅停机清理监听器
 *
 * 【为什么需要优雅停机？】
 * 在应用关闭时，可能有一些资源正在使用中：
 * - 数据库连接池中的连接
 * - 线程池中的任务
 * - 缓存中的数据
 * - 第三方服务的连接（如 Redis、MQ 等）
 *
 * 如果直接关闭应用（如 kill -9），这些资源可能没有被正确释放，导致：
 * - 数据库连接泄漏
 * - 数据丢失（如未提交的事务）
 * - 第三方服务异常（如未正常断开连接）
 *
 * 优雅停机的目的是：在应用关闭前，给应用一个机会去清理资源，确保数据安全。
 *
 * 【Spring Boot 的优雅停机】
 * Spring Boot 2.3+ 支持优雅停机：
 * server.shutdown=graceful
 * spring.lifecycle.timeout-per-shutdown-phase=30s
 *
 * 配置后，Spring Boot 会：
 * 1. 停止接收新请求
 * 2. 等待正在处理的请求完成（最多等待 30 秒）
 * 3. 调用 @PreDestroy 方法清理资源
 * 4. 关闭应用
 *
 * 【本监听器的作用】
 * 在 Spring Boot 的优雅停机基础上，额外清理一些 Servlet 规范级别的资源。
 * 例如：
 * - 关闭数据库连接池（如果使用原生 JDBC）
 * - 关闭线程池
 * - 清理缓存
 * - 断开第三方连接
 *
 * 【执行时机】
 * contextDestroyed() 方法会在应用关闭时执行，时机在：
 * - Spring Boot 停止接收新请求之后
 * - JVM 关闭之前
 *
 * 【注意事项】
 * 1. contextDestroyed() 的执行时间不能太长，否则会影响应用关闭速度
 * 2. 如果有耗时操作（如保存大量数据到磁盘），应该使用异步线程
 * 3. 不要在这里抛出异常，否则可能影响其他 Listener 的执行
 *
 * 【大厂做法】
 * - 阿里：使用 Kubernetes 的 preStop hook，在容器关闭前执行清理脚本
 * - 美团：使用 ShutdownHook + Spring 的 @PreDestroy 双重保障
 * - 字节跳动：使用自研的服务治理框架，统一管理资源生命周期
 *
 * 【本实现】
 * 演示如何清理数据库连接池和线程池。
 * 由于本项目使用 Spring 管理的资源（如 HikariCP、@Async），
 * 实际清理逻辑由 Spring 负责，这里只做演示。
 */
public class ShutdownListener implements ServletContextListener {

    /**
     * 应用关闭时触发
     * 
     * 【执行流程】
     * 1. 停止接收新请求（由 Spring Boot 负责）
     * 2. 等待正在处理的请求完成（由 Spring Boot 负责）
     * 3. 清理 Servlet 规范级别的资源（本方法负责）
     * 4. 清理 Spring 管理的资源（由 Spring 负责）
     * 5. 关闭 JVM
     *
     * @param sce ServletContextEvent 对象
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[ShutdownListener] ========================================");
        System.out.println("[ShutdownListener] 应用正在关闭，开始清理资源...");
        System.out.println("[ShutdownListener] ========================================");
        
        ServletContext context = sce.getServletContext();
        
        // ===== 1. 清理字典缓存 =====
        System.out.println("[ShutdownListener] 清理字典缓存...");
        Object dictCache = context.getAttribute("DICT_CACHE");
        if (dictCache != null) {
            ((java.util.Map<?, ?>) dictCache).clear();
            System.out.println("[ShutdownListener] 字典缓存已清理");
        }
        
        // ===== 2. 演示：关闭数据库连接池 =====
        // 【注意】本项目使用 Spring 管理的 HikariCP，不需要手动关闭
        // 这里只是演示如何关闭原生 JDBC 连接池
        System.out.println("[ShutdownListener] 检查数据库连接池...");
        // ===== 实际场景代码（注释） =====
        // DataSource dataSource = (DataSource) context.getAttribute("dataSource");
        // if (dataSource instanceof HikariDataSource) {
        //     HikariDataSource hikariDS = (HikariDataSource) dataSource;
        //     System.out.println("[ShutdownListener] 正在关闭数据库连接池...");
        //     System.out.println("[ShutdownListener] 当前活跃连接数: " + hikariDS.getHikariPoolMXBean().getActiveConnections());
        //     System.out.println("[ShutdownListener] 当前空闲连接数: " + hikariDS.getHikariPoolMXBean().getIdleConnections());
        //     hikariDS.close();
        //     System.out.println("[ShutdownListener] 数据库连接池已关闭");
        // }
        System.out.println("[ShutdownListener] 数据库连接池由 Spring 管理，无需手动关闭");
        
        // ===== 3. 演示：关闭线程池 =====
        // 【注意】本项目使用 Spring 管理的线程池（@Async），不需要手动关闭
        // 这里只是演示如何关闭原生线程池
        System.out.println("[ShutdownListener] 检查线程池...");
        // ===== 实际场景代码（注释） =====
        // ExecutorService executor = (ExecutorService) context.getAttribute("executor");
        // if (executor != null) {
        //     System.out.println("[ShutdownListener] 正在关闭线程池...");
        //     System.out.println("[ShutdownListener] 等待任务完成...");
        //     executor.shutdown(); // 停止接收新任务
        //     try {
        //         // 等待正在执行的任务完成（最多等待 10 秒）
        //         if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
        //             System.out.println("[ShutdownListener] 任务未完成，强制关闭...");
        //             executor.shutdownNow(); // 强制关闭
        //         }
        //     } catch (InterruptedException e) {
        //         System.err.println("[ShutdownListener] 等待任务完成时被中断");
        //         executor.shutdownNow();
        //         Thread.currentThread().interrupt();
        //     }
        //     System.out.println("[ShutdownListener] 线程池已关闭");
        // }
        System.out.println("[ShutdownListener] 线程池由 Spring 管理，无需手动关闭");
        
        // ===== 4. 演示：关闭 Redis 连接 =====
        // 【注意】本项目使用 Spring 管理的 RedisTemplate，不需要手动关闭
        // 这里只是演示如何关闭原生 Redis 连接
        System.out.println("[ShutdownListener] 检查 Redis 连接...");
        // ===== 实际场景代码（注释） =====
        // RedisConnectionFactory factory = (RedisConnectionFactory) context.getAttribute("redisFactory");
        // if (factory != null) {
        //     System.out.println("[ShutdownListener] 正在关闭 Redis 连接...");
        //     RedisConnection connection = factory.getConnection();
        //     connection.close();
        //     System.out.println("[ShutdownListener] Redis 连接已关闭");
        // }
        System.out.println("[ShutdownListener] Redis 连接由 Spring 管理，无需手动关闭");
        
        // ===== 5. 保存运行时状态（可选） =====
        // 某些应用需要在关闭前保存运行时状态，下次启动时恢复
        // 例如：保存当前处理进度、保存未发送的消息等
        System.out.println("[ShutdownListener] 保存运行时状态...");
        // ===== 实际场景代码（注释） =====
        // System.out.println("[ShutdownListener] 保存处理进度到磁盘...");
        // saveProgressToDisk();
        // System.out.println("[ShutdownListener] 保存未发送的消息到数据库...");
        // saveUnsentMessagesToDatabase();
        System.out.println("[ShutdownListener] 无需保存运行时状态");
        
        System.out.println("[ShutdownListener] ========================================");
        System.out.println("[ShutdownListener] 资源清理完成，应用可以安全关闭");
        System.out.println("[ShutdownListener] ========================================");
    }

    /**
     * 应用启动时触发
     * 
     * 【说明】
     * ShutdownListener 主要关注应用关闭时的清理逻辑，
     * 启动时的初始化逻辑由 AppStartupListener 负责。
     * 这里实现 contextInitialized() 只是为了完整性，实际不做任何操作。
     *
     * @param sce ServletContextEvent 对象
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[ShutdownListener] 应用启动（ShutdownListener 仅关注关闭事件）");
    }
}
