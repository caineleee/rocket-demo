package org.lee.rocket.train.common.annotation;

import org.lee.rocket.train.common.constant.RedisAopConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 删除注解
 * <p>
 * 方法执行后，删除指定的 Redis Key。
 * <p>
 * 【为什么 @CacheEvict 不够用？】
 * @CacheEvict 只能清除 @Cacheable 管理的缓存，Key 格式受限于 CacheManager 的配置。
 * 而实际业务中需要删除的 Key 可能是：
 * - Token 黑名单（blacklist:{token}）
 * - 分布式锁（lock:{key}）
 * - 计数器（limit:{key}）
 * 这些 Key 根本不是 @Cacheable 管理的，@CacheEvict 无法触及。
 * <p>
 * 【典型场景】
 * - 删除商品时，同时清理商品缓存和关联计数器
 * - 用户登出时，删除 Token 和 Session 数据
 * - 订单取消时，释放分布式锁和库存计数器
 * <p>
 * 【使用示例】
 * <pre>
 * // 删除单个 Key
 * {@code @RedisDel}(key = "#goodsId", prefix = "goods:detail")
 * public Result deleteGoods(Long goodsId) { ... }
 *
 * // 删除多个 Key（同时清理关联数据）
 * {@code @RedisDel}(keys = {"#goodsId", "'goods:category:' + #goodsId"}, prefix = "goods:detail")
 * public Result deleteGoods(Long goodsId) { ... }
 * </pre>
 * <p>
 * 【注意事项】
 * 1. keys 属性支持多个 Key（String 数组）
 * 2. 删除操作是异步的（fire-and-forget），不等待 Redis 响应
 * 3. Redis 异常时根据 failStrategy 处理，默认 FAIL_SAFE
 * 4. 删除不存在的 Key 不会报错（Redis DEL 命令的特性）
 *
 * @author lee
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisDel {

    /**
     * Redis Key 的 SpEL 表达式（单个 Key）
     * <p>
     * 如果指定了 keys 数组，此属性被忽略。
     * 支持引用方法参数，例如：
     * - "#goodsId" → 取方法参数 goodsId 的值
     */
    String key() default "";

    /**
     * Redis Key 的 SpEL 表达式数组（多个 Key）
     * <p>
     * 支持同时删除多个 Key，例如：
     * - {"#goodsId", "'goods:category:' + #goodsId"}
     * <p>
     * 如果同时指定了 key 和 keys，keys 优先。
     */
    String[] keys() default {};

    /**
     * Key 前缀
     */
    String prefix() default RedisAopConstants.DEFAULT_PREFIX;

    /**
     * 异常兜底策略
     * <p>
     * Redis 删除失败时的处理方式：
     * - FAIL_SAFE（默认）：记录日志，不影响业务方法执行
     * - FAIL_FAST：直接抛异常（不推荐，删除失败不应阻止业务）
     * <p>
     * 默认值：FAIL_SAFE
     */
    FailStrategy failStrategy() default FailStrategy.FAIL_SAFE;
}
