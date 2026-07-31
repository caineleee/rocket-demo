package org.lee.rocket.train.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.lee.rocket.train.common.constant.JwtConstants;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 * 提供 JWT Token 的生成、解析、验证等功能
 *
 * 【密钥外部化说明】
 * 密钥、Access/Refresh Token 过期时间均从系统属性读取（由 spring-dotenv 从 .env 注入，
 * 见 .env 的 JWT_SECRET / JWT_ACCESS_EXPIRE / JWT_REFRESH_EXPIRE），不再硬编码到 Java 源码中，
 * 可在不重新编译的情况下调整。
 *
 * 【为什么用 System.getProperty() 而不是 @Value 注入？】
 * JwtUtil 是纯静态工具类，被 Gateway（WebFlux）和各业务服务共用。Gateway 的启动类在
 * org.lee.rocket.train.gateway 包下，默认只扫描该包及其子包，不会扫描 common 包，因此
 * common 里的 @Configuration / @Component 不会在 Gateway 中被实例化。如果 JwtUtil 改成依赖
 * Spring Bean 注入密钥，Gateway 里 JwtUtil 会因未被配置而 SECRET_KEY 为 null，认证全线 500。
 * 改用 System.getProperty() 读取系统属性，零 Spring 依赖，Gateway 与所有服务都通用。
 *
 * 【为什么用 System.getProperty() 而不是 System.getenv()？】
 * System.getenv() 只能读 OS 真实环境变量（需 shell export / source .env 注入），Java 运行时无法修改；
 * spring-dotenv 读取 .env 后通过 EnvironmentPostProcessor 把变量灌进 System.getProperties()
 * （需在 Nacos 共享配置开启 springdotenv.export-to-system-properties=true），但不能灌进 getenv()。
 * 因此改用 System.getProperty()，配合 spring-dotenv 即可"零 source 启动"。
 *
 * 【时序安全性】
 * spring-dotenv 在 EnvironmentPostProcessor 阶段执行（Spring Boot 最早的扩展点，早于所有 Bean 实例化）；
 * JwtUtil 的 static final 字段在类加载时初始化，而类加载发生在调用方 Bean 实例化时，远晚于 export，
 * 故 System.getProperty() 能取到 .env 的值。
 * application.yml 里也保留了 jwt.secret 等配置项（${JWT_SECRET:...}）便于查阅，二者读取同一来源。
 */
public class JwtUtil {

    /**
     * JWT 签名密钥（原始字符串，从系统属性 JWT_SECRET 读取，由 spring-dotenv 从 .env 注入）
     *
     * 【长度要求】HMAC-SHA256 算法要求密钥长度 >= 32 字节（256 位），否则 jjwt 在
     * Keys.hmacShaKeyFor() 阶段抛出 WeakKeyException，导致 JwtUtil 静态初始化失败
     * （ExceptionInInitializerError），登录等所有 Token 生成接口直接 500。
     * 此前 "rocket-demo-jwt-secret-key-2026" 仅 31 字节（248 位），差 1 字节触发该异常，
     * 已补齐至 38 字节（304 位）。
     *
     * 【兜底值】未设置 JWT_SECRET 系统属性时使用开发兜底值；生产环境必须通过 .env / 启动参数
     * 设置 JWT_SECRET 覆盖此值，避免密钥泄露。
     */
    private static final String SECRET_KEY_RAW = System.getProperty("JWT_SECRET") != null
            ? System.getProperty("JWT_SECRET")
            : "rocket-demo-jwt-secret-key-2026-secure";

