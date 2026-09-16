package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.planning.domain.PlanDetail;
import com.mrp.planning.domain.PlanVersion;
import com.mrp.planning.repository.PlanMapper;
import com.mrp.planning.service.PlanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    @Autowired
    private ForecastMapper forecastMapper;

    @Autowired
    private PlanningService planningService;

    @Autowired
    private PlanMapper planMapper;

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
    void compareVersions_withNoVersions_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/plans/compare")
                        .header("Authorization", "Bearer " + token)
                        .param("baseId", "999")
                        .param("currentId", "998"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getExportTask_withNonExistentId_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/export-tasks/999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void saveOverride_shouldReturn200() throws Exception {
        Long planId = createTestPlan();
        List<PlanDetail> details = planMapper.selectDetailsByVersionId(planId);
        PlanDetail detail = details.get(0);

        Map<String, Object> override = Map.of(
                "materialId", detail.materialId(),
                "weekStartDate", detail.weekStartDate().toString(),
                "manualQuantity", detail.systemQuantity() + 100,
                "reason", "API test override"
        );

        mockMvc.perform(post("/api/v1/plans/" + planId + "/overrides")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void deleteOverride_shouldReturn200() throws Exception {
        Long planId = createTestPlan();
        List<PlanDetail> details = planMapper.selectDetailsByVersionId(planId);
        PlanDetail detail = details.get(0);

        Map<String, Object> override = Map.of(
                "materialId", detail.materialId(),
                "weekStartDate", detail.weekStartDate().toString(),
                "manualQuantity", detail.systemQuantity() + 200,
                "reason", "to be deleted"
        );
        mockMvc.perform(post("/api/v1/plans/" + planId + "/overrides")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(override)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/plans/" + planId + "/overrides/" + detail.materialId())
                        .header("Authorization", "Bearer " + token)
                        .param("weekStartDate", detail.weekStartDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void publish_shouldReturn200() throws Exception {
        Long planId = createTestPlan();

        mockMvc.perform(post("/api/v1/plans/" + planId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void recalculate_shouldReturn202() throws Exception {
        String factoryCode = "API_TEST_" + System.nanoTime();

        ForecastVersion version = new ForecastVersion(
                null, 1, "api-test.xlsx", "checksum_" + System.nanoTime(),
                ForecastVersion.STATUS_IMPORTED, 1L, null
        );
        forecastMapper.insertVersion(version);
        Long forecastVersionId = forecastMapper.selectLastInsertVersionId();

        LocalDate planMonth = LocalDate.of(2026, 10, 1);
        ForecastDetail detail = new ForecastDetail(
                null, forecastVersionId, factoryCode,
                "API_MAT_" + System.nanoTime(), "API Test Material",
                null, null, null, null, null, null,
                planMonth, new BigDecimal("500")
        );
        forecastMapper.insertDetails(List.of(detail));

        Map<String, Object> request = Map.of(
                "factoryCode", factoryCode,
                "currentWeekStart", "2026-09-15",
                "forecastVersionId", forecastVersionId
        );

        mockMvc.perform(post("/api/v1/recalculations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").exists());
    }

    @Test
    void listPlans_withPagination_shouldReturnPagedResult() throws Exception {
        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(lessThanOrEqualTo(5))));
    }

    private Long createTestPlan() {
        String factoryCode = "TEST_FACTORY_" + System.nanoTime();

        ForecastVersion version = new ForecastVersion(
                null, 1, "test.xlsx", "checksum_" + System.nanoTime(),
                ForecastVersion.STATUS_IMPORTED, 1L, null
        );
        forecastMapper.insertVersion(version);
        Long forecastVersionId = forecastMapper.selectLastInsertVersionId();

        LocalDate planMonth = LocalDate.of(2026, 10, 1);
        ForecastDetail detail = new ForecastDetail(
                null, forecastVersionId, factoryCode,
                "TEST_MAT_" + System.nanoTime(), "Test Material",
                null, null, null, null, null, null,
                planMonth, new BigDecimal("1000")
        );
        forecastMapper.insertDetails(List.of(detail));

        LocalDate currentWeekStart = LocalDate.of(2026, 9, 15);
        PlanVersion plan = planningService.recalculate(
                forecastVersionId, null, null, null,
                factoryCode, currentWeekStart, 1L
        );

        return plan.id();
    }
}
