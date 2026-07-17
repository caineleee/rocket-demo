package org.lee.rocket.train.common.annotation;

import org.lee.rocket.train.common.constant.RedisAopConstants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 读取 + 参数注入注解
 * <p>
 * 在方法执行前，从 Redis 读取值并注入到指定方法参数中。
 * <p>
 * 【为什么 @Cacheable 做不到？】
 * @Cacheable 缓存的是方法返回值，而这里是要在方法执行前从 Redis 读一个值注入到参数中。
 * 例如根据 Token 查用户信息，Token 是入参，用户信息需要注入到另一个参数。
 * <p>
 * 【典型场景】
 * - JWT Token → Redis 查 userId → 注入参数
 * - 配置中心 → Redis 读配置 → 注入参数
 * - 会话信息 → Redis 读 Session → 注入参数
 * <p>
 * 【使用示例】
 * <pre>
 * // 最简用法：自动推断参数类型
 * {@code @RedisGet}(key = "#token", prefix = "token:access", injectParam = "cachedUser")
 * public Result getUserInfo(String token, UserDTO cachedUser) {
 *     if (cachedUser == null) {
 *         // Redis 未命中，从 DB 查询
 *         cachedUser = userService.getByToken(token);
 *     }
 *     return Result.success(cachedUser);
 * }
 *
 * // 完整用法：显式指定反序列化类型和失败策略
 * {@code @RedisGet}(
 *     key = "#token",
 *     prefix = "token:access",
 *     injectParam = "cachedUser",
 *     deserializeAs = UserDTO.class,
 *     failStrategy = FailStrategy.DB_ONLY
 * )
 * public Result getUserInfo(String token, UserDTO cachedUser) { ... }
 * </pre>
 * <p>
 * 【未命中行为】
 * 根据 failStrategy 动态决定：
 * - DB_ONLY（默认）：Redis 无数据 → 参数注入 null → 方法继续执行（走 DB）
 * - FAIL_FAST：Redis 无数据 → 直接抛异常，阻止方法执行
 * - FAIL_SAFE：Redis 异常时降级，参数注入 null
 * <p>
 * 【注意事项】
 * 1. injectParam 指定的参数名必须与方法参数名一致
 * 2. 默认自动从方法参数类型推断反序列化目标类型
 * 3. 如果参数声明为 Object 类型，需要通过 deserializeAs 显式指定
 * 4. Redis 中的值必须是 JSON 格式（由 @RedisSet 写入）
 *
 * @author lee
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisGet {

    /**
     * Redis Key 的 SpEL 表达式
     * <p>
     * 支持引用方法参数，例如：
     * - "#token" → 取方法参数 token 的值
     * - "#user.id" → 取方法参数 user 的 id 属性
     */
    String key();

    /**
     * Key 前缀
     * <p>
     * 完整 Key 格式：{prefix}:{类名.方法名}:{key解析结果}
     */
    String prefix() default RedisAopConstants.DEFAULT_PREFIX;

    /**
     * 要注入的方法参数名
     * <p>
     * 从 Redis 读取的值将注入到此参数中。
     * 参数名必须与方法签名中的参数名完全一致。
     * <p>
     * 示例：injectParam = "cachedUser" → 方法中必须有 UserDTO cachedUser 参数
     */
    String injectParam();

    /**
     * 反序列化目标类型
     * <p>
     * 从 Redis 读出 JSON 字符串后，反序列化为指定类型。
     * - 不指定：自动从 injectParam 对应的方法参数类型推断
     * - 显式指定：用于参数声明为 Object 等泛型场景
     * <p>
     * 默认值：Void.class（表示自动推断）
     */
    Class<?> deserializeAs() default Void.class;

    /**
     * 异常兜底策略
     * <p>
     * Redis 无数据或异常时的处理方式：
     * - DB_ONLY（默认）：参数注入 null，方法继续执行（推荐用于缓存+DB 双读场景）
     * - FAIL_FAST：直接抛异常（推荐用于强依赖 Redis 的场景，如 Token 校验）
     * - FAIL_SAFE：Redis 异常时降级，参数注入 null
     * <p>
     * 默认值：DB_ONLY
     */
    FailStrategy failStrategy() default FailStrategy.DB_ONLY;
}
