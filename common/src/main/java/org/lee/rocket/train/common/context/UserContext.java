package org.lee.rocket.train.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 用户上下文工具类
 * 基于 TransmittableThreadLocal（TTL）存储当前请求的用户信息
 *
 * 【为什么用 TTL 而不是普通 ThreadLocal？】
 * 普通 ThreadLocal 在线程池场景下无法自动传递上下文：
 *   - 线程池复用线程，子线程无法继承父线程的 ThreadLocal 值
 *   - 异步任务（@Async、CompletableFuture、线程池）中 UserContext.getUserId() 返回 null
 *
 * TTL 解决了这个问题：
 *   - 任务提交到线程池时，自动捕获父线程的上下文
 *   - 子线程执行时，自动恢复父线程的上下文
 *   - 任务完成后，自动清理子线程的上下文
 *
 * 【使用前提】
 * 线程池必须使用 TtlExecutors 包装，TTL 才能生效：
 *   ExecutorService executor = TtlExecutors.getTtlExecutorService(Executors.newFixedThreadPool(10));
 *
 * 或者使用 TTL 提供的 Java Agent（推荐，无需修改代码）：
 *   -javaagent:transmittable-thread-local-2.14.3.jar
 *
 * 【使用场景】
 * 在 Controller 或 Service 中获取当前登录用户信息：
 *   Long userId = UserContext.getUserId();
 *   String userName = UserContext.getUserName();
 *
 * 【注意事项】
 * 1. 必须在请求结束后调用 clear() 方法清理，防止线程复用导致数据串线
 * 2. 由 UserInfoInterceptor 在 preHandle 中设置，在 afterCompletion 中清理
 * 3. Dubbo ProviderFilter 在 finally 中清理
 *
 * @see com.alibaba.ttl.TransmittableThreadLocal
 */
public class UserContext {

    /**
     * 存储当前请求的用户 ID
     * 使用 TransmittableThreadLocal 替代 ThreadLocal，支持线程池场景下的上下文自动传递
     */
    private static final TransmittableThreadLocal<Long> USER_ID = new TransmittableThreadLocal<>();

    /**
     * 存储当前请求的用户名
     * 使用 TransmittableThreadLocal 替代 ThreadLocal，支持线程池场景下的上下文自动传递
     */
    private static final TransmittableThreadLocal<String> USER_NAME = new TransmittableThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置用户 ID
     *
     * @param userId 用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID，如果未设置返回 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 设置用户名
     *
     * @param userName 用户名
     */
    public static void setUserName(String userName) {
        USER_NAME.set(userName);
    }

    /**
     * 获取用户名
     *
     * @return 用户名，如果未设置返回 null
     */
    public static String getUserName() {
        return USER_NAME.get();
    }

    /**
     * 清理 ThreadLocal
     * 必须在请求结束后调用，防止线程复用导致数据串线
     */
    public static void clear() {
        USER_ID.remove();
        USER_NAME.remove();
    }
}
