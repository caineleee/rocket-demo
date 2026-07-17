package org.lee.rocket.train.common.annotation;

import org.lee.rocket.train.common.constant.RedisAopConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 写入注解
 * <p>
 * 方法执行后，将结果写入 Redis。不读缓存，只写缓存。
 * <p>
 * 【为什么 @Cacheable/@CachePut 不够用？】
 * @CachePut 虽然也能写缓存，但它是"方法返回值 → 缓存"的固定模式。
 * @RedisSet 更灵活：
 * - 可以指定写哪个 Key（不一定和方法返回值直接对应）
 * - 可以写方法的入参而非返回值
 * - 有大 Key 检测、条件判断等额外控制
 * <p>
 * 【典型场景】
 * - 更新商品后，主动刷新缓存（Key 和返回值不完全对应）
 * - 预热：将计算结果写入 Redis 供其他服务使用
 * - Token 存储：将生成的 Token 写入 Redis（Token 不是方法返回值）
 * <p>
 * 【使用示例】
 * <pre>
 * // 最简用法：缓存方法返回值
 * {@code @RedisSet}(key = "#result.id", prefix = "goods:detail")
 * public Goods updateGoods(Goods goods) {
 *     goodsMapper.updateById(goods);
 *     return goods;  // 返回值自动序列化写入 Redis
 * }
 *
 * // 完整用法：带条件判断和大 Key 检测
 * {@code @RedisSet}(
 *     key = "#result.id",
 *     prefix = "goods:detail",
 *     ttl = 3600,
 *     maxSize = 10240,           // 超过 10KB 告警
 *     condition = "#result != null"  // 结果非空才缓存
 * )
 * public Goods updateGoods(Goods goods) { ... }
 * </pre>
 * <p>
 * 【注意事项】
 * 1. 写入的值是方法的返回值（必须是可序列化的对象）
 * 2. maxSize 超过阈值时打印 WARN 日志，但不阻止写入（可后续改为降级）
 * 3. condition 为空时始终写入，不为空时只有条件为 true 才写入
 * 4. Redis 异常时根据 failStrategy 处理，默认 FAIL_SAFE（不影响业务）
 *
 * @author lee
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisSet {

    /**
     * Redis Key 的 SpEL 表达式
     * <p>
     * 支持引用方法参数和返回值（#result）：
     * - "#goods.id" → 取方法参数 goods 的 id 属性
     * - "#result.id" → 取方法返回值的 id 属性
     * - "'goods:' + #id" → 字符串拼接
     */
    String key();

    /**
     * Key 前缀
     */
    String prefix() default RedisAopConstants.DEFAULT_PREFIX;

    /**
     * 过期时间（秒）
     * <p>
     * - ttl = 0：永不过期（不推荐，可能导致内存泄漏）
     * - ttl = 3600：1 小时后过期
     * <p>
     * 默认值：3600 秒（1 小时）
     */
    int ttl() default RedisAopConstants.DEFAULT_TTL;

    /**
     * 大 Key 阈值（字节）
     * <p>
     * 序列化后的数据超过此值时打印 WARN 日志告警。
     * 参考阿里/美团 Redis 规范：String 类型不超过 10KB。
     * <p>
     * 默认值：10240 字节（10KB）
     */
    int maxSize() default RedisAopConstants.DEFAULT_MAX_SIZE;

    /**
     * 条件表达式（SpEL）
     * <p>
     * 只有条件为 true 时才写入 Redis。
     * - condition = ""：无条件，始终写入
     * - condition = "#result != null"：结果非空时才写入
     * - condition = "#result.status == 1"：结果为特定状态时才写入
     * <p>
     * 默认值：空字符串（无条件）
     */
    String condition() default RedisAopConstants.DEFAULT_CONDITION;

    /**
     * 异常兜底策略
     * <p>
     * Redis 写入失败时的处理方式：
     * - FAIL_SAFE（默认）：记录日志，不影响业务方法执行
     * - FAIL_FAST：直接抛异常（不推荐，缓存写入失败不应阻止业务）
     * <p>
     * 默认值：FAIL_SAFE
     */
    FailStrategy failStrategy() default FailStrategy.FAIL_SAFE;
}