    /**
     * 使用 HMAC-SHA256 算法生成签名密钥
     * 密钥来源：系统属性 JWT_SECRET（由 spring-dotenv 从 .env 注入）
     */
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET_KEY_RAW.getBytes(StandardCharsets.UTF_8));

    /**
     * Access Token 过期时间（毫秒），从系统属性 JWT_ACCESS_EXPIRE 读取，默认 30 分钟
     */
    private static final long ACCESS_TOKEN_EXPIRE_TIME = parseLongEnv("JWT_ACCESS_EXPIRE", 1800000L);

    /**
     * Refresh Token 过期时间（毫秒），从系统属性 JWT_REFRESH_EXPIRE 读取，默认 7 天
     */
    private static final long REFRESH_TOKEN_EXPIRE_TIME = parseLongEnv("JWT_REFRESH_EXPIRE", 604800000L);

    private JwtUtil() {
    }

    /**
     * 解析 long 类型系统属性，解析失败或未设置时返回默认值
     *
     * @param propName     系统属性名
     * @param defaultValue 默认值
     * @return 解析后的 long 值
     */
    private static long parseLongEnv(String propName, long defaultValue) {
        String value = System.getProperty(propName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            // 系统属性格式非法时回退默认值，避免启动失败
            return defaultValue;
        }
    }

    /**
     * 获取 Access Token 过期时间（毫秒）
     *
     * @return Access Token 过期时间
     */
    public static long getAccessTokenExpireTime() {
        return ACCESS_TOKEN_EXPIRE_TIME;
    }

    /**
     * 获取 Refresh Token 过期时间（毫秒）
     *
     * @return Refresh Token 过期时间
     */
    public static long getRefreshTokenExpireTime() {
        return REFRESH_TOKEN_EXPIRE_TIME;
    }

    /**
     * 生成 Access Token
     *
     * @param userId   用户 ID
     * @param userName 用户名称
     * @return Access Token 字符串
     */
    public static String generateAccessToken(Long userId, String userName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtConstants.USER_ID_KEY, userId);
        claims.put(JwtConstants.USER_NAME_KEY, userName);

        return Jwts.builder()
                .claims(claims)                    // 设置 Payload 中的自定义数据
                .subject(String.valueOf(userId))   // 设置 subject（通常为用户 ID）
                .issuedAt(new Date())              // 设置签发时间
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE_TIME))  // 设置过期时间（默认 30 分钟）
                .signWith(SECRET_KEY)              // 使用密钥签名
                .compact();                        // 生成 Token 字符串
    }

    /**
     * 生成 Refresh Token
     * Refresh Token 只包含用户 ID，用于刷新 Access Token
     *
     * @param userId 用户 ID
     * @return Refresh Token 字符串
     */
    public static String generateRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtConstants.USER_ID_KEY, userId);

        return Jwts.builder()
                .claims(claims)                    // 设置 Payload
                .subject(String.valueOf(userId))   // 设置 subject
                .issuedAt(new Date())              // 设置签发时间
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE_TIME))  // 设置过期时间（默认 7 天）
                .signWith(SECRET_KEY)              // 使用密钥签名
                .compact();                        // 生成 Token 字符串
    }

    /**
     * 解析 Token 获取 Claims（Payload）
     *
     * @param token JWT Token 字符串
     * @return Claims 对象，包含用户信息
     * @throws JwtException        Token 无效时抛出
     * @throws IllegalArgumentException Token 为空或格式错误时抛出
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)           // 设置验证密钥
                .build()                          // 构建解析器
                .parseSignedClaims(token)         // 解析并验证签名
                .getPayload();                    // 获取 Payload
    }

    /**
     * 从 Token 中提取用户 ID
     *
     * @param token JWT Token 字符串
     * @return 用户 ID，如果无法提取返回 null
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.USER_ID_KEY, Long.class);
    }

    /**
     * 从 Token 中提取用户名称
     *
     * @param token JWT Token 字符串
     * @return 用户名称，如果无法提取返回 null
     */
    public static String getUserNameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get(JwtConstants.USER_NAME_KEY, String.class);
    }

    /**
     * 验证 Token 是否有效（签名是否正确）
     *
     * @param token JWT Token 字符串
     * @return true 表示有效，false 表示无效
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException: 签名无效、格式错误等
            // IllegalArgumentException: 参数为空等
            return false;
        }
    }

    /**
     * 检查 Token 是否已过期
     *
     * @param token JWT Token 字符串
     * @return true 表示已过期，false 表示未过期
     */
    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            // 解析失败视为已过期
            return true;
        }
    }

    /**
     * 从 Token 中获取过期时间
     *
     * @param token JWT Token 字符串
     * @return 过期时间
     */
    public static Date getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration();
    }
}
