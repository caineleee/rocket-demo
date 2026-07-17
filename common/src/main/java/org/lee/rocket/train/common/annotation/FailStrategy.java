package org.lee.rocket.train.common.annotation;

/**
 * Redis AOP 异常兜底策略枚举
 * <p>
 * 当 Redis 操作异常（连接超时、命令执行失败等）时，决定如何处理。
 * <p>
 * 三种策略的适用场景：
 * <pre>
 * FAIL_SAFE（默认）：
 *   - 行为：Redis 异常时降级为直接执行方法，不影响业务
 *   - 场景：缓存场景（缓存只是加速，DB 才是数据源）
 *   - 示例：@RedisGet 查缓存失败 → 参数注入 null → 方法走 DB 查询
 *
 * FAIL_FAST：
 *   - 行为：Redis 异常时直接抛异常，阻止方法执行
 *   - 场景：强依赖 Redis 的场景（如分布式锁、限流）
 *   - 示例：@RedisLock 获取锁失败 → 直接抛异常 → 阻止重复提交
 *
 * DB_ONLY：
 *   - 行为：Redis 异常时记录日志，参数注入 null，只走 DB
 *   - 场景：非关键缓存，Redis 不可用时完全依赖 DB
 *   - 示例：@RedisGet 查 Token 失败 → 参数为 null → 方法查 DB
 * </pre>
 * <p>
 * 选择策略的核心原则：
 * - 如果 Redis 挂了业务还能跑 → FAIL_SAFE 或 DB_ONLY
 * - 如果 Redis 挂了业务必须停 → FAIL_FAST
 *
 * @author lee
 */
public enum FailStrategy {

    /**
     * 安全降级：Redis 异常时降级执行，不影响业务
     * <p>
     * 这是最宽松的策略，适合缓存场景。
     * Redis 不可用时，自动降级为直接执行方法逻辑（如查 DB）。
     * 保证业务可用性优先于缓存命中率。
     */
    FAIL_SAFE,

    /**
     * 快速失败：Redis 异常时直接抛异常，阻止方法执行
     * <p>
     * 这是最严格的策略，适合强依赖 Redis 的场景。
     * 例如分布式锁：如果 Redis 不可用，无法保证互斥，必须拒绝执行。
     * 例如限流计数：如果 Redis 不可用，无法准确计数，必须拒绝执行。
     */
    FAIL_FAST,

    /**
     * 仅走 DB：Redis 异常时参数注入 null，方法走 DB 查询
     * <p>
     * 这是 FAIL_SAFE 的变体，专门用于 @RedisGet 的参数注入场景。
     * Redis 不可用时，注入参数为 null，方法内部根据 null 判断走 DB。
     * 与 FAIL_SAFE 的区别：DB_ONLY 明确告知方法"缓存未命中"，
     * 而 FAIL_SAFE 只是"降级执行"，语义上更模糊。
     */
    DB_ONLY
}
