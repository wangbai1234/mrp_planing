package com.mrp.auth.controller;

import com.mrp.auth.service.JwtService;
import com.mrp.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(@Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate, 
                         PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return ApiResponse.error("MRP_AUTH_ERROR", "用户名和密码不能为空");
        }

        var users = jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.password_hash, u.display_name, " +
                "(SELECT GROUP_CONCAT(r.code) FROM user_role ur JOIN role r ON ur.role_id = r.id WHERE ur.user_id = u.id) as roles " +
                "FROM user u WHERE u.username = ? AND u.is_active = 1",
                username);

        if (users.isEmpty()) {
            return ApiResponse.error("MRP_AUTH_ERROR", "用户名或密码错误");
        }

        var user = users.get(0);
        String hash = (String) user.get("password_hash");
        if (!passwordEncoder.matches(password, hash)) {
            return ApiResponse.error("MRP_AUTH_ERROR", "用户名或密码错误");
        }

        Long userId = ((Number) user.get("id")).longValue();
        String roles = (String) user.get("roles");
        String primaryRole = roles != null ? roles.split(",")[0] : "READONLY";

        // Get factory scope
        var scopes = jdbcTemplate.queryForList(
                "SELECT factory_code FROM user_factory_scope WHERE user_id = ?", userId);
        String factoryScope = scopes.stream()
                .map(s -> (String) s.get("factory_code"))
                .reduce((a, b) -> a + "," + b).orElse("");

        String token = jwtService.generateToken(userId, username, primaryRole, factoryScope);

        return ApiResponse.ok(Map.of(
                "token", token,
                "userId", userId,
                "username", username,
                "role", primaryRole,
                "factoryScope", factoryScope
        ));
    }
}
