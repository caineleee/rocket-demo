package org.lee.rocket.train.common.service;

import jakarta.annotation.Resource;
import org.lee.rocket.train.common.constant.JwtConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Token 管理服务
 * 基于 Redis 实现 Token 的存储、续期、黑名单管理和登出功能
 */
@Service
public class TokenService {

    @Resource
    private StringRedisTemplate redisTemplate;

    /**
     * 保存 Access Token 到 Redis
     * 同时维护用户的 Token 集合（用于多端登录管理）
     *
     * @param userId      用户 ID
     * @param accessToken Access Token
     */
    public void saveAccessToken(Long userId, String accessToken) {
        // Key: token:{accessToken}, Value: userId, Expire: 2小时
        String key = JwtConstants.REDIS_TOKEN_PREFIX + accessToken;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), JwtConstants.ACCESS_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
        
        // 维护用户的 Token 集合，支持多端登录管理
        String userTokensKey = JwtConstants.REDIS_USER_TOKENS_PREFIX + userId;
        redisTemplate.opsForSet().add(userTokensKey, accessToken);
        // 用户 Token 集合的过期时间与 Refresh Token 一致（3天）
        redisTemplate.expire(userTokensKey, JwtConstants.REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
    }

    /**
     * 保存 Refresh Token 到 Redis
     *
     * @param userId       用户 ID
     * @param refreshToken Refresh Token
     */
    public void saveRefreshToken(Long userId, String refreshToken) {
        // Key: refresh:{refreshToken}, Value: userId, Expire: 3天
        String key = JwtConstants.REDIS_REFRESH_PREFIX + refreshToken;
        redisTemplate.opsForValue().set(key, String.valueOf(userId), JwtConstants.REFRESH_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
    }

    /**
     * 根据 Access Token 获取用户 ID
     *
     * @param accessToken Access Token
     * @return 用户 ID，如果不存在返回 null
     */
    public Long getUserIdByAccessToken(String accessToken) {
        String key = JwtConstants.REDIS_TOKEN_PREFIX + accessToken;
        String userId = redisTemplate.opsForValue().get(key);
        return userId != null ? Long.parseLong(userId) : null;
    }

    /**
     * 根据 Refresh Token 获取用户 ID
     *
     * @param refreshToken Refresh Token
     * @return 用户 ID，如果不存在返回 null
     */
    public Long getUserIdByRefreshToken(String refreshToken) {
        // 修复：使用正确的常量名 REDIS_REFRESH_PREFIX
        String key = JwtConstants.REDIS_REFRESH_PREFIX + refreshToken;
        String userId = redisTemplate.opsForValue().get(key);
        return userId != null ? Long.parseLong(userId) : null;
    }

    /**
     * 刷新 Access Token 的过期时间（滑动过期）
     * 用户每次请求时调用，延长 Token 的有效期
     *
     * @param userId      用户 ID
     * @param accessToken Access Token
     */
    public void refreshAccessToken(Long userId, String accessToken) {
        String key = JwtConstants.REDIS_TOKEN_PREFIX + accessToken;
        // 将过期时间重新设置为 2 小时
        redisTemplate.expire(key, JwtConstants.ACCESS_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
    }

    /**
     * 将 Token 加入黑名单
     * 登出时使用，使 Token 立即失效
     *
     * @param token 需要失效的 Token
     */
    public void addToBlacklist(String token) {
        // Key: blacklist:{token}, Value: 1, Expire: 2小时（与 Access Token 有效期一致）
        // 过期后自动从黑名单移除，避免 Redis 内存泄漏
        String key = JwtConstants.REDIS_BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", JwtConstants.ACCESS_TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
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
     * 将 Token 加入黑名单，并从 Redis 中删除
     *
     * @param userId      用户 ID
     * @param accessToken 需要失效的 Access Token
     */
    public void logout(Long userId, String accessToken) {
        // 1. 将 Token 加入黑名单，立即失效
        addToBlacklist(accessToken);
        // 2. 从 Redis 中删除 Token
        String key = JwtConstants.REDIS_TOKEN_PREFIX + accessToken;
        redisTemplate.delete(key);
        // 3. 从用户的 Token 集合中移除
        String userTokensKey = JwtConstants.REDIS_USER_TOKENS_PREFIX + userId;
        redisTemplate.opsForSet().remove(userTokensKey, accessToken);
    }

    /**
     * 用户全部登出（使所有 Token 失效）
     * 修改密码或用户主动退出所有设备时使用
     *
     * @param userId 用户 ID
     */
    public void logoutAll(Long userId) {
        // 获取该用户的所有 Token
        String userTokensKey = JwtConstants.REDIS_USER_TOKENS_PREFIX + userId;
        Set<String> tokens = redisTemplate.opsForSet().members(userTokensKey);
        
        if (tokens != null) {
            // 将所有 Token 加入黑名单并删除
            for (String token : tokens) {
                addToBlacklist(token);
                String tokenKey = JwtConstants.REDIS_TOKEN_PREFIX + token;
                redisTemplate.delete(tokenKey);
            }
            // 删除用户的 Token 集合
            redisTemplate.delete(userTokensKey);
        }
        // 删除 Refresh Token
        String refreshKey = JwtConstants.REDIS_REFRESH_PREFIX + userId;
        redisTemplate.delete(refreshKey);
    }

    /**
     * 检查 Access Token 是否有效
     * 同时检查黑名单和 Redis 中是否存在
     *
     * @param accessToken Access Token
     * @return true 表示有效，false 表示无效或已失效
     */
    public boolean isValidAccessToken(String accessToken) {
        // 1. 检查是否在黑名单中
        if (isBlacklisted(accessToken)) {
            return false;
        }
        // 2. 检查 Redis 中是否存在
        String key = JwtConstants.REDIS_TOKEN_PREFIX + accessToken;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
