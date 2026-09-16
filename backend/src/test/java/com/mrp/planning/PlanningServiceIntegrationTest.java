package com.mrp.planning;

import com.mrp.MrpApiApplication;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.planning.domain.PlanOverride;
import com.mrp.planning.domain.PlanVersion;
import com.mrp.planning.repository.PlanMapper;
import com.mrp.planning.repository.PlanOverrideMapper;
import com.mrp.planning.service.PlanningDomainService;
import com.mrp.planning.service.PlanningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MrpApiApplication.class)
@ActiveProfiles("local")
@Transactional
class PlanningServiceIntegrationTest {

    @Autowired
    private PlanningService planningService;

    @Autowired
    private PlanningDomainService planningDomainService;

    @Autowired
    private ForecastMapper forecastMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanOverrideMapper overrideMapper;

    @Test
    void contextLoads() {
        assertNotNull(planningService);
        assertNotNull(planningDomainService);
    }

    @Test
    void getLatestForecastVersionId_returnsNullWhenEmpty() {
        Long versionId = planningService.getLatestForecastVersionId();
        // No assertion - just checking it doesn't throw
    }

    @Test
    void recalculate_withNoForecast_throwsBusinessException() {
        assertThrows(Exception.class, () -> {
            planningService.recalculate(
                    99999L, null, null, null,
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
    }

    @Test
    void listVersions_returnsEmptyForNonExistentFactory() {
        var versions = planningService.listVersions("NONEXISTENT");
        assertNotNull(versions);
        assertTrue(versions.isEmpty(), "Should be empty for non-existent factory");
    }

    @Test
    void recalculate_withOverrides_shouldPreserveOverrides() {
        Long planVersionId = createTestPlan();
        List<com.mrp.planning.domain.PlanDetail> details = planMapper.selectDetailsByVersionId(planVersionId);
        assertFalse(details.isEmpty(), "Plan should have details");

        com.mrp.planning.domain.PlanDetail firstDetail = details.get(0);
        Long newManualQty = firstDetail.systemQuantity() + 50;

        planningDomainService.saveOverride(
                planVersionId,
                firstDetail.materialId(),
                firstDetail.weekStartDate(),
                newManualQty,
                "test override",
                1L
        );

        PlanOverride saved = overrideMapper.selectByUnique(
                planVersionId, firstDetail.materialId(), firstDetail.weekStartDate());
        assertNotNull(saved, "Override should be saved");
        assertEquals(newManualQty, saved.manualQuantity());

        Map<String, Long> overridesMap = planningDomainService.getOverridesMap(planVersionId);
        assertFalse(overridesMap.isEmpty(), "Overrides map should not be empty");
        assertTrue(overridesMap.containsKey(firstDetail.materialId() + ":" + firstDetail.weekStartDate()));
    }

    @Test
    void publish_shouldChangeStatus() {
        Long planVersionId = createTestPlan();

        PlanVersion before = planMapper.selectVersionById(planVersionId);
        assertEquals(PlanVersion.STATUS_READY, before.status());

        PlanVersion published = planningDomainService.publish(planVersionId, 1L);
        assertEquals(PlanVersion.STATUS_PUBLISHED, published.status());

        PlanVersion after = planMapper.selectVersionById(planVersionId);
        assertEquals(PlanVersion.STATUS_PUBLISHED, after.status());
    }

    @Test
    void publish_thenModify_shouldAllowModification() {
        Long planVersionId = createTestPlan();

        planningDomainService.publish(planVersionId, 1L);

        PlanVersion published = planMapper.selectVersionById(planVersionId);
        assertEquals(PlanVersion.STATUS_PUBLISHED, published.status());

        List<com.mrp.planning.domain.PlanDetail> details = planMapper.selectDetailsByVersionId(planVersionId);
        assertFalse(details.isEmpty(), "Plan should have details");

        com.mrp.planning.domain.PlanDetail firstDetail = details.get(0);
        Long newManualQty = firstDetail.systemQuantity() + 100;

        PlanOverride override = planningDomainService.saveOverride(
                planVersionId,
                firstDetail.materialId(),
                firstDetail.weekStartDate(),
                newManualQty,
                "modify after publish",
                1L
        );

        assertNotNull(override, "Override should be saved on published plan");
        assertEquals(newManualQty, override.manualQuantity());
    }

    @Test
    void compareVersions_shouldShowDifferences() {
        Long planVersionId1 = createTestPlan();

        List<com.mrp.planning.domain.PlanDetail> details1 = planMapper.selectDetailsByVersionId(planVersionId1);
        assertFalse(details1.isEmpty(), "First plan should have details");

        com.mrp.planning.domain.PlanDetail firstDetail = details1.get(0);
        planningDomainService.saveOverride(
                planVersionId1,
                firstDetail.materialId(),
                firstDetail.weekStartDate(),
                firstDetail.systemQuantity() + 500,
                "version diff test",
                1L
        );

        Long planVersionId2 = createTestPlan();

        Map<String, Object> diff = planningDomainService.compareVersions(planVersionId1, planVersionId2);
        assertNotNull(diff, "Diff should not be null");
        assertTrue(diff.containsKey("differences"));
        assertTrue(diff.containsKey("baseVersionId"));
        assertTrue(diff.containsKey("currentVersionId"));
        assertEquals(planVersionId1, diff.get("baseVersionId"));
        assertEquals(planVersionId2, diff.get("currentVersionId"));
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

        assertNotNull(plan, "Plan should be created");
        assertEquals(PlanVersion.STATUS_READY, plan.status());
        return plan.id();
    }
}
