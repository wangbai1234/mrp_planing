package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import org.junit.jupiter.api.BeforeEach;
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
class PlanningApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin", "password", "admin123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        token = objectMapper.readTree(responseBody).path("data").path("token").asText();
    }

    @Test
    void listPlans_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getCurrentPlan_withNoPlan_shouldReturnEmpty() throws Exception {
        mockMvc.perform(get("/api/v1/plans/current")
                        .header("Authorization", "Bearer " + token)
                        .param("factoryCode", "NONEXISTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void compareVersions_withNoVersions_shouldReturnError() throws Exception {
        mockMvc.perform(get("/api/v1/plans/compare")
                        .header("Authorization", "Bearer " + token)
                        .param("v1", "999")
                        .param("v2", "998"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getExportTask_withNonExistentId_shouldReturnSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/export-tasks/999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
