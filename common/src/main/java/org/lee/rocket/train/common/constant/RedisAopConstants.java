package org.lee.rocket.train.common.constant;

/**
 * Redis AOP 框架常量类
 * <p>
 * 集中管理所有注解参数的默认值。
 * 这样设计的好处：
 * 1. 统一管理：所有默认值在一个地方，便于调整和维护
 * 2. 可配置化：后续可以通过 application.properties 覆盖这些常量
 * 3. 文档化：常量名本身就是文档，一看就懂含义
 * 4. 类型安全：编译期检查，避免注解中写错默认值
 * <p>
 * 使用方式：注解参数不指定时，自动使用本类中的默认值
 * 例如：@RedisGet(key = "#id") 未指定 ttl，则使用 DEFAULT_TTL = 3600
 *
 * @author lee
 */
public final class RedisAopConstants {

    private RedisAopConstants() {
        // 工具类，禁止实例化
    }

    // ==================== 时间相关默认值（秒） ====================

    /**
     * 默认缓存过期时间：1 小时（3600 秒）
     * <p>
     * 适用场景：@RedisSet 的 ttl 参数
     * 行业参考：大部分业务缓存 1 小时过期是平衡点
     * - 太短（如 60 秒）：缓存命中率低，频繁查 DB
     * - 太长（如 86400 秒）：数据一致性差，用户看到旧数据
     */
    public static final int DEFAULT_TTL = 3600;

    /**
     * 默认锁最大等待时间：3 秒
     * <p>
     * 适用场景：@RedisLock 的 waitTime 参数
     * 含义：尝试获取锁时，最多等待 3 秒
     * - 3 秒内获取到锁 → 执行业务
     * - 3 秒内未获取到锁 → 根据 failStrategy 处理
     * <p>
     * 为什么是 3 秒？
     * - 太短（如 100ms）：高并发时大量请求直接失败
     * - 太长（如 30 秒）：用户等待时间过长，体验差
     * - 3 秒是用户体验和并发控制的平衡点
     */
    public static final int DEFAULT_LOCK_WAIT_TIME = 3;

    /**
     * 默认锁自动释放时间：10 秒
     * <p>
     * 适用场景：@RedisLock 的 leaseTime 参数
     * 含义：锁被持有 10 秒后自动释放（防止死锁）
     * <p>
     * 关键考量：leaseTime 必须 > 业务方法的最大执行时间
     * - 如果业务方法执行 5 秒，leaseTime 至少设为 10 秒
     * - 如果业务方法可能执行很久，需要适当增大 leaseTime
     * - Redisson 的看门狗机制可以自动续期（但本框架未启用，保持简单）
     */
    public static final int DEFAULT_LOCK_LEASE_TIME = 10;

    /**
     * 默认计数器过期时间：0 表示永不过期
     * <p>
     * 适用场景：@RedisIncr 的 ttl 参数
     * - ttl = 0：计数器永不过期（如总库存）
     * - ttl > 0：计数器定时过期（如日限流，ttl = 86400）
     */
    public static final int DEFAULT_INCR_TTL = 0;

    // ==================== 阈值相关默认值 ====================

    /**
     * 默认大 Key 阈值：10KB（10240 字节）
     * <p>
     * 适用场景：@RedisSet 的 maxSize 参数
     * 含义：序列化后的数据超过 10KB 时打印 WARN 日志告警
     * <p>
     * 为什么关注大 Key？
     * - Redis 是单线程模型，读写大 Key 会阻塞其他命令
     * - 大 Key 传输占用网络带宽
     * - 大 Key 删除时可能导致 Redis 卡顿
     * <p>
     * 行业参考：
     * - 阿里 Redis 规范：String 类型不超过 10KB
     * - 美团 Redis 规范：单个 Key 不超过 10KB
     */
    public static final int DEFAULT_MAX_SIZE = 10240;

    /**
     * 默认计数器步长：1
     * <p>
     * 适用场景：@RedisIncr 的 delta 参数
     * - delta = 1：每次 +1（如限流计数、日活统计）
     * - delta = -1：每次 -1（如库存扣减）
     */
    public static final long DEFAULT_INCR_DELTA = 1;

    /**
     * 默认计数器上限：Long.MAX_VALUE（不限制）
     * <p>
     * 适用场景：@RedisIncr 的 maxCount 参数
     * - maxCount = Long.MAX_VALUE：不限制上限
     * - maxCount = 100：超过 100 时根据 failStrategy 处理
     */
    public static final long DEFAULT_INCR_MAX_COUNT = Long.MAX_VALUE;

    /**
     * 默认计数器下限：Long.MIN_VALUE（不限制）
     * <p>
     * 适用场景：@RedisIncr 的 minCount 参数
     * - minCount = Long.MIN_VALUE：不限制下限
     * - minCount = 0：不允许扣成负数（库存场景）
     */
    public static final long DEFAULT_INCR_MIN_COUNT = Long.MIN_VALUE;

    // ==================== 前缀相关默认值 ====================

    /**
     * 默认 Key 前缀：空字符串
     * <p>
     * 适用场景：所有注解的 prefix 参数
     * 含义：不添加额外前缀，Key 格式为 {业务标识}:{SpEL解析结果}
     * <p>
     * 建议：生产环境建议设置项目级前缀，避免多项目 Redis Key 冲突
     * 例如：prefix = "rocket:goods" → Key 为 rocket:goods:detail:123
     */
    public static final String DEFAULT_PREFIX = "";

    /**
     * 默认条件表达式：空字符串（表示无条件限制）
     * <p>
     * 适用场景：@RedisSet 的 condition 参数
     * - condition = ""：无条件，始终执行
     * - condition = "#result != null"：结果非空时才执行
     */
    public static final String DEFAULT_CONDITION = "";
}
