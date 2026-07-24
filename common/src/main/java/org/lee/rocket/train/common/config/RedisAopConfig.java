package org.lee.rocket.train.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis AOP 框架配置类
 * <p>
 * 配置 Redisson 客户端，供 RedisOperationService 使用。
 * <p>
 * 【配置项说明】
 * - Redisson：用于分布式锁和高级 Redis 操作
 * - ObjectMapper：使用 Spring Boot 自动配置的 Bean（已包含 JavaTimeModule）
 * <p>
 * 【Redisson 连接方式】
 * 复用 Spring Data Redis 的连接信息（spring.data.redis.host/port），
 * 避免重复配置。
 * <p>
 * 【为什么不需要自定义 ObjectMapper？】
 * Spring Boot 自动配置的 ObjectMapper 已经注册了 JavaTimeModule，
 * 支持 LocalDateTime 等 Java 8 时间类型的序列化/反序列化。
 * 直接使用 Spring 容器中的 ObjectMapper Bean 即可。
 *
 * @author lee
 */
@Configuration
public class RedisAopConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redisson 客户端
     * <p>
     * 【为什么需要 Redisson？】
     * Spring Data Redis 的 StringRedisTemplate 只支持基本操作（get/set/del），
     * 不支持分布式锁、原子操作等高级功能。Redisson 填补了这个空白。
     * <p>
     * 【连接配置】
     * - 地址：redis://{host}:{port}
     * - 密码：如果有密码则设置，否则不设置
     * - 连接超时：10 秒
     * - 命令超时：3 秒
     * <p>
     * 【生命周期】
     * destroyMethod = "shutdown" 确保应用关闭时优雅关闭 Redisson 连接池
     *
     * @return RedissonClient 实例
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer()
                .setAddress(address)
                // 如果有密码则设置，否则不设置（空字符串会被 Redisson 忽略）
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                // 连接超时时间（毫秒）：10 秒
                .setConnectTimeout(10000)
                // 命令等待超时时间（毫秒）：3 秒
                .setTimeout(3000);
        return Redisson.create(config);
    }
}
