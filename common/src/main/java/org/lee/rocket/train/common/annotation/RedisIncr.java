package org.lee.rocket.train.common.annotation;

import org.lee.rocket.train.common.constant.RedisAopConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 原子计数器注解
 * <p>
 * 基于 Redis INCR 命令实现原子自增/自减，支持设置上限和下限。
 * <p>
 * 【为什么 @Cacheable 做不到？】
 * @Cacheable 是"读-执行-写"模式，没有原子自增能力。
 * 限流、库存扣减需要 INCR + 阈值判断的原子操作，@Cacheable 无法表达。
 * <p>
 * 【典型场景】
 * - API 限流：每分钟最多 N 次调用
 * - 库存扣减：delta = -1，minCount = 0（防止扣成负数）
 * - 日活统计：delta = 1，ttl = 86400（每天重置）
 * - 验证码发送频率：每小时最多 5 次
 * <p>
 * 【使用示例】
 * <pre>
 * // API 限流：每分钟最多 100 次
 * {@code @RedisIncr}(key = "#userId", prefix = "limit:api", maxCount = 100, ttl = 60)
 * public Result callApi(Long userId) { ... }
 *
 * // 库存扣减：每次 -1，不能低于 0
 * {@code @RedisIncr}(key = "#goodsId", prefix = "stock", delta = -1, minCount = 0)
 * public Result deductStock(Long goodsId) { ... }
 *
 * // 日活统计：每天重置
 * {@code @RedisIncr}(key = "#userId", prefix = "dau", ttl = 86400)
 * public Result trackDailyActive(Long userId) { ... }
 * </pre>
 * <p>
 * 【注意事项】
 * 1. delta 支持负数（库存扣减场景）
 * 2. ttl = 0 表示永不过期（如总库存），ttl > 0 表示定时过期（如日限流）
 * 3. 超过 maxCount 或低于 minCount 时，根据 failStrategy 处理
 * 4. Redis INCR 是原子操作，天然支持高并发
 *
 * @author lee
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisIncr {

    /**
     * Redis Key 的 SpEL 表达式
     * <p>
     * 支持引用方法参数，例如：
     * - "#userId" → 取方法参数 userId 的值
     * - "#goodsId" → 取方法参数 goodsId 的值
     */
    String key();

    /**
     * Key 前缀
     * <p>
     * 完整 Key 格式：{prefix}:{类名.方法名}:{key解析结果}
     * 例如：prefix = "limit:api" → Key = limit:api:UserService.callApi:12345
     */
    String prefix() default RedisAopConstants.DEFAULT_PREFIX;

    /**
     * 步长（每次增加/减少的值）
     * <p>
     * - delta = 1：每次 +1（限流计数、日活统计）
     * - delta = -1：每次 -1（库存扣减）
     * - delta = 5：每次 +5（批量操作）
     * <p>
     * 默认值：1
     */
    long delta() default RedisAopConstants.DEFAULT_INCR_DELTA;

    /**
     * 计数器上限
     * <p>
     * 自增后超过此值时，根据 failStrategy 处理。
     * - maxCount = Long.MAX_VALUE：不限制上限
     * - maxCount = 100：超过 100 时拒绝执行
     * <p>
     * 默认值：Long.MAX_VALUE（不限制）
     */
    long maxCount() default RedisAopConstants.DEFAULT_INCR_MAX_COUNT;

    /**
     * 计数器下限
     * <p>
     * 自减后低于此值时，根据 failStrategy 处理。
     * - minCount = Long.MIN_VALUE：不限制下限
     * - minCount = 0：不允许扣成负数（库存场景）
     * <p>
     * 默认值：Long.MIN_VALUE（不限制）
     */
    long minCount() default RedisAopConstants.DEFAULT_INCR_MIN_COUNT;

    /**
     * 自动过期时间（秒）
     * <p>
     * - ttl = 0：永不过期（如总库存）
     * - ttl = 60：60 秒后过期（如 API 限流，每分钟重置）
     * - ttl = 86400：24 小时后过期（如日活统计，每天重置）
     * <p>
     * 默认值：0（永不过期）
     */
    int ttl() default RedisAopConstants.DEFAULT_INCR_TTL;

    /**
     * 异常兜底策略
     * <p>
     * Redis 异常或超过阈值时的处理方式：
     * - FAIL_SAFE（默认）：Redis 异常时降级放行，不影响业务
     * - FAIL_FAST：Redis 异常或超限时直接抛异常
     * <p>
     * 默认值：FAIL_SAFE
     */
    FailStrategy failStrategy() default FailStrategy.FAIL_SAFE;
}
