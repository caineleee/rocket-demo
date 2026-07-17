package org.lee.rocket.train.common.annotation;

import org.lee.rocket.train.common.constant.RedisAopConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解
 * <p>
 * 基于 Redisson 实现分布式锁，方法执行前自动加锁，执行后自动释放。
 * <p>
 * 【为什么 @Cacheable 做不到？】
 * @Cacheable 是缓存语义（读-执行-写），没有互斥锁能力。
 * 分布式锁需要 Redisson 的 RLock.tryLock(waitTime, leaseTime)，
 * 涉及等待、续期、释放等完整生命周期，@Cacheable 无法表达。
 * <p>
 * 【典型场景】
 * - 防重复提交：同一订单号不能并发创建
 * - 秒杀防超卖：同一商品库存不能扣成负数
 * - 定时任务互斥：同一任务不能多实例同时执行
 * <p>
 * 【使用示例】
 * <pre>
 * // 最简用法：只指定 key
 * {@code @RedisLock}(key = "#orderNo")
 * public Result createOrder(String orderNo) { ... }
 *
 * // 完整用法：指定前缀、等待时间、锁释放时间、失败策略
 * {@code @RedisLock}(
 *     key = "#orderNo",
 *     prefix = "lock:order:create",
 *     waitTime = 5,        // 最多等 5 秒
 *     leaseTime = 30,      // 30 秒后自动释放
 *     failStrategy = FailStrategy.FAIL_FAST
 * )
 * public Result createOrder(String orderNo) { ... }
 * </pre>
 * <p>
 * 【注意事项】
 * 1. leaseTime 必须 > 业务方法的最大执行时间，否则锁会提前释放
 * 2. 被注解的方法必须是 Spring Bean 的方法（AOP 代理生效）
 * 3. 锁的粒度要合适：太粗（如锁整个表）影响并发，太细（如锁单行）可能漏锁
 * 4. 默认 failStrategy = FAIL_FAST，获取锁失败直接抛异常
 *
 * @author lee
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisLock {

    /**
     * Redis Key 的 SpEL 表达式
     * <p>
     * 支持引用方法参数，例如：
     * - "#orderNo" → 取方法参数 orderNo 的值
     * - "#user.id" → 取方法参数 user 的 id 属性
     * - "'prefix:' + #id" → 字符串拼接
     * <p>
     * ⚠️ 安全警告：永远不要将用户输入直接拼入 key() 表达式
     * SpEL 表达式会被安全过滤器检查，禁止调用 Runtime、ProcessBuilder 等危险类
     */
    String key();

    /**
     * Key 前缀
     * <p>
     * 完整 Key 格式：{prefix}:{类名.方法名}:{key解析结果}
     * 例如：prefix = "lock:order" → Key = lock:order:OrderService.createOrder:12345
     * <p>
     * 默认值：空字符串（不添加额外前缀）
     */
    String prefix() default RedisAopConstants.DEFAULT_PREFIX;

    /**
     * 最大等待时间（秒）
     * <p>
     * 尝试获取锁时，最多等待多长时间。
     * - waitTime = 0：不等待，立即返回（获取不到就失败）
     * - waitTime = 3：最多等 3 秒
     * <p>
     * 默认值：3 秒（用户体验和并发控制的平衡点）
     */
    int waitTime() default RedisAopConstants.DEFAULT_LOCK_WAIT_TIME;

    /**
     * 锁自动释放时间（秒）
     * <p>
     * 锁被持有多长时间后自动释放（防止死锁）。
     * ⚠️ 必须 > 业务方法的最大执行时间！
     * <p>
     * 默认值：10 秒
     */
    int leaseTime() default RedisAopConstants.DEFAULT_LOCK_LEASE_TIME;

    /**
     * 异常兜底策略
     * <p>
     * 获取锁失败时的处理方式：
     * - FAIL_FAST（默认）：直接抛异常，阻止方法执行
     * - FAIL_SAFE：记录日志，继续执行方法（不推荐，失去锁的意义）
     * <p>
     * 默认值：FAIL_FAST（分布式锁场景必须严格）
     */
    FailStrategy failStrategy() default FailStrategy.FAIL_FAST;
}
