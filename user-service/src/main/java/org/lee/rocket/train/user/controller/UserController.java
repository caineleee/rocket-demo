package org.lee.rocket.train.user.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.lee.rocket.train.common.constant.JwtConstants;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.common.service.TokenService;
import org.lee.rocket.train.common.util.JwtUtil;
import org.lee.rocket.train.service.entity.User;
import org.lee.rocket.train.api.IUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 * 提供用户登录、登出、Token 刷新、获取用户信息等接口
 */
@RestController
@RequestMapping("/user-service/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private TokenService tokenService;

    /**
     * BCrypt 密码加密器
     * 用于验证用户密码（数据库中存储的是加密后的密码）
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录接口
     * 验证用户名和密码，生成双 Token（Access Token + Refresh Token）
     *
     * @param loginRequest 登录请求体，包含 userMobile 和 userPassword
     * @return 包含双 Token 的响应结果
     */
    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> loginRequest) {
        String userMobile = loginRequest.get("userMobile");
        String userPassword = loginRequest.get("userPassword");

        // 参数校验
        if (userMobile == null || userMobile.isEmpty() || userPassword == null || userPassword.isEmpty()) {
            return Result.error("用户名或密码不能为空");
        }

        // 根据手机号查询用户
        User user = userService.lambdaQuery().eq(User::getUserMobile, userMobile).one();
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 验证密码（使用 BCrypt 比对）
        // 注意：数据库中存储的是加密后的密码，不能直接比对明文
        if (!passwordEncoder.matches(userPassword, user.getUserPassword())) {
            return Result.error("用户名或密码错误");
        }

        // 生成双 Token
        String accessToken = JwtUtil.generateAccessToken(user.getUserId(), user.getUserName());
        String refreshToken = JwtUtil.generateRefreshToken(user.getUserId());

        // 保存到 Redis
        tokenService.saveAccessToken(user.getUserId(), accessToken);
        tokenService.saveRefreshToken(user.getUserId(), refreshToken);

        // 构建响应
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);

        return Result.success(tokenMap);
    }

    /**
     * 用户登出接口
     * 将当前 Token 加入黑名单，使其立即失效
     *
     * @param request HTTP 请求对象（用于获取 Authorization Header 中的 Token）
     * @return 登出成功的响应结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 从请求头中获取 Token
        String authHeader = request.getHeader(JwtConstants.AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            String token = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());
            // 从 Token 中提取用户 ID
            Long userId = JwtUtil.getUserIdFromToken(token);
            if (userId != null) {
                // 将 Token 加入黑名单并从 Redis 中删除
                tokenService.logout(userId, token);
            }
        }
        return Result.success();
    }

    /**
     * 刷新 Token 接口
     * 使用 Refresh Token 获取新的双 Token（Access Token + Refresh Token）
     * 用于 Access Token 过期后，用户无需重新输入密码即可获取新 Token
     *
     * @param refreshRequest 刷新请求体，包含 refreshToken
     * @return 包含新双 Token 的响应结果
     */
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestBody Map<String, String> refreshRequest) {
        String refreshToken = refreshRequest.get("refreshToken");

        // 参数校验
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Result.error("Refresh Token不能为空");
        }

        // 验证 Refresh Token 签名
        if (!JwtUtil.validateToken(refreshToken)) {
            return Result.error("Refresh Token无效");
        }

        // 检查 Refresh Token 是否过期
        if (JwtUtil.isTokenExpired(refreshToken)) {
            return Result.error("Refresh Token已过期，请重新登录");
        }

        // 从 Redis 中验证 Refresh Token 是否存在
        Long userId = tokenService.getUserIdByRefreshToken(refreshToken);
        if (userId == null) {
            return Result.error("Refresh Token无效");
        }

        // 查询用户信息
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 生成新的双 Token
        String newAccessToken = JwtUtil.generateAccessToken(userId, user.getUserName());
        String newRefreshToken = JwtUtil.generateRefreshToken(userId);

        // 更新 Redis 中的 Token
        tokenService.saveAccessToken(userId, newAccessToken);
        tokenService.saveRefreshToken(userId, newRefreshToken);

        // 构建响应
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", newAccessToken);
        tokenMap.put("refreshToken", newRefreshToken);

        return Result.success(tokenMap);
    }

    /**
     * 获取当前用户信息接口
     * 从 Request 属性中获取用户 ID（由 JwtInterceptor 注入），查询并返回用户信息
     *
     * @param request HTTP 请求对象（用于获取用户 ID）
     * @return 用户信息（不包含密码）
     */
    @GetMapping("/info")
    public Result<User> getUserInfo(HttpServletRequest request) {
        // 从 Request 属性中获取用户 ID（由 JwtInterceptor 在验证 Token 后注入）
        Long userId = (Long) request.getAttribute(JwtConstants.USER_ID_KEY);
        User user = userService.getById(userId);
        // 安全处理：不返回密码字段
        if (user != null) {
            user.setUserPassword(null);
        }
        return Result.success(user);
    }
}
