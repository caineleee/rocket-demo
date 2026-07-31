package org.lee.rocket.train.user.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.lee.rocket.train.common.constant.JwtConstants;
import org.lee.rocket.train.common.context.UserContext;
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
@RequestMapping("/user")
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
        @SuppressWarnings("null")
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
        // Refresh Token 使用 UUID 格式（不透明 Token），不存 Redis
        String refreshToken = tokenService.generateRefreshToken();

        // 只保存 Refresh Token 到 Redis（Access Token 不存 Redis，只验证签名 + 检查黑名单）
        tokenService.saveRefreshToken(user.getUserId(), refreshToken);

        // 构建响应
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);

        return Result.success(tokenMap);
    }

    /**
     * 用户登出接口
     * 将当前 Access Token 加入黑名单，删除 Refresh Token
     *
     * @param request HTTP 请求对象（用于获取 Authorization Header 中的 Token）
     * @return 登出成功的响应结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 从请求头中获取 Access Token
        String authHeader = request.getHeader(JwtConstants.AUTH_HEADER);
        String refreshToken = request.getHeader("X-Refresh-Token");
        
        if (authHeader != null && authHeader.startsWith(JwtConstants.TOKEN_PREFIX)) {
            String accessToken = authHeader.substring(JwtConstants.TOKEN_PREFIX.length());
            // 将 Access Token 加入黑名单，删除 Refresh Token
            tokenService.logout(accessToken, refreshToken);
        }
        return Result.success();
    }

    /**
     * 刷新 Token 接口
     * 使用 Refresh Token 获取新的双 Token（Access Token + Refresh Token）
     * 实现 Token 轮换：旧的 Refresh Token 失效，生成新的 Refresh Token
     *
     * @param refreshRequest 刷新请求体，包含 refreshToken
     * @return 包含新双 Token 的响应结果
     */
    @PostMapping("/refresh")
    public Result<Map<String, String>> refresh(@RequestBody Map<String, String> refreshRequest) {
        String oldRefreshToken = refreshRequest.get("refreshToken");

        // 参数校验
        if (oldRefreshToken == null || oldRefreshToken.isEmpty()) {
            return Result.error("Refresh Token不能为空");
        }

        // 从 Redis 中验证 Refresh Token 是否存在（UUID 格式，不需要验证签名）
        Long userId = tokenService.getUserIdByRefreshToken(oldRefreshToken);
        if (userId == null) {
            return Result.error("Refresh Token无效或已过期，请重新登录");
        }

        // 查询用户信息
        User user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // Token 轮换：删除旧的 Refresh Token
        tokenService.deleteRefreshToken(oldRefreshToken);

        // 生成新的双 Token
        String newAccessToken = JwtUtil.generateAccessToken(userId, user.getUserName());
        String newRefreshToken = tokenService.generateRefreshToken();

        // 保存新的 Refresh Token 到 Redis
        tokenService.saveRefreshToken(userId, newRefreshToken);

        // 构建响应
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", newAccessToken);
        tokenMap.put("refreshToken", newRefreshToken);

        return Result.success(tokenMap);
    }

    /**
     * 获取当前用户信息接口
     * 从 UserContext 中获取用户 ID（由 UserInfoInterceptor 从请求头读取并存入）
     *
     * @return 用户信息（不包含密码）
     */
    @GetMapping("/info")
    public Result<User> getUserInfo() {
        // 从 UserContext 获取用户 ID（由 UserInfoInterceptor 从 Gateway 传递的请求头中读取）
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        User user = userService.getById(userId);
        // 安全处理：不返回密码字段
        if (user != null) {
            user.setUserPassword(null);
        }
        return Result.success(user);
    }
}
