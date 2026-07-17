package org.lee.rocket.train.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CustomerException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 操作封装服务
 * <p>
 * 统一封装所有 Redis 操作，提供：
 * 1. 缓存读写（get/set/delete）
 * 2. 分布式锁（tryLock/unlock）
 * 3. 原子操作（incr/decr/expire）
 * 4. 大 Key 检测与告警
 * 5. 监控埋点（Micrometer Counter/Timer）
 * <p>
 * 【设计原则】
 * - 所有操作都记录监控指标（命中率、耗时、异常率）
 * - 大 Key 检测：超过阈值打印 WARN 日志
 * - 异常处理：根据 failStrategy 决定是降级还是抛异常
 * <p>
 * 【依赖说明】
 * - StringRedisTemplate：用于缓存读写（JSON 序列化）
 * - RedissonClient：用于分布式锁和原子操作
 * - MeterRegistry：用于监控埋点（Spring Boot Actuator 自动注入）
 * - ObjectMapper：用于 JSON 序列化/反序列化
 *
 * @author lee
 */
@Slf4j
@Service
public class RedisOperationService {

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // ==================== 监控指标（在构造器中初始化） ====================

    /** 缓存命中次数 */
    private final Counter cacheHitCounter;
    /** 缓存未命中次数 */
    private final Counter cacheMissCounter;
    /** 异常次数 */
    private final Counter errorCounter;
    /** 大 Key 告警次数 */
    private final Counter bigKeyCounter;
    /** 锁获取成功次数 */
    private final Counter lockAcquiredCounter;
    /** 锁获取失败次数 */
    private final Counter lockFailedCounter;
    /** 操作耗时分布 */
    private final Timer operationTimer;

    /**
     * 构造器注入
     * <p>
     * 【为什么不用 @RequiredArgsConstructor？】
     * 因为 Counter/Timer 需要在构造时通过 MeterRegistry 创建，
     * 而 final 字段的初始化器在构造器之前执行，此时 meterRegistry 还未注入。
     * 所以必须用显式构造器，先注入依赖，再初始化监控指标。
     *
     * @param redisTemplate Spring Data Redis 模板
     * @param redissonClient Redisson 客户端
     * @param objectMapper Jackson 对象映射器
     * @param meterRegistry Micrometer 指标注册表
     */
    public RedisOperationService(StringRedisTemplate redisTemplate,
                                 RedissonClient redissonClient,
                                 ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;

        // 初始化监控指标（使用 MeterRegistry 创建）
        this.cacheHitCounter = Counter.builder("redis.aop.hit")
                .description("缓存命中次数").register(meterRegistry);
        this.cacheMissCounter = Counter.builder("redis.aop.miss")
                .description("缓存未命中次数").register(meterRegistry);
        this.errorCounter = Counter.builder("redis.aop.error")
                .description("异常次数").register(meterRegistry);
        this.bigKeyCounter = Counter.builder("redis.aop.bigkey")
                .description("大 Key 告警次数").register(meterRegistry);
        this.lockAcquiredCounter = Counter.builder("redis.aop.lock.acquired")
                .description("锁获取成功次数").register(meterRegistry);
        this.lockFailedCounter = Counter.builder("redis.aop.lock.failed")
                .description("锁获取失败次数").register(meterRegistry);
        this.operationTimer = Timer.builder("redis.aop.duration")
                .description("操作耗时分布").register(meterRegistry);
    }

    // ==================== 缓存读写操作 ====================

