package org.lee.rocket.train.common.service;

import org.lee.rocket.train.common.constant.JwtConstants;
import org.lee.rocket.train.common.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token 管理服务
 * 基于 Redis 实现 Refresh Token 的存储、黑名单管理和登出功能
 *
 * 【设计说明】
 * - Access Token：JWT 格式，不存 Redis，每次请求只验证签名 + 检查黑名单
 * - Refresh Token：UUID 格式，存 Redis，用于刷新 Access Token
 * - 黑名单：登出时将 Access Token 加入黑名单，TTL = Token 剩余有效期
 *
 * 【为什么用 @Autowired 而不是 @Resource？】
 * Spring Boot 的 RedisAutoConfiguration 会注册两个 Redis 模板 Bean：
 *   - redisTemplate      → 类型为 RedisTemplate<Object, Object>
 *   - stringRedisTemplate → 类型为 StringRedisTemplate
 * @Resource 默认按字段名注入，字段名 "redisTemplate" 会匹配到 RedisTemplate 类型的 Bean，
 * 导致 BeanNotOfRequiredTypeException（类型不匹配）。
 * @Autowired 按类型注入，能精确匹配到 StringRedisTemplate 类型的 Bean。
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private StringRedisTemplate redisTemplate;

    /**
     * 生成 Refresh Token（UUID 格式）
     *
     * @return UUID 格式的 Refresh Token
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 保存 Refresh Token 到 Redis
     *
     * @param userId       用户 ID
     * @param refreshToken Refresh Token（UUID）
     */
    @SuppressWarnings("null")
    public void saveRefreshToken(Long userId, String refreshToken) {
        // Key: refresh:{refreshToken}, Value: userId, Expire: 7天
        String key = JwtConstants.REDIS_REFRESH_PREFIX + refreshToken;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), JwtConstants.REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
    }

    /**
     * 根据 Refresh Token 获取用户 ID
     *
     * @param refreshToken Refresh Token
     * @return 用户 ID，如果不存在返回 null
     */
    public Long getUserIdByRefreshToken(String refreshToken) {
        String key = JwtConstants.REDIS_REFRESH_PREFIX + refreshToken;
        String userId = redisTemplate.opsForValue().get(key);
        return userId != null ? Long.parseLong(userId) : null;
    }

    /**
     * 将 Access Token 加入黑名单
     * 登出时使用，使 Token 立即失效
     *
     * @param token 需要失效的 Access Token
     */
    public void addToBlacklist(String token) {
        // 计算 Token 剩余有效期
        long remainingTime = getRemainingExpiration(token);
        if (remainingTime <= 0) {
            // Token 已过期，不需要加入黑名单
            return;
        }

        // Key: blacklist:{token}, Value: 1, Expire: Token 剩余有效期
        // 过期后自动从黑名单移除，避免 Redis 内存泄漏
        String key = JwtConstants.REDIS_BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", remainingTime, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param token 需要检查的 Token
     * @return true 表示已被拉黑，false 表示正常
     */
    public boolean isBlacklisted(String token) {
        String key = JwtConstants.REDIS_BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 单个用户登出（使当前 Token 失效）
     * 将 Access Token 加入黑名单，删除 Refresh Token
     *
     * @param accessToken 需要失效的 Access Token
     * @param refreshToken 需要删除的 Refresh Token
     */
    public void logout(String accessToken, String refreshToken) {
        // 1. 将 Access Token 加入黑名单，立即失效
        addToBlacklist(accessToken);
        // 2. 从 Redis 中删除 Refresh Token
        if (refreshToken != null) {
            String refreshKey = JwtConstants.REDIS_REFRESH_PREFIX + refreshToken;
            redisTemplate.delete(refreshKey);
        }
    }

    /**
     * 删除 Refresh Token（Token 轮换时使用）
     *
     * @param refreshToken 需要删除的 Refresh Token
     */
    public void deleteRefreshToken(String refreshToken) {
        if (refreshToken != null) {
            String key = JwtConstants.REDIS_REFRESH_PREFIX + refreshToken;
            redisTemplate.delete(key);
        }
    }

    /**
     * 计算 Token 剩余有效期（毫秒）
     *
     * @param token JWT Token
     * @return 剩余有效期（毫秒），如果已过期返回 0
     */
    private long getRemainingExpiration(String token) {
        try {
            Date expiration = JwtUtil.getExpirationFromToken(token);
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (Exception e) {
            // 解析失败，返回 0
            return 0;
        }
    }
}
