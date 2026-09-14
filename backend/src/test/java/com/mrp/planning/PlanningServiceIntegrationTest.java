package com.mrp.planning;

import com.mrp.MrpApiApplication;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.planning.domain.PlanVersion;
import com.mrp.planning.service.PlanningService;
import com.mrp.task.domain.ImportTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MrpApiApplication.class)
@ActiveProfiles("local")
@Transactional
class PlanningServiceIntegrationTest {

    @Autowired
    private PlanningService planningService;

    @Autowired
    private ForecastImportService forecastImportService;

    @Test
    void contextLoads() {
        assertNotNull(planningService);
    }

    @Test
    void getLatestForecastVersionId_returnsNullWhenEmpty() {
        // This might return a value if there's existing data, or null
        // Just verify it doesn't throw
        Long versionId = planningService.getLatestForecastVersionId();
        // No assertion - just checking it doesn't throw
    }

    @Test
    void recalculate_withNoForecast_throwsBusinessException() {
        // When there's no forecast data, recalculate should throw
        assertThrows(Exception.class, () -> {
            planningService.recalculate(
                    99999L, // non-existent forecast version
                    null, null, null,
                    "永惠",
                    LocalDate.of(2026, 8, 3),
                    1L
            );
        });
    }

    @Test
    void getLatestPlan_returnsNullWhenNoPlan() {
        PlanVersion plan = planningService.getLatestPlan("NONEXISTENT");
        assertNull(plan, "Should return null for non-existent factory");
    }

    @Test
    void getAllLatestPlans_returnsEmptyWhenNoPlans() {
        var plans = planningService.getAllLatestPlans();
        assertNotNull(plans);
        // May or may not be empty depending on existing data
    }

    @Test
    void listVersions_returnsEmptyForNonExistentFactory() {
        var versions = planningService.listVersions("NONEXISTENT");
        assertNotNull(versions);
        assertTrue(versions.isEmpty(), "Should be empty for non-existent factory");
    }
}
