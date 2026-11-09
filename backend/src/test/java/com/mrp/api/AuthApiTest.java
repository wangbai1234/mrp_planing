package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import com.mrp.BaseIntegrationTest;
import com.mrp.auth.service.FeishuService;
import com.mrp.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
class AuthApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeishuService feishuService;

    @Autowired
    private UserService userService;

    private String loginAndGetToken(String username, String password) throws Exception {
        Map<String, String> loginRequest = Map.of("username", username, "password", password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).path("data").path("token").asText();
    }

    @Test
    void login_withValidCredentials_shouldReturnToken() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin", "password", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void login_withInvalidPassword_shouldReturnError() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin", "password", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MRP_AUTH_ERROR"));
    }

    @Test
    void login_withNonExistentUser_shouldReturnError() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "nonexistent", "password", "password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MRP_AUTH_ERROR"));
    }

    @Test
    void login_withMissingFields_shouldReturnError() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MRP_AUTH_ERROR"));
    }

    @Test
    void accessProtectedEndpoint_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpoint_withValidToken_shouldSucceed() throws Exception {
        String token = loginAndGetToken("admin", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void temporaryPassword_requiresChangeAndResetInvalidatesPreviousPassword() throws Exception {
        String adminToken = loginAndGetToken("admin", BaseIntegrationTest.TEST_ADMIN_PASSWORD);
        String username = "auth_temp_" + System.nanoTime();
        MvcResult created = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "displayName", "Temporary User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        var createData = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
        long userId = createData.path("user").path("id").asLong();
        String firstPassword = createData.path("temporaryPassword").asText();
        org.junit.jupiter.api.Assertions.assertTrue(firstPassword.length() >= 24);

        try {
            String firstToken = loginAndGetToken(username, firstPassword);
            mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + firstToken))
                    .andExpect(status().isUnauthorized());
            MvcResult changed = mockMvc.perform(post("/api/v1/auth/change-password")
                            .header("Authorization", "Bearer " + firstToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"ChangedPassword123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andReturn();
            String changedToken = objectMapper.readTree(changed.getResponse().getContentAsString())
                    .path("data").path("token").asText();
            mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + firstToken))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + changedToken))
                    .andExpect(status().isForbidden());

            MvcResult reset = mockMvc.perform(put("/api/v1/users/{id}/reset-password", userId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andReturn();
            String nextPassword = objectMapper.readTree(reset.getResponse().getContentAsString())
                    .path("data").path("temporaryPassword").asText();
            org.junit.jupiter.api.Assertions.assertNotEquals(firstPassword, nextPassword);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "username", username, "password", "ChangedPassword123!"))))
                    .andExpect(jsonPath("$.success").value(false));
            String resetToken = loginAndGetToken(username, nextPassword);
            mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + resetToken))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header("Authorization", "Bearer " + resetToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"AfterResetPassword123!\"}"))
                    .andExpect(jsonPath("$.success").value(true));
            mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + changedToken))
                    .andExpect(status().isUnauthorized());
        } finally {
            mockMvc.perform(delete("/api/v1/users/{id}", userId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void feishuBinding_rejectsDisabledAccountAndNewAccountHasNoLocalDefaultPassword() throws Exception {
        String username = "feishu_test_" + System.nanoTime() + "@example.invalid";
        com.mrp.auth.domain.User user = new com.mrp.auth.domain.User();
        user.setUsername(username);
        user.setEmail(username);
        long userId = userService.create(user, java.util.List.of(), 1L).user().getId();
        try {
            userService.updateStatus(userId, false, 1L);
            var info = new FeishuService.FeishuUserInfo("disabled-" + userId, "open-" + userId,
                    "Disabled User", username, null);
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> feishuService.findOrCreateUser(info));
        } finally {
            userService.delete(userId, 1L);
        }

        String feishuEmail = "feishu_new_" + System.nanoTime() + "@example.invalid";
        var newInfo = new FeishuService.FeishuUserInfo("new-" + System.nanoTime(),
                "open-" + System.nanoTime(), "Feishu New", feishuEmail, null);
        var login = feishuService.findOrCreateUser(newInfo);
        long newId = ((Number) login.get("userId")).longValue();
        try {
            org.junit.jupiter.api.Assertions.assertEquals(true, login.get("needChangePassword"));
            org.junit.jupiter.api.Assertions.assertNull(userService.getById(newId).getPasswordHash());
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "username", feishuEmail, "password", "123456"))))
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            userService.delete(newId, 1L);
        }
    }
}