    /**
     * 从 Redis 读取值并反序列化为指定类型
     * <p>
     * 【执行流程】
     * 1. 从 Redis 获取 JSON 字符串
     * 2. 如果为 null，记录 miss 指标，返回 null
     * 3. 如果命中，记录 hit 指标
     * 4. 使用 Jackson 反序列化为目标类型
     * 5. 记录操作耗时
     * <p>
     * 【异常处理】
     * 如果 Redis 异常或反序列化失败，记录 error 指标，返回 null（降级）
     *
     * @param key Redis Key
     * @param clazz 目标类型
     * @param <T> 泛型
     * @return 反序列化后的对象，如果 Key 不存在或异常则返回 null
     */
    public <T> T get(String key, Class<T> clazz) {
        long start = System.currentTimeMillis();
        try {
            // 从 Redis 获取 JSON 字符串
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                // 缓存未命中
                cacheMissCounter.increment();
                return null;
            }
            // 缓存命中
            cacheHitCounter.increment();
            // 反序列化为目标类型
            T result = objectMapper.readValue(json, clazz);
            recordDuration(start, "get");
            return result;
        } catch (Exception e) {
            log.error("Redis GET 操作失败: key={}", key, e);
            errorCounter.increment();
            return null;
        }
    }

    /**
     * 将对象序列化后写入 Redis
     * <p>
     * 【执行流程】
     * 1. 使用 Jackson 将对象序列化为 JSON 字符串
     * 2. 检测大 Key：如果超过 maxSize 阈值，打印 WARN 日志
     * 3. 写入 Redis 并设置过期时间
     * 4. 记录操作耗时
     * <p>
     * 【大 Key 检测】
     * 参考阿里/美团 Redis 规范：String 类型不超过 10KB
     * 超过阈值时打印 WARN 日志，但不阻止写入（可后续改为降级）
     * <p>
     * 【异常处理】
     * 如果 Redis 异常，记录 error 指标，抛出 CustomerException
     *
     * @param key Redis Key
     * @param value 要写入的对象
     * @param ttl 过期时间（秒），0 表示永不过期
     * @param maxSize 大 Key 阈值（字节）
     */
    public void set(String key, Object value, int ttl, int maxSize) {
        long start = System.currentTimeMillis();
        try {
            // 1. 序列化为 JSON
            String json = objectMapper.writeValueAsString(value);

            // 2. 大 Key 检测：检查序列化后的字节大小
            int size = json.getBytes().length;
            if (size > maxSize) {
                log.warn("⚠ 大 Key 告警: key={}, size={} bytes, threshold={} bytes", key, size, maxSize);
                bigKeyCounter.increment();
            }

            // 3. 写入 Redis（根据 ttl 决定是否设置过期时间）
            if (ttl > 0) {
                redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(key, json);
            }

            recordDuration(start, "set");
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: key={}, value={}", key, value, e);
            errorCounter.increment();
            throw new CustomerException(ShopCode.REDIS_OPERATION_ERROR);
        } catch (Exception e) {
            log.error("Redis SET 操作失败: key={}", key, e);
            errorCounter.increment();
            throw new CustomerException(ShopCode.REDIS_OPERATION_ERROR);
        }
    }

    /**
     * 删除 Redis Key
     * <p>
     * 【执行流程】
     * 1. 调用 Redis DEL 命令
     * 2. 记录操作耗时
     * <p>
     * 【注意事项】
     * - 删除不存在的 Key 不会报错（Redis DEL 命令的特性）
     * - 删除操作是同步的，等待 Redis 响应
     *
     * @param key Redis Key
     */
    public void delete(String key) {
        long start = System.currentTimeMillis();
        try {
            redisTemplate.delete(key);
            recordDuration(start, "delete");
        } catch (Exception e) {
            log.error("Redis DELETE 操作失败: key={}", key, e);
            errorCounter.increment();
        }
    }

    // ==================== 分布式锁操作 ====================

    /**
     * 尝试获取分布式锁
     * <p>
     * 【执行流程】
     * 1. 通过 Redisson 获取 RLock 对象
     * 2. 调用 tryLock(waitTime, leaseTime, TimeUnit)
     * 3. 如果获取成功，记录 acquired 指标，返回 true
     * 4. 如果获取失败，记录 failed 指标，返回 false
     * <p>
     * 【参数说明】
     * @param key 锁的 Key
     * @param waitTime 最大等待时间（秒）
     * @param leaseTime 锁自动释放时间（秒）
     * @return true 表示获取成功，false 表示获取失败
     */
    public boolean tryLock(String key, int waitTime, int leaseTime) {
        RLock lock = redissonClient.getLock(key);
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (acquired) {
                lockAcquiredCounter.increment();
            } else {
                lockFailedCounter.increment();
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁时被中断: key={}", key, e);
            lockFailedCounter.increment();
            return false;
        }
    }

    /**
     * 释放分布式锁
     * <p>
     * 【执行流程】
     * 1. 检查当前线程是否持有锁
     * 2. 如果持有，调用 unlock() 释放
     * 3. 如果未持有，记录警告日志（可能是锁已过期）
     * <p>
     * 【注意事项】
     * - 只能释放自己持有的锁，不能释放别人的锁
     * - 如果锁已过期自动释放，unlock() 会抛异常，需要捕获
     *
     * @param key 锁的 Key
     */
    public void unlock(String key) {
        RLock lock = redissonClient.getLock(key);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            } else {
                log.warn("尝试释放未持有的锁: key={}", key);
            }
        } catch (Exception e) {
            log.error("释放分布式锁失败: key={}", key, e);
        }
    }

    // ==================== 原子操作 ====================

    /**
     * 原子自增/自减
     * <p>
     * 【执行流程】
     * 1. 调用 Redis INCRBY 命令（支持正负数）
     * 2. 如果设置了 ttl 且 Key 是新创建的，设置过期时间
     * 3. 返回自增后的值
     * <p>
     * 【注意事项】
     * - INCRBY 是原子操作，天然支持高并发
     * - 如果 Key 不存在，Redis 会初始化为 0 再执行 INCRBY
     * - ttl 只在 Key 新创建时生效（已存在的 Key 不会重置过期时间）
     *
     * @param key Redis Key
     * @param delta 步长（正数自增，负数自减）
     * @param ttl 过期时间（秒），0 表示永不过期
     * @return 自增/自减后的值
     */
    public long incrBy(String key, long delta, int ttl) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, delta);
            if (result == null) {
                throw new CustomerException(ShopCode.REDIS_OPERATION_ERROR);
            }
            // 如果是新创建的 Key（result == delta），设置过期时间
            if (ttl > 0 && result == delta) {
                redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
            }
            return result;
        } catch (CustomerException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis INCRBY 操作失败: key={}, delta={}", key, delta, e);
            errorCounter.increment();
            throw new CustomerException(ShopCode.REDIS_OPERATION_ERROR);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 记录操作耗时
     * <p>
     * 使用 Micrometer Timer 记录操作耗时分布，便于性能分析。
     *
     * @param startMillis 操作开始时间（毫秒时间戳）
     * @param operation 操作名称（get/set/delete/lock 等）
     */
    private void recordDuration(long startMillis, String operation) {
        long duration = System.currentTimeMillis() - startMillis;
        operationTimer.record(duration, TimeUnit.MILLISECONDS);
    }
}
