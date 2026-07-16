package org.lee.rocket.train.common.listener;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 应用启动预热监听器
 *
 * 【为什么需要启动预热？】
 * 在 Web 应用启动时，某些数据需要提前加载到内存中，以提高后续请求的响应速度。
 * 例如：
 * - 字典数据（如订单状态、支付方式等）
 * - 配置信息（如系统参数、业务规则等）
 * - 热点数据（如热门商品、推荐列表等）
 *
 * 如果不做预热，第一次访问这些数据的请求需要查询数据库或远程服务，响应会很慢。
 * 通过启动预热，可以在应用启动时就加载好数据，后续请求直接从内存读取，响应速度极快。
 *
 * 【监听的事件】
 * ServletContextListener 监听的是应用级别的启动和关闭事件：
 * - contextInitialized()：应用启动时触发，用于初始化资源
 * - contextDestroyed()：应用关闭时触发，用于清理资源
 *
 * 【执行时机】
 * 在 Spring Boot 启动过程中，ServletContextListener 的 contextInitialized() 方法会在
 * Spring 容器初始化完成后、开始接收请求之前执行。
 *
 * 【注意事项】
 * 1. contextInitialized() 的执行会阻塞应用启动，如果加载数据很慢，会导致应用启动很慢
 * 2. 如果加载失败，应该抛出异常，让应用启动失败，而不是带病运行
 * 3. 对于耗时操作，可以考虑使用异步线程，但要注意线程的生命周期管理
 *
 * 【大厂做法】
 * - 阿里：使用 Diamond/Nacos 配置中心，应用启动时拉取配置并缓存
 * - 美团：使用 Redis 缓存热点数据，应用启动时预热
 * - 字节跳动：使用本地缓存 + 远程缓存两级架构，启动时加载本地缓存
 *
 * 【本实现】
 * 从 classpath:data/dict.json 文件读取字典数据，加载到 ConcurrentHashMap 中。
 * 模拟从数据库或配置中心加载数据的过程。
 */
public class AppStartupListener implements ServletContextListener {

    /**
     * 字典数据缓存
     * 
     * 【为什么使用 ConcurrentHashMap？】
     * - 线程安全：多个请求可能同时读取字典数据，ConcurrentHashMap 保证并发安全
     * - 高性能：相比 Hashtable，ConcurrentHashMap 使用分段锁，并发性能更好
     * - 不允许 null：ConcurrentHashMap 不允许 null key 和 null value，避免空指针
     *
     * 【数据结构】
     * Map<String, Map<String, String>>
     * 外层 key：字典类型（如 "order_status", "pay_type"）
     * 内层 Map：字典项（key 为字典编码，value 为字典名称）
     * 
     * 例如：
     * {
     *   "order_status": {"0": "待支付", "1": "已支付", "2": "已发货"},
     *   "pay_type": {"1": "支付宝", "2": "微信支付"}
     * }
     */
    private static final Map<String, Map<String, String>> DICT_CACHE = new ConcurrentHashMap<>();

    /**
     * 应用启动时触发
     * 
     * 【执行流程】
     * 1. 读取 classpath:data/dict.json 文件
     * 2. 解析 JSON 数据
     * 3. 加载到 DICT_CACHE 中
     * 4. 将 DICT_CACHE 存储到 ServletContext 中，供其他组件使用
     *
     * 【异常处理】
     * 如果加载失败，抛出 RuntimeException，让应用启动失败。
     * 这样可以避免应用带病运行，导致后续请求出现不可预知的问题。
     *
     * @param sce ServletContextEvent 对象，可以获取 ServletContext
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[AppStartupListener] 应用启动，开始预热字典数据...");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // ===== 1. 读取 JSON 文件 =====
            // getResourceAsStream() 从 classpath 读取资源文件
            // classpath 包括：src/main/resources 目录下的文件
            ServletContext context = sce.getServletContext();
            InputStream inputStream = context.getResourceAsStream("/data/dict.json");
            
            if (inputStream == null) {
                throw new RuntimeException("找不到字典数据文件：/data/dict.json");
            }
            
            // ===== 2. 读取文件内容 =====
            // 使用 BufferedReader 逐行读取文件内容
            // 指定字符编码为 UTF-8，避免中文乱码
            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }
            
            // ===== 3. 解析 JSON 数据 =====
            // 使用 Jackson 的 ObjectMapper 解析 JSON
            // 【注意】ObjectMapper 是线程安全的，可以复用
            // 【大厂做法】通常会将 ObjectMapper 定义为静态常量，避免重复创建
            ObjectMapper objectMapper = new ObjectMapper();
            
            // 解析 JSON 为 Map<String, Map<String, String>>
            // 【类型擦除】由于 Java 泛型的类型擦除，需要使用 TypeReference 来指定具体类型
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> dictData = objectMapper.readValue(
                jsonContent.toString(), 
                Map.class
            );
            
            // ===== 4. 加载到缓存 =====
            DICT_CACHE.putAll(dictData);
            
            // ===== 5. 存储到 ServletContext =====
            // 将缓存存储到 ServletContext 中，供其他组件（如 Controller、Service）使用
            // ServletContext 是应用级别的上下文，所有请求共享
            context.setAttribute("DICT_CACHE", DICT_CACHE);
            
            long cost = System.currentTimeMillis() - startTime;
            System.out.println("[AppStartupListener] 字典数据预热完成，共加载 " 
                + DICT_CACHE.size() + " 个字典类型，耗时 " + cost + "ms");
            
            // 打印加载的字典类型
            for (String dictType : DICT_CACHE.keySet()) {
                System.out.println("[AppStartupListener]   - " + dictType + ": " 
                    + DICT_CACHE.get(dictType).size() + " 个字典项");
            }
            
        } catch (Exception e) {
            // 加载失败，抛出异常，让应用启动失败
            System.err.println("[AppStartupListener] 字典数据预热失败：" + e.getMessage());
            throw new RuntimeException("字典数据预热失败", e);
        }
    }

    /**
     * 应用关闭时触发
     * 
     * 【执行时机】
     * 在应用关闭时，ServletContextListener 的 contextDestroyed() 方法会被调用。
     * 此时可以清理资源，如关闭数据库连接、释放文件句柄等。
     * 
     * 【本实现】
     * 清空字典缓存，释放内存。
     * 实际上，应用关闭时 JVM 会回收所有内存，这里只是为了演示清理逻辑。
     *
     * @param sce ServletContextEvent 对象
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[AppStartupListener] 应用关闭，清理字典缓存...");
        
        DICT_CACHE.clear();
        
        System.out.println("[AppStartupListener] 字典缓存已清理");
    }
}
