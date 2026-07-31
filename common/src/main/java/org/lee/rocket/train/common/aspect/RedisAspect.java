package org.lee.rocket.train.common.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.annotation.*;
import org.lee.rocket.train.common.constant.RedisAopConstants;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.exception.CustomerException;
import org.lee.rocket.train.common.service.RedisOperationService;
import org.lee.rocket.train.common.spel.SpelSecurityFilter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Redis AOP 核心切面
 * <p>
 * 拦截所有带 Redis 注解的方法，根据注解类型分发到不同的处理器。
 * <p>
 * 【架构设计】
 * 注解层 → RedisAspect（路由） → RedisOperationService（执行） → Redis/Redisson
 * <p>
 * 【处理流程】
 * 1. 拦截方法调用
 * 2. 识别方法上的 Redis 注解类型
 * 3. 解析 SpEL 表达式生成 Redis Key
 * 4. 根据注解类型执行对应的 Redis 操作
 * 5. 处理异常（根据 failStrategy 决定降级还是抛异常）
 * 6. 返回方法执行结果
 * <p>
 * 【洋葱模型】
 * RedisAspect 是"洋葱"的最外层：
 * - 前置处理：加锁、读缓存、计数器校验
 * - 执行方法：joinPoint.proceed()
 * - 后置处理：写缓存、删缓存、释放锁
 *
 * @author lee
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RedisAspect {

    private final RedisOperationService redisOperationService;

    // ==================== @RedisLock 处理 ====================

    /**
     * 处理 @RedisLock 注解
     * <p>
     * 【执行流程】（洋葱模型）
     * 前置：解析 Key → 尝试获取锁 → 获取失败则根据策略处理
     * 执行：joinPoint.proceed()（业务方法）
     * 后置：finally 块中释放锁
     * <p>
     * 【关键考量】
     * - 锁必须在 finally 中释放，即使业务方法抛异常也要释放
     * - 获取锁失败时，FAIL_FAST 直接抛异常，FAIL_SAFE 降级执行
     * - 锁的 Key 格式：{prefix}:{类名.方法名}:{SpEL解析结果}
     *
     * @param joinPoint AOP 连接点
     * @param redisLock 注解实例
     * @return 方法执行结果
     * @throws Throwable 业务异常或锁获取失败异常
     */
    @Around("@annotation(redisLock)")
    public Object handleRedisLock(ProceedingJoinPoint joinPoint, RedisLock redisLock) throws Throwable {
        // 1. 解析 SpEL 表达式，生成 Redis Key
        String key = buildKey(redisLock.prefix(), redisLock.key(), joinPoint);

        // 2. 尝试获取分布式锁
        boolean acquired = redisOperationService.tryLock(
                key,
                redisLock.waitTime(),
                redisLock.leaseTime()
        );

        if (!acquired) {
            // 3. 获取锁失败，根据策略处理
            if (redisLock.failStrategy() == FailStrategy.FAIL_FAST) {
                // FAIL_FAST：直接抛异常，阻止方法执行
                log.warn("分布式锁获取失败（FAIL_FAST）: key={}", key);
                throw new CustomerException(ResultCode.REDIS_LOCK_ACQUIRE_FAIL);
            } else {
                // FAIL_SAFE：记录日志，降级执行（不推荐，失去锁的意义）
                log.warn("分布式锁获取失败（FAIL_SAFE 降级执行）: key={}", key);
            }
        }

        // 4. 执行业务方法
        try {
            return joinPoint.proceed();
        } finally {
            // 5. 释放锁（无论成功还是异常都要释放）
            if (acquired) {
                redisOperationService.unlock(key);
            }
        }
    }

    // ==================== @RedisIncr 处理 ====================

    /**
     * 处理 @RedisIncr 注解
     * <p>
     * 【执行流程】
     * 前置：解析 Key → 执行 INCRBY → 检查是否超限
     * 执行：joinPoint.proceed()（业务方法）
     * 后置：无（计数器已在 Redis 中更新）
     * <p>
     * 【关键考量】
     * - INCRBY 是原子操作，天然支持高并发
     * - 超过 maxCount 或低于 minCount 时，根据 failStrategy 处理
     * - ttl 只在 Key 新创建时生效（已存在的 Key 不会重置过期时间）
     *
     * @param joinPoint AOP 连接点
     * @param redisIncr 注解实例
     * @return 方法执行结果
     * @throws Throwable 业务异常或计数器超限异常
     */
    @Around("@annotation(redisIncr)")
    public Object handleRedisIncr(ProceedingJoinPoint joinPoint, RedisIncr redisIncr) throws Throwable {
        // 1. 解析 SpEL 表达式，生成 Redis Key
        String key = buildKey(redisIncr.prefix(), redisIncr.key(), joinPoint);

        // 2. 执行原子自增/自减
        long currentValue = redisOperationService.incrBy(
                key,
                redisIncr.delta(),
                redisIncr.ttl()
        );

        // 3. 检查是否超过上限
        if (currentValue > redisIncr.maxCount()) {
            log.warn("计数器超过上限: key={}, value={}, maxCount={}", key, currentValue, redisIncr.maxCount());
            if (redisIncr.failStrategy() == FailStrategy.FAIL_FAST) {
                throw new CustomerException(ResultCode.REDIS_INCR_EXCEED_MAX);
            }
            // FAIL_SAFE：记录日志，继续执行
        }

        // 4. 检查是否低于下限
        if (currentValue < redisIncr.minCount()) {
            log.warn("计数器低于下限: key={}, value={}, minCount={}", key, currentValue, redisIncr.minCount());
            if (redisIncr.failStrategy() == FailStrategy.FAIL_FAST) {
                throw new CustomerException(ResultCode.REDIS_INCR_BELOW_MIN);
            }
            // FAIL_SAFE：记录日志，继续执行
        }

        // 5. 执行业务方法
        return joinPoint.proceed();
    }

    // ==================== @RedisGet 处理 ====================

    /**
     * 处理 @RedisGet 注解
     * <p>
     * 【执行流程】
     * 前置：解析 Key → 从 Redis 读取 → 反序列化 → 注入到方法参数
     * 执行：joinPoint.proceed(args)（使用修改后的参数执行方法）
     * 后置：无
     * <p>
     * 【关键考量】
     * - 从 Redis 读出的值注入到指定参数，其他参数不变
     * - 如果 Redis 未命中，根据 failStrategy 决定注入 null 还是抛异常
     * - 需要修改 args 数组，然后传给 proceed(args)
     *
     * @param joinPoint AOP 连接点
     * @param redisGet 注解实例
     * @return 方法执行结果
     * @throws Throwable 业务异常或缓存未命中异常
     */
    @Around("@annotation(redisGet)")
    public Object handleRedisGet(ProceedingJoinPoint joinPoint, RedisGet redisGet) throws Throwable {
        // 1. 解析 SpEL 表达式，生成 Redis Key
        String key = buildKey(redisGet.prefix(), redisGet.key(), joinPoint);

        // 2. 确定反序列化目标类型
        Class<?> targetType = resolveTargetType(redisGet, joinPoint, redisGet.injectParam());

        // 3. 从 Redis 读取并反序列化
        Object cachedValue = redisOperationService.get(key, targetType);

        if (cachedValue == null) {
            // 缓存未命中
            if (redisGet.failStrategy() == FailStrategy.FAIL_FAST) {
                // FAIL_FAST：直接抛异常，阻止方法执行
                log.warn("RedisGet 缓存未命中（FAIL_FAST）: key={}", key);
                throw new CustomerException(ResultCode.REDIS_GET_MISS_FAST_FAIL);
            }
            // DB_ONLY 或 FAIL_SAFE：注入 null，方法继续执行
            log.debug("RedisGet 缓存未命中（注入 null）: key={}", key);
        }

        // 4. 将缓存值注入到方法参数
        Object[] args = joinPoint.getArgs();
        int injectIndex = findParamIndex(joinPoint, redisGet.injectParam());
        if (injectIndex >= 0) {
            args[injectIndex] = cachedValue;
        }

        // 5. 使用修改后的参数执行方法
        return joinPoint.proceed(args);
    }

    // ==================== @RedisSet 处理 ====================

    /**
     * 处理 @RedisSet 注解
     * <p>
     * 【执行流程】（洋葱模型）
     * 前置：无（不读缓存）
     * 执行：joinPoint.proceed()（业务方法）
     * 后置：获取返回值 → 检查条件 → 序列化 → 写入 Redis
     * <p>
     * 【关键考量】
     * - 先执行业务方法，再将结果写入 Redis
     * - condition 为空时始终写入，不为空时只有条件为 true 才写入
     * - 大 Key 检测在 RedisOperationService.set() 中处理
     *
     * @param joinPoint AOP 连接点
     * @param redisSet 注解实例
     * @return 方法执行结果
     * @throws Throwable 业务异常
     */
    @Around("@annotation(redisSet)")
    public Object handleRedisSet(ProceedingJoinPoint joinPoint, RedisSet redisSet) throws Throwable {
        // 1. 执行业务方法
        Object result = joinPoint.proceed();

        // 2. 检查条件表达式（如果设置了 condition）
        if (!RedisAopConstants.DEFAULT_CONDITION.equals(redisSet.condition())) {
            Object conditionResult = SpelSecurityFilter.evaluate(
                    redisSet.condition(),
                    getMethod(joinPoint),
                    joinPoint.getArgs()
            );
            // 将 #result 绑定到上下文，供 condition 使用
            if (Boolean.FALSE.equals(conditionResult)) {
                log.debug("RedisSet 条件不满足，跳过写入: condition={}", redisSet.condition());
                return result;
            }
        }

        // 3. 解析 SpEL 表达式，生成 Redis Key
        String key = buildKeyWithResult(redisSet.prefix(), redisSet.key(), joinPoint, result);

        // 4. 写入 Redis（大 Key 检测在 RedisOperationService 中处理）
        try {
            redisOperationService.set(key, result, redisSet.ttl(), redisSet.maxSize());
        } catch (Exception e) {
            if (redisSet.failStrategy() == FailStrategy.FAIL_FAST) {
                throw e;
            }
            // FAIL_SAFE：记录日志，不影响业务
            log.warn("RedisSet 写入失败（FAIL_SAFE）: key={}", key, e);
        }

        return result;
    }

    // ==================== @RedisDel 处理 ====================

    /**
     * 处理 @RedisDel 注解
     * <p>
     * 【执行流程】（洋葱模型）
     * 前置：无
     * 执行：joinPoint.proceed()（业务方法）
     * 后置：解析 Key → 删除 Redis Key
     * <p>
     * 【关键考量】
     * - 先执行业务方法，再删除缓存（保证业务成功后才清理缓存）
     * - 支持删除多个 Key（keys 数组）
     * - 删除失败不影响业务（FAIL_SAFE）
     *
     * @param joinPoint AOP 连接点
     * @param redisDel 注解实例
     * @return 方法执行结果
     * @throws Throwable 业务异常
     */
    @Around("@annotation(redisDel)")
    public Object handleRedisDel(ProceedingJoinPoint joinPoint, RedisDel redisDel) throws Throwable {
        // 1. 执行业务方法
        Object result = joinPoint.proceed();

        // 2. 解析并删除 Key
        try {
            String[] keys = redisDel.keys();
            if (keys.length > 0) {
                // 使用 keys 数组（多 Key 删除）
                for (String keyExpr : keys) {
                    String key = buildKey(redisDel.prefix(), keyExpr, joinPoint);
                    redisOperationService.delete(key);
                }
            } else if (!redisDel.key().isEmpty()) {
                // 使用单个 key
                String key = buildKey(redisDel.prefix(), redisDel.key(), joinPoint);
                redisOperationService.delete(key);
            }
        } catch (Exception e) {
            if (redisDel.failStrategy() == FailStrategy.FAIL_FAST) {
                throw e;
            }
            // FAIL_SAFE：记录日志，不影响业务
            log.warn("RedisDel 删除失败（FAIL_SAFE）", e);
        }

        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建 Redis Key
     * <p>
     * Key 格式：{prefix}:{类名.方法名}:{SpEL解析结果}
     * <p>
     * 例如：
     * - prefix = "goods:detail", key = "#id"
     * - 方法 = GoodsService.getDetail(Long id), id = 123
     * - 最终 Key = goods:detail:GoodsService.getDetail:123
     *
     * @param prefix Key 前缀
     * @param keyExpr SpEL 表达式
     * @param joinPoint AOP 连接点
     * @return 完整的 Redis Key
     */
    private String buildKey(String prefix, String keyExpr, ProceedingJoinPoint joinPoint) {
        Method method = getMethod(joinPoint);
        Object keyValue = SpelSecurityFilter.evaluate(keyExpr, method, joinPoint.getArgs());
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix).append(":");
        }
        sb.append(className).append(".").append(methodName).append(":");
        sb.append(keyValue);
        return sb.toString();
    }

    /**
     * 构建 Redis Key（带返回值引用）
     * <p>
     * 用于 @RedisSet，Key 表达式可能引用 #result（方法返回值）。
     * 需要将 result 绑定到 SpEL 上下文中。
     *
     * @param prefix Key 前缀
     * @param keyExpr SpEL 表达式（可能包含 #result）
     * @param joinPoint AOP 连接点
     * @param result 方法返回值
     * @return 完整的 Redis Key
     */
    private String buildKeyWithResult(String prefix, String keyExpr, ProceedingJoinPoint joinPoint, Object result) {
        Method method = getMethod(joinPoint);
        Object[] args = joinPoint.getArgs();

        // 如果表达式包含 #result，需要将 result 作为额外参数传入
        // 简化处理：将 result 追加到 args 末尾，参数名为 "result"
        // 注意：这里需要扩展 SpelSecurityFilter 支持 #result 变量
        Object keyValue;
        if (keyExpr.contains("#result")) {
            // 创建扩展的评估上下文，绑定 #result
            keyValue = evaluateWithResult(keyExpr, method, args, result);
        } else {
            keyValue = SpelSecurityFilter.evaluate(keyExpr, method, args);
        }

        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix).append(":");
        }
        sb.append(className).append(".").append(methodName).append(":");
        sb.append(keyValue);
        return sb.toString();
    }

    /**
     * 扩展 SpEL 评估，支持 #result 变量
     * <p>
     * 在标准 SpEL 评估基础上，额外绑定 #result 变量（方法返回值）。
     *
     * @param expression SpEL 表达式
     * @param method 目标方法
     * @param args 方法参数
     * @param result 方法返回值
     * @return 表达式解析结果
     */
    private Object evaluateWithResult(String expression, Method method, Object[] args, Object result) {
        // 复用 SpelSecurityFilter 的安全检查
        org.springframework.expression.ExpressionParser parser =
                new org.springframework.expression.spel.standard.SpelExpressionParser();
        org.springframework.expression.spel.support.StandardEvaluationContext context =
                new org.springframework.expression.spel.support.StandardEvaluationContext();

        // 绑定方法参数
        if (method != null && args != null) {
            for (int i = 0; i < method.getParameterCount(); i++) {
                context.setVariable("arg" + i, args[i]);
            }
        }
        // 绑定 #result 变量
        context.setVariable("result", result);

        try {
            return parser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            log.error("SpEL 表达式解析失败（含 #result）: expression={}", expression, e);
            throw new CustomerException(ResultCode.REDIS_OPERATION_ERROR);
        }
    }

    /**
     * 获取目标方法
     * <p>
     * 从 AOP 连接点中提取 Method 对象，用于 SpEL 参数名解析。
     *
     * @param joinPoint AOP 连接点
     * @return 目标方法
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }

    /**
     * 查找方法参数索引
     * <p>
     * 根据参数名查找其在方法参数列表中的索引位置。
     * 用于 @RedisGet 的参数注入。
     *
     * @param joinPoint AOP 连接点
     * @param paramName 参数名
     * @return 参数索引（-1 表示未找到）
     */
    private int findParamIndex(ProceedingJoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                return i;
            }
        }
        log.warn("未找到参数: paramName={}, 可用参数={}", paramName,
                java.util.Arrays.stream(parameters).map(Parameter::getName).toList());
        return -1;
    }

    /**
     * 解析反序列化目标类型
     * <p>
     * 【解析逻辑】
     * 1. 如果注解中显式指定了 deserializeAs（非 Void.class），使用指定类型
     * 2. 否则，从 injectParam 对应的方法参数类型自动推断
     * <p>
     * 【示例】
     * - @RedisGet(injectParam = "user", deserializeAs = UserDTO.class) → UserDTO.class
     * - @RedisGet(injectParam = "user") → 从方法参数 UserDTO user 推断 → UserDTO.class
     *
     * @param redisGet 注解实例
     * @param joinPoint AOP 连接点
     * @param paramName 注入参数名
     * @return 目标类型
     */
    private Class<?> resolveTargetType(RedisGet redisGet, ProceedingJoinPoint joinPoint, String paramName) {
        // 1. 如果显式指定了类型，直接使用
        if (redisGet.deserializeAs() != Void.class) {
            return redisGet.deserializeAs();
        }

        // 2. 否则从方法参数类型自动推断
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        for (Parameter param : parameters) {
            if (param.getName().equals(paramName)) {
                return param.getType();
            }
        }

        // 3. 未找到参数，默认使用 Object.class
        log.warn("未找到注入参数: paramName={}, 使用 Object.class", paramName);
        return Object.class;
    }
}
