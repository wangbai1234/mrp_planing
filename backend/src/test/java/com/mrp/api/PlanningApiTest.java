package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mrp.MrpApiApplication;
import com.mrp.BaseIntegrationTest;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.planning.domain.PlanDetail;
import com.mrp.planning.domain.PlanVersion;
import com.mrp.planning.repository.PlanMapper;
import com.mrp.planning.repository.PlanOverrideMapper;
import com.mrp.planning.service.PlanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class PlanningApiTest extends BaseIntegrationTest {

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

    @Autowired
    private PlanOverrideMapper planOverrideMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin", "password", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

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
    void apiShouldNotCreateUnusedGeneratedPasswordUser() {
        org.junit.jupiter.api.Assertions.assertFalse(applicationContext.containsBean("inMemoryUserDetailsManager"));
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
    void saveOverride_shouldRejectLegacyWrites() throws Exception {
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
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertTrue(planOverrideMapper.selectByVersionId(planId).isEmpty());
    }

    @Test
    void deleteOverride_shouldRejectLegacyWrites() throws Exception {
        Long planId = createTestPlan();
        List<PlanDetail> details = planMapper.selectDetailsByVersionId(planId);
        PlanDetail detail = details.get(0);

        mockMvc.perform(delete("/api/v1/plans/" + planId + "/overrides/" + detail.materialId())
                        .header("Authorization", "Bearer " + token)
                        .param("weekStartDate", detail.weekStartDate().toString()))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertTrue(planOverrideMapper.selectByVersionId(planId).isEmpty());
    }

    @Test
    void publish_shouldRejectLegacyWrites() throws Exception {
        Long planId = createTestPlan();

        mockMvc.perform(post("/api/v1/plans/" + planId + "/publish")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(PlanVersion.STATUS_READY,
                planMapper.selectVersionById(planId).status());
    }

    @Test
    void recalculate_shouldRejectLegacyWrites() throws Exception {
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
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyReads_shouldRespectFactoryScope() throws Exception {
        ForecastVersion forecast = new ForecastVersion(null, 1, "scope-test.xlsx",
                "scope_" + System.nanoTime(), ForecastVersion.STATUS_IMPORTED, 1L, null);
        forecastMapper.insertVersion(forecast);
        Long forecastId = forecastMapper.selectLastInsertVersionId();
        PlanVersion allowed = legacyPlan(forecastId, "爱培科");
        PlanVersion denied = legacyPlan(forecastId, "永惠");
        String username = "legacy_scope_" + System.nanoTime();
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user WHERE username = 'admin'", String.class);
        jdbcTemplate.update("INSERT INTO user(username, password_hash, display_name) VALUES (?, ?, ?)",
                username, hash, "Legacy scope test");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
        try {
            jdbcTemplate.update("INSERT INTO user_role(user_id, role_id) SELECT ?, id FROM role WHERE code = 'READONLY'", userId);
            jdbcTemplate.update("INSERT INTO user_factory_scope(user_id, factory_code) VALUES (?, ?)", userId, "爱培科");
            MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("username", username, "password", BaseIntegrationTest.TEST_ADMIN_PASSWORD))))
                    .andExpect(status().isOk()).andReturn();
            String scopedToken = objectMapper.readTree(login.getResponse().getContentAsString())
                    .path("data").path("token").asText();

            MvcResult list = mockMvc.perform(get("/api/v1/plans")
                            .header("Authorization", "Bearer " + scopedToken))
                    .andExpect(status().isOk()).andReturn();
            JsonNode rows = objectMapper.readTree(list.getResponse().getContentAsString()).path("data");
            org.junit.jupiter.api.Assertions.assertTrue(rows.isArray());
            org.junit.jupiter.api.Assertions.assertTrue(rows.findValuesAsText("id").contains(allowed.id().toString()));
            org.junit.jupiter.api.Assertions.assertFalse(rows.findValuesAsText("id").contains(denied.id().toString()));

            MvcResult current = mockMvc.perform(get("/api/v1/plans/current")
                            .header("Authorization", "Bearer " + scopedToken))
                    .andExpect(status().isOk()).andReturn();
            JsonNode currentRows = objectMapper.readTree(current.getResponse().getContentAsString()).path("data");
            org.junit.jupiter.api.Assertions.assertTrue(currentRows.findValuesAsText("id").contains(allowed.id().toString()));
            org.junit.jupiter.api.Assertions.assertFalse(currentRows.findValuesAsText("id").contains(denied.id().toString()));
            mockMvc.perform(get("/api/v1/plans/" + allowed.id() + "/grid")
                            .header("Authorization", "Bearer " + scopedToken))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/plans/" + denied.id() + "/grid")
                            .header("Authorization", "Bearer " + scopedToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/plans/compare")
                            .header("Authorization", "Bearer " + scopedToken)
                            .param("baseId", allowed.id().toString())
                            .param("currentId", denied.id().toString()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/users/" + userId + "/status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isActive\":false}"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/plans")
                            .header("Authorization", "Bearer " + scopedToken))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbcTemplate.update("DELETE FROM user_factory_scope WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
            jdbcTemplate.update("DELETE FROM plan_version WHERE id IN (?, ?)", allowed.id(), denied.id());
            jdbcTemplate.update("DELETE FROM forecast_version WHERE id = ?", forecastId);
        }
    }

    private PlanVersion legacyPlan(Long forecastId, String factory) {
        PlanVersion version = new PlanVersion(null, 1, factory, forecastId, null, null, null,
                "v1", LocalDate.of(2026, 11, 2), null, null, PlanVersion.STATUS_PUBLISHED,
                false, false, 1L, null, null);
        planMapper.insertVersion(version);
        return version;
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
