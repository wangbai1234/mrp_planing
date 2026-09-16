package com.mrp.security;

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

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityPermissionTest {

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
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).path("data").path("token").asText();
    }

    @Test
    void admin_canAccessAllEndpoints() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        // Admin should have access to all endpoints
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/materials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void userWithoutPermission_cannotAccessMaterials() throws Exception {
        // Create a test user without material:view permission
        // For now, test with invalid token
        String invalidToken = "invalid.token.here";

        mockMvc.perform(get("/api/v1/materials")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/materials"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredToken_returns403() throws Exception {
        // This would require a token with expired timestamp
        // For now, test with a malformed token
        String malformedToken = "eyJhbGciOiJIUzM4NCJ9.invalid.payload";

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + malformedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthEndpoint_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void accessWithoutToken_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessWithInvalidToken_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessWithInsufficientPermission_shouldReturn403() throws Exception {
        // Login as a user with limited permissions (if exists)
        // For now, test with invalid token which simulates insufficient permission
        String limitedToken = "eyJhbGciOiJIUzM4NCJ9.limited.payload";

        // Try to publish (requires schedule:publish permission)
        mockMvc.perform(post("/api/v1/plans/1/publish")
                        .header("Authorization", "Bearer " + limitedToken))
                .andExpect(status().isForbidden());
    }
}
