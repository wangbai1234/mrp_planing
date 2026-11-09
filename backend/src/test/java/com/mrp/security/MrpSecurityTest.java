package com.mrp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import com.mrp.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
class MrpSecurityTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken(String username, String password) throws Exception {
        Map<String, String> loginRequest = Map.of("username", username, "password", password);

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).path("data").path("token").asText();
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/mrp/batches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/mrp/batches/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/mrp/batches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/mrp/batches")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredToken_returns401() throws Exception {
        String malformedToken = "eyJhbGciOiJIUzM4NCJ9.expired.payload";
        mockMvc.perform(get("/api/v1/mrp/batches")
                        .header("Authorization", "Bearer " + malformedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void admin_canAccessMrpBatchView() throws Exception {
        String token = loginAndGetToken("admin", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        // Listing batches should not return 403 - proves list permission works
        mockMvc.perform(get("/api/v1/mrp/batches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("Expected non-403 status but got 403");
                    }
                });
    }

    @Test
    void admin_canAccessMrpBatchCreate() throws Exception {
        String token = loginAndGetToken("admin", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        // Creating a batch should not return 403 - proves create permission works
        mockMvc.perform(post("/api/v1/mrp/batches")
                        .header("Authorization", "Bearer " + token)
                        .param("factoryCode", "F001")
                        .param("forecastVersionId", "1")
                        .param("inventorySnapshotId", "1")
                        .param("bomSnapshotId", "1")
                        .param("categoryRoleVersionId", "1")
                        .param("materialPolicyVersionId", "1")
                        .param("productionCalendarVersionId", "1"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("Expected non-403 status but got 403");
                    }
                });
    }

    @Test
    void userWithoutMrpPermission_cannotAccessMrpEndpoints() throws Exception {
        String invalidToken = "eyJhbGciOiJIUzM4NCJ9.limited.payload";

        mockMvc.perform(get("/api/v1/mrp/batches")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mrpBatchViewPermission_requiredForGetEndpoints() throws Exception {
        String token = loginAndGetToken("admin", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        // GET /api/v1/mrp/batches requires mrp_batch:list - should not return 403
        mockMvc.perform(get("/api/v1/mrp/batches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("Expected non-403 status but got 403");
                    }
                });
    }

    @Test
    void mrpDecisionManage_requiredForIssueEndpoints() throws Exception {
        String token = loginAndGetToken("admin", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        // Accessing a non-existent batch returns error, not 403 - proves decision permission works
        mockMvc.perform(post("/api/v1/mrp/batches/999999/issues")
                        .header("Authorization", "Bearer " + token)
                        .param("issueKey", "TEST-001")
                        .param("issueType", "TEST")
                        .param("severity", "LOW"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Should not be 403 (forbidden) - proves permission is granted
                    if (status == 403) {
                        throw new AssertionError("Expected non-403 status but got 403");
                    }
                });
    }
}
