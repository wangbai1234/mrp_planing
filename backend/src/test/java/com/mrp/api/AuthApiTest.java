package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        Map<String, String> loginRequest = Map.of("username", "admin", "password", "admin123");

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
    void accessProtectedEndpoint_withoutToken_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessProtectedEndpoint_withValidToken_shouldSucceed() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
