package com.mrp.auth.controller;

import com.mrp.auth.service.FeishuService;
import com.mrp.auth.service.JwtService;
import com.mrp.auth.service.LegacyAdminBootstrap;
import com.mrp.auth.service.UserService;
import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;
    private final FeishuService feishuService;

    public AuthController(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
                         PasswordEncoder passwordEncoder,
                         JwtService jwtService,
                         UserService userService,
                         FeishuService feishuService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userService = userService;
        this.feishuService = feishuService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return ApiResponse.error("MRP_AUTH_ERROR", "邮箱和密码不能为空");
        }

        // 支持邮箱或用户名登录
        var users = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.password_hash, u.display_name, u.is_active, u.need_change_password " +
                "FROM user u WHERE u.username = ? OR u.email = ?",
                username, username);

        if (users.isEmpty()) {
            return ApiResponse.error("MRP_AUTH_ERROR", "邮箱或密码错误");
        }

        var user = users.get(0);
        Boolean isActive = (Boolean) user.get("is_active");
        if (isActive == null || !isActive) {
            return ApiResponse.error("MRP_AUTH_ERROR", "账号已被禁用");
        }

        String hash = (String) user.get("password_hash");
        if (hash == null || LegacyAdminBootstrap.isSeededAdmin((String) user.get("username"), hash)
                || !passwordEncoder.matches(password, hash)) {
            return ApiResponse.error("MRP_AUTH_ERROR", "邮箱或密码错误");
        }

        Long userId = ((Number) user.get("id")).longValue();
        String actualUsername = (String) user.get("username");

        // Update last login time
        jdbcTemplate.update("UPDATE user SET last_login_at = NOW() WHERE id = ?", userId);

        // Get user permissions
        Set<String> permissions = userService.getEffectivePermissions(userId);

        // Generate token (only contains userId)
        String token = jwtService.generateToken(userId, actualUsername, hash);

        // Get roles
        var roles = jdbcTemplate.queryForList(
                "SELECT r.code FROM role r INNER JOIN user_role ur ON r.id = ur.role_id " +
                "WHERE ur.user_id = ? AND r.status = 'ACTIVE'",
                userId);
        var roleCodes = roles.stream().map(r -> (String) r.get("code")).toList();

        return ApiResponse.ok(Map.of(
                "token", token,
                "userId", userId,
                "username", actualUsername,
                "displayName", user.get("display_name") != null ? user.get("display_name") : actualUsername,
                "roles", roleCodes,
                "permissions", permissions,
                "needChangePassword", Boolean.TRUE.equals(user.get("need_change_password"))
        ));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser() {
        Long userId = CurrentUser.getUserId();
        if (userId == null) return ApiResponse.error("MRP_AUTH_ERROR", "未登录");
        var roles = userService.getUserRoles(userId).stream()
                .filter(role -> "ACTIVE".equals(role.getStatus()))
                .map(com.mrp.auth.domain.Role::getCode).toList();
        return ApiResponse.ok(Map.of(
                "roles", roles,
                "permissions", userService.getEffectivePermissions(userId)
        ));
    }

    @GetMapping("/feishu/authorize")
    public ApiResponse<Map<String, String>> getFeishuAuthorizeUrl() {
        try {
            String state = feishuService.generateState();
            String authorizeUrl = feishuService.getAuthorizeUrl(state);
            return ApiResponse.ok(Map.of(
                    "authorizeUrl", authorizeUrl,
                    "state", state
            ));
        } catch (Exception e) {
            return ApiResponse.error("FEISHU_AUTH_ERROR", "获取飞书授权链接失败");
        }
    }

    @PostMapping("/feishu/callback")
    public ApiResponse<Map<String, Object>> handleFeishuCallback(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isEmpty()) {
            return ApiResponse.error("FEISHU_AUTH_ERROR", "授权码不能为空");
        }

        try {
            // 用授权码换取token
            String accessToken = feishuService.exchangeCodeForToken(code);

            // 获取飞书用户信息
            FeishuService.FeishuUserInfo feishuUserInfo = feishuService.getUserInfo(accessToken);

            // 查找或创建用户
            Map<String, Object> loginResult = feishuService.findOrCreateUser(feishuUserInfo);

            return ApiResponse.ok(loginResult);
        } catch (Exception e) {
            return ApiResponse.error("FEISHU_AUTH_ERROR", "飞书登录失败");
        }
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, String>> changePassword(@RequestBody Map<String, String> request) {
        Long userId = CurrentUser.getUserId();
        if (userId == null) {
            return ApiResponse.error("MRP_AUTH_ERROR", "未登录");
        }

        String newPassword = request.get("password");
        if (newPassword == null || newPassword.length() < 8) {
            return ApiResponse.error("MRP_VALIDATION_ERROR", "密码长度至少8位");
        }

        // 检查密码强度：至少包含数字和字母
        boolean hasDigit = newPassword.matches(".*\\d.*");
        boolean hasLetter = newPassword.matches(".*[a-zA-Z].*");
        if (!hasDigit || !hasLetter) {
            return ApiResponse.error("MRP_VALIDATION_ERROR", "密码必须包含数字和字母");
        }

        try {
            var users = jdbcTemplate.queryForList(
                    "SELECT username, password_hash, need_change_password FROM user WHERE id = ?", userId);
            if (users.isEmpty()) {
                return ApiResponse.error("MRP_AUTH_ERROR", "账号不存在");
            }
            var user = users.get(0);
            if (!Boolean.TRUE.equals(user.get("need_change_password"))) {
                String oldPassword = request.get("oldPassword");
                String oldHash = (String) user.get("password_hash");
                if (oldPassword == null || oldHash == null || !passwordEncoder.matches(oldPassword, oldHash)) {
                    return ApiResponse.error("MRP_AUTH_ERROR", "原密码错误");
                }
            }
            String newPasswordHash = passwordEncoder.encode(newPassword);
            jdbcTemplate.update(
                    "UPDATE user SET password_hash = ?, need_change_password = 0 WHERE id = ?",
                    newPasswordHash, userId);

            log.info("User {} changed password successfully", userId);
            return ApiResponse.ok(Map.of("token", jwtService.generateToken(
                    userId, (String) user.get("username"), newPasswordHash)));
        } catch (Exception e) {
            log.error("Failed to change password for user {}", userId);
            return ApiResponse.error("MRP_ERROR", "修改密码失败");
        }
    }
}
