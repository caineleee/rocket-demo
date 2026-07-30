package org.lee.rocket.train.common.constant;

/**
 * JWT 认证常量配置类
 * 集中管理 JWT 相关的常量定义，便于统一维护和修改
 */
public class JwtConstants {

    private JwtConstants() {
    }

    /**
     * JWT 签名密钥
     * 生产环境应从配置文件或密钥管理服务中获取，此处为演示使用硬编码
     */
    public static final String SECRET_KEY = "rocket-demo-jwt-secret-key-2026";

    /**
     * Access Token 过期时间（毫秒）
     * 默认 30 分钟：30 * 60 * 1000 = 1800000
     */
    public static final long ACCESS_TOKEN_EXPIRE_TIME = 1800000L;

    /**
     * Refresh Token 过期时间（毫秒）
     * 默认 7 天：7 * 24 * 60 * 60 * 1000 = 604800000
     */
    public static final long REFRESH_TOKEN_EXPIRE_TIME = 604800000L;

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
