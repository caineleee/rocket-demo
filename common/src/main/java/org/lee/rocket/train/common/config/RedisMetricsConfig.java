package org.lee.rocket.train.common.config;

import org.springframework.context.annotation.Configuration;

/**
 * Redis AOP 监控配置类
 * <p>
 * 本配置类目前为空，仅作为占位符。
 * <p>
 * 【监控指标】
 * 所有指标由 RedisOperationService 在构造时通过 MeterRegistry 注册：
 * - redis.aop.hit：缓存命中次数（Counter）
 * - redis.aop.miss：缓存未命中次数（Counter）
 * - redis.aop.error：异常次数（Counter）
 * - redis.aop.bigkey：大 Key 告警次数（Counter）
 * - redis.aop.lock.acquired：锁获取成功次数（Counter）
 * - redis.aop.lock.failed：锁获取失败次数（Counter）
 * - redis.aop.duration：操作耗时分布（Timer）
 * <p>
 * 【查看方式】
 * 在业务模块中添加 spring-boot-starter-actuator 依赖后，
 * 启动应用访问：http://localhost:{port}/actuator/metrics/redis.aop.hit
 * <p>
 * 【扩展说明】
 * 如果需要为所有指标添加公共标签（如 application、env），
 * 可以在业务模块中定义 MeterRegistryCustomizer Bean：
 * <pre>
 * {@code @Bean}
 * public MeterRegistryCustomizer{@literal <MeterRegistry>} metricsCommonTags() {
 *     return registry -> registry.config().commonTags("application", "rocket-demo");
 * }
 * </pre>
 *
 * @author lee
 */
@Configuration
public class RedisMetricsConfig {
    // 监控指标由 RedisOperationService 构造时自动注册到 MeterRegistry
    // 无需在此处额外配置
}
