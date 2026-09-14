package com.mrp.auth.controller;

import com.mrp.auth.service.JwtService;
import com.mrp.auth.service.UserService;
import com.mrp.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthController(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
                         PasswordEncoder passwordEncoder,
                         JwtService jwtService,
                         UserService userService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return ApiResponse.error("MRP_AUTH_ERROR", "用户名和密码不能为空");
        }

        var users = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.password_hash, u.display_name, u.is_active " +
                "FROM user u WHERE u.username = ?",
                username);

        if (users.isEmpty()) {
            return ApiResponse.error("MRP_AUTH_ERROR", "用户名或密码错误");
        }

        var user = users.get(0);
        Boolean isActive = (Boolean) user.get("is_active");
        if (isActive == null || !isActive) {
            return ApiResponse.error("MRP_AUTH_ERROR", "账号已被禁用");
        }

        String hash = (String) user.get("password_hash");
        if (!passwordEncoder.matches(password, hash)) {
            return ApiResponse.error("MRP_AUTH_ERROR", "用户名或密码错误");
        }

        Long userId = ((Number) user.get("id")).longValue();

        // Update last login time
        jdbcTemplate.update("UPDATE user SET last_login_at = NOW() WHERE id = ?", userId);

        // Get user permissions
        Set<String> permissions = userService.getEffectivePermissions(userId);

        // Generate token (only contains userId)
        String token = jwtService.generateToken(userId, username);

        // Get roles
        var roles = jdbcTemplate.queryForList(
                "SELECT r.code FROM role r INNER JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                userId);
        var roleCodes = roles.stream().map(r -> (String) r.get("code")).toList();

        return ApiResponse.ok(Map.of(
                "token", token,
                "userId", userId,
                "username", username,
                "displayName", user.get("display_name") != null ? user.get("display_name") : username,
                "roles", roleCodes,
                "permissions", permissions
        ));
    }
}
