package org.lee.rocket.train.common.constant;

/**
 * JWT 认证常量配置类
 * 集中管理 JWT 相关的结构性常量定义（前缀、Header 名、Payload key、Redis key 前缀等）。
 *
 * 【注意】密钥与过期时间属于"敏感/可变配置"，已从本类移除并外部化到环境变量（见 .env 的
 * JWT_SECRET / JWT_ACCESS_EXPIRE / JWT_REFRESH_EXPIRE），由 JwtUtil 读取。
 * 这里只保留不会随环境变化的结构性常量。
 */
public class JwtConstants {

    private JwtConstants() {
    }

    /**
     * Token 前缀（Authorization Header 中使用）
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 认证请求头名称
     */
    public static final String AUTH_HEADER = "Authorization";

    /**
     * JWT Payload 中用户 ID 的 key
     */
    public static final String USER_ID_KEY = "userId";

    /**
     * JWT Payload 中用户名称的 key
     */
    public static final String USER_NAME_KEY = "userName";

    /**
     * Redis 中 Refresh Token 的 key 前缀
     */
    public static final String REDIS_REFRESH_PREFIX = "refresh:";

    /**
     * Redis 中 Token 黑名单的 key 前缀
     */
    public static final String REDIS_BLACKLIST_PREFIX = "blacklist:";
}
