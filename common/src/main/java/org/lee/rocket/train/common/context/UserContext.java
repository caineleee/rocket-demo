package org.lee.rocket.train.common.context;

/**
 * 用户上下文工具类
 * 基于 ThreadLocal 存储当前请求的用户信息
 *
 * 【使用场景】
 * 在 Controller 或 Service 中获取当前登录用户信息：
 * Long userId = UserContext.getUserId();
 * String userName = UserContext.getUserName();
 *
 * 【注意事项】
 * 1. 必须在请求结束后调用 clear() 方法清理 ThreadLocal，防止线程复用导致数据串线
 * 2. 由 UserInfoInterceptor 在 preHandle 中设置，在 afterCompletion 中清理
 *
 * @author rocket-demo
 * @since 1.0.0
 */
public class UserContext {

    /**
     * 存储当前请求的用户 ID
     */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /**
     * 存储当前请求的用户名
     */
    private static final ThreadLocal<String> USER_NAME = new ThreadLocal<>();

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
