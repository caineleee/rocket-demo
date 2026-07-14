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
 */
public class JwtUtil {

    /**
     * 使用 HMAC-SHA256 算法生成签名密钥
     * 密钥来源：JwtConstants.SECRET_KEY
     */
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(JwtConstants.SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    private JwtUtil() {
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
                .expiration(new Date(System.currentTimeMillis() + JwtConstants.ACCESS_TOKEN_EXPIRE_TIME))  // 设置过期时间（2小时）
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
                .expiration(new Date(System.currentTimeMillis() + JwtConstants.REFRESH_TOKEN_EXPIRE_TIME))  // 设置过期时间（3天）
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
}
