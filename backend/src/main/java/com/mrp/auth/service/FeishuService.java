package com.mrp.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class FeishuService {

    private static final Logger log = LoggerFactory.getLogger(FeishuService.class);

    private final String appId;
    private final String appSecret;
    private final String redirectUri;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FeishuService(
            @Value("${feishu.app-id}") String appId,
            @Value("${feishu.app-secret}") String appSecret,
            @Value("${feishu.redirect-uri}") String redirectUri,
            @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ObjectMapper objectMapper) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectUri = redirectUri;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * 生成随机state参数，用于防止CSRF攻击
     */
    public String generateState() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 构造飞书授权链接
     */
    public String getAuthorizeUrl(String state) {
        return UriComponentsBuilder
                .fromUriString("https://accounts.feishu.cn/open-apis/authen/v1/authorize")
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .queryParam("scope", "contact:user.email:readonly contact:user.base:readonly")
                .build()
                .toUriString();
    }

    /**
     * 通过授权码换取user_access_token
     */
    public String exchangeCodeForToken(String code) {
        String url = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "grant_type", "authorization_code",
                "client_id", appId,
                "client_secret", appSecret,
                "code", code,
                "redirect_uri", redirectUri
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            if (jsonNode.has("access_token")) {
                return jsonNode.get("access_token").asText();
            }

            throw new RuntimeException("获取飞书访问令牌失败");
        } catch (Exception e) {
            log.warn("Feishu token exchange failed");
            throw new RuntimeException("获取飞书访问令牌失败");
        }
    }

    /**
     * 获取飞书用户信息
     */
    public FeishuUserInfo getUserInfo(String accessToken) {
        String url = "https://open.feishu.cn/open-apis/authen/v1/user_info";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            if (jsonNode.has("data")) {
                JsonNode data = jsonNode.get("data");
                return new FeishuUserInfo(
                        data.has("user_id") ? data.get("user_id").asText() : null,
                        data.has("open_id") ? data.get("open_id").asText() : null,
                        data.has("name") ? data.get("name").asText() : null,
                        data.has("email") ? data.get("email").asText() : null,
                        data.has("avatar") && data.get("avatar").has("avatar_72")
                                ? data.get("avatar").get("avatar_72").asText() : null
                );
            }

            throw new RuntimeException("获取飞书用户信息失败");
        } catch (Exception e) {
            log.warn("Feishu user info request failed");
            throw new RuntimeException("获取飞书用户信息失败");
        }
    }

    /**
     * 查找或创建系统用户
     * 1. 通过飞书user_id查找现有绑定
     * 2. 通过邮箱查找现有用户并绑定
     * 3. 创建新用户（使用邮箱作为用户名）
     */
    public Map<String, Object> findOrCreateUser(FeishuUserInfo feishuUser) {
        // 验证邮箱必须存在
        if (feishuUser.email() == null || feishuUser.email().isEmpty()) {
            throw new RuntimeException("飞书账号未绑定邮箱，请先在飞书中绑定邮箱后重试");
        }

        // 1. 通过飞书user_id查找现有绑定
        var existingByFeishuId = jdbcTemplate.queryForList(
                "SELECT id, username, display_name, is_active, need_change_password FROM user WHERE feishu_user_id = ?",
                feishuUser.userId());

        if (!existingByFeishuId.isEmpty()) {
            var user = existingByFeishuId.get(0);
            Boolean isActive = (Boolean) user.get("is_active");
            if (isActive == null || !isActive) {
                throw new RuntimeException("账号已被禁用");
            }
            // 确保用户名是邮箱格式
            Long userId = ((Number) user.get("id")).longValue();
            jdbcTemplate.update("UPDATE user SET username = ?, email = ? WHERE id = ?", 
                    feishuUser.email(), feishuUser.email(), userId);
            user.put("username", feishuUser.email());
            return buildLoginResult(user);
        }

        // 2. 通过邮箱查找现有用户并绑定
        var existingByEmail = jdbcTemplate.queryForList(
                "SELECT id, username, display_name, is_active, need_change_password FROM user WHERE email = ?",
                feishuUser.email());

        if (!existingByEmail.isEmpty()) {
            var user = existingByEmail.get(0);
            if (!Boolean.TRUE.equals(user.get("is_active"))) {
                throw new RuntimeException("账号已被禁用");
            }
            Long userId = ((Number) user.get("id")).longValue();

            // 绑定飞书账号，确保用户名是邮箱
            jdbcTemplate.update(
                    "UPDATE user SET feishu_user_id = ?, feishu_open_id = ?, avatar_url = ?, auth_source = 'FEISHU', username = ? WHERE id = ?",
                    feishuUser.userId(), feishuUser.openId(), feishuUser.avatarUrl(), feishuUser.email(), userId);

            user.put("username", feishuUser.email());
            log.info("Bound Feishu account to existing user: {}", userId);
            return buildLoginResult(user);
        }

        // 3. 飞书新用户不设置本地登录密码，首次登录后要求设置密码。
        String username = feishuUser.email(); // 使用邮箱作为用户名
        String displayName = feishuUser.name() != null ? feishuUser.name() : username;
        String email = feishuUser.email();

        jdbcTemplate.update(
                "INSERT INTO user (username, display_name, email, password_hash, feishu_user_id, feishu_open_id, avatar_url, auth_source, is_active, need_change_password) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'FEISHU', 1, 1)",
                username, displayName, email, null, feishuUser.userId(), feishuUser.openId(), feishuUser.avatarUrl());

        Long newUserId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 分配NEW_USER角色（无权限，需要管理员分配）
        jdbcTemplate.update(
                "INSERT INTO user_role (user_id, role_id) SELECT ?, id FROM role WHERE code = 'NEW_USER'",
                newUserId);

        log.info("Created new Feishu user: {} (ID: {}), need change password", username, newUserId);

        Map<String, Object> newUser = new java.util.HashMap<>();
        newUser.put("id", newUserId);
        newUser.put("username", username);
        newUser.put("display_name", displayName);
        newUser.put("need_change_password", true);
        return buildLoginResult(newUser);
    }

    /**
     * 构建登录结果（JWT token + 用户信息）
     */
    private Map<String, Object> buildLoginResult(Map<String, Object> user) {
        Long userId = ((Number) user.get("id")).longValue();
        String username = (String) user.get("username");
        String displayName = user.get("display_name") != null ? (String) user.get("display_name") : username;
        Boolean needChangePassword = user.containsKey("need_change_password") 
                ? (Boolean) user.get("need_change_password") 
                : false;

        // 更新最后登录时间
        jdbcTemplate.update("UPDATE user SET last_login_at = NOW() WHERE id = ?", userId);

        // 获取权限
        var permissions = jdbcTemplate.queryForList(
                "SELECT p.code FROM permission p " +
                        "INNER JOIN role_permission rp ON p.id = rp.permission_id " +
                        "INNER JOIN user_role ur ON rp.role_id = ur.role_id " +
                        "WHERE ur.user_id = ?",
                String.class, userId);

        // 获取角色
        var roles = jdbcTemplate.queryForList(
                "SELECT r.code FROM role r INNER JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                String.class, userId);

        // 生成JWT
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user WHERE id = ?", String.class, userId);
        String token = jwtService.generateToken(userId, username, passwordHash);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("userId", userId);
        result.put("username", username);
        result.put("displayName", displayName);
        result.put("roles", roles);
        result.put("permissions", permissions);
        result.put("needChangePassword", needChangePassword);
        return result;
    }

    /**
     * 飞书用户信息记录
     */
    public record FeishuUserInfo(
            String userId,
            String openId,
            String name,
            String email,
            String avatarUrl
    ) {}
}
