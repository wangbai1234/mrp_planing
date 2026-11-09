package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.DailyDistributionEngine;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyPlanningServiceTest {

    @Mock
    private MrpDailyPlanDetailMapper dailyPlanMapper;
    @Mock
    private MrpDailyPlanOverrideMapper overrideMapper;
    @Mock
    private MrpUnallocatedQuantityMapper unallocatedMapper;
    @Mock
    private MrpTerminalDemandMapper terminalDemandMapper;
    @Mock
    private ProductionCalendarDayMapper calendarDayMapper;
    @Mock
    private MaterialPolicyVersionService materialPolicyService;
    @Mock
    private MrpExecutionService executionService;
    @Mock
    private MrpDailyExecutionMapper executionMapper;
    @Mock
    private MrpCalcBatchMapper batchMapper;
    @Mock
    private Phase2ScopeConfigMapper scopeConfigMapper;

    @Mock private DailyCapacityService capacityService;
    @Mock private MrpDecisionIssueMapper issueMapper;
    @Mock private com.mrp.audit.service.AuditService audit;

    @InjectMocks
    private DailyPlanningService dailyPlanningService;

    private MrpCalcBatch testBatch;
    private MrpTerminalDemand testDemand;
    private MrpDailyPlanDetail testPlan;

    @BeforeEach
    void setUp() {
        testBatch = new MrpCalcBatch();
        testBatch.setId(1L);
        testBatch.setStatus("READY");
        testBatch.setActiveRunId(1L);
        testBatch.setProductionCalendarVersionId(1L);
        testBatch.setMaterialPolicyVersionId(1L);

        testDemand = new MrpTerminalDemand();
        testDemand.setId(1L);
        testDemand.setRunId(1L);
        testDemand.setBatchId(1L);
        testDemand.setFactoryCode("F001");
        testDemand.setRootMaterialCode("RM001");
        testDemand.setTerminalMaterialCode("TM001");
        testDemand.setTerminalType("RAW");
        testDemand.setSupplyMode("SELF");
        testDemand.setSourceMonth(LocalDate.now().withDayOfMonth(1));
        testDemand.setQuantity(BigDecimal.valueOf(100));

        testPlan = new MrpDailyPlanDetail();
        testPlan.setId(1L);
        testPlan.setRunId(1L);
        testPlan.setBatchId(1L);
        testPlan.setFactoryCode("F001");
        testPlan.setRootMaterialCode("RM001");
        testPlan.setTerminalMaterialCode("TM001");
        testPlan.setTerminalType("RAW");
        testPlan.setSupplyMode("SELF");
        testPlan.setPathGroupKey("path1");
        testPlan.setSourceMonth(LocalDate.now().withDayOfMonth(1));
        testPlan.setSourceDate(LocalDate.now());
        testPlan.setScheduleDate(LocalDate.now());
        testPlan.setSystemQuantity(BigDecimal.valueOf(100));
        testPlan.setIsProductionDay(true);
        testPlan.setRowKey("rowkey1");
    }

    @Test
    void automaticProjectionRestoresExactSystemValuesAndReturnsIndependentList() {
        when(overrideMapper.selectByBatchId(1L)).thenReturn(List.of());
        testPlan.setSystemQuantity(new BigDecimal("83.500001"));
        testPlan.setEffectiveQuantity(new BigDecimal("9"));
        List<MrpDailyPlanDetail> input = List.of(testPlan);

        List<MrpDailyPlanDetail> result = dailyPlanningService.applyOverrides(1L, input);

        assertNotSame(input, result);
        assertSame(testPlan, result.getFirst());
        assertEquals(new BigDecimal("83.500001"), result.getFirst().getEffectiveQuantity());
        assertEquals("rowkey1", result.getFirst().getRowKey());
        result.clear();
        assertEquals(1, input.size());
        verifyNoInteractions(batchMapper);
    }

    @Test
    void testGetDailyPlansWithActiveRun() {
        // Given
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));

        // When
        List<MrpDailyPlanDetail> result = dailyPlanningService.getDailyPlans(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("TM001", result.get(0).getTerminalMaterialCode());
    }

    @Test
    void distributePreservesLockedQuantityAndRedistributesOnlyUnlockedRemainder() {
        LocalDate month = LocalDate.of(2026, 8, 1);
        testDemand.setSourceMonth(month);
        testDemand.setQuantity(new BigDecimal("100"));
        when(terminalDemandMapper.selectByRunId(1L)).thenReturn(List.of(testDemand));
        List<ProductionCalendarDay> days = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 7, 25);
             !date.isAfter(LocalDate.of(2026, 8, 31));
             date = date.plusDays(1)) {
            ProductionCalendarDay calendarDay = new ProductionCalendarDay();
            calendarDay.setCalendarDate(date);
            calendarDay.setIsProductionDay(true);
            calendarDay.setAllocationWeight(BigDecimal.ONE);
            days.add(calendarDay);
        }
        when(calendarDayMapper.selectByVersionId(1L)).thenReturn(days);
        when(materialPolicyService.getDetailMap(1L)).thenReturn(Collections.emptyMap());

        // First capture the deterministic row key generated for the locked date, then rerun.
        when(executionMapper.selectByBatchId(1L)).thenReturn(Collections.emptyList());
        DailyDistributionEngine.DistributionResult initial = dailyPlanningService.distribute(1L, 1L, 1L, 1L);
        MrpDailyPlanDetail first = initial.details.stream()
                .filter(item -> item.getScheduleDate().equals(LocalDate.of(2026, 7, 25)))
                .findFirst().orElseThrow();

        MrpDailyExecution locked = new MrpDailyExecution();
        locked.setBatchId(1L);
        locked.setExecutionStatus("LOCKED");
        locked.setScheduleDate(first.getScheduleDate());
        locked.setPlannedQuantity(new BigDecimal("10"));
        locked.setRowKey(first.getRowKey());

        reset(dailyPlanMapper);
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of(locked));
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        DailyDistributionEngine.DistributionResult result = dailyPlanningService.distribute(1L, 1L, 1L, 1L);

        MrpDailyPlanDetail preserved = result.details.stream()
                .filter(item -> locked.getRowKey().equals(item.getRowKey()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(preserved.getSystemQuantity()));
        BigDecimal total = result.details.stream().map(MrpDailyPlanDetail::getSystemQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100").compareTo(total));
    }

    @Test
    void testGetDailyPlansWithoutActiveRun() {
        // Given
        testBatch.setActiveRunId(null);
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);

        // When
        List<MrpDailyPlanDetail> result = dailyPlanningService.getDailyPlans(1L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDailyPlansWithDateRange() {
        // Given
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = LocalDate.now().plusDays(7);
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));

        // When
        List<MrpDailyPlanDetail> result = dailyPlanningService.getDailyPlans(1L, startDate, endDate);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetWeeklySummary() {
        // Given
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));
        BusinessWeekDetail businessWeek = new BusinessWeekDetail();
        businessWeek.setBusinessMonth(testPlan.getScheduleDate().withDayOfMonth(1));
        businessWeek.setWeekCode("W1");
        businessWeek.setStartDate(testPlan.getScheduleDate());
        businessWeek.setEndDate(testPlan.getScheduleDate().plusDays(6));
        when(scopeConfigMapper.selectPublishedBusinessWeeksByRange(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(businessWeek));

        // When
        List<DailyPlanningService.WeeklySummaryRow> result = dailyPlanningService.getWeeklySummary(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testPlan.getSystemQuantity(), result.get(0).totalQuantity);
        assertTrue(result.get(0).weekKey.endsWith(" W1"));
    }

    @Test
    void weeklySummaryIgnoresOverlappingBoundaryWeekDefinitions() {
        LocalDate start = LocalDate.of(2026, 9, 28);
        MrpDailyPlanDetail firstPlan = new MrpDailyPlanDetail();
        firstPlan.setRunId(1L);
        firstPlan.setBatchId(1L);
        firstPlan.setFactoryCode("F001");
        firstPlan.setRootMaterialCode("RM001");
        firstPlan.setTerminalMaterialCode("TM001");
        firstPlan.setTerminalType("RAW");
        firstPlan.setSupplyMode("SELF");
        firstPlan.setPathGroupKey("RM001|TM001|RAW|SELF|2026-09");
        firstPlan.setScheduleDate(start);
        firstPlan.setSystemQuantity(BigDecimal.TEN);
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(firstPlan));
        BusinessWeekDetail september = new BusinessWeekDetail();
        september.setBusinessMonth(LocalDate.of(2026, 9, 1));
        september.setWeekCode("W5");
        september.setStartDate(start);
        september.setEndDate(LocalDate.of(2026, 10, 4));
        BusinessWeekDetail october = new BusinessWeekDetail();
        october.setBusinessMonth(LocalDate.of(2026, 10, 1));
        october.setWeekCode("W1");
        october.setStartDate(start);
        october.setEndDate(LocalDate.of(2026, 10, 4));
        when(batchMapper.selectBusinessWeeksByBatchAndRange(eq(1L), any(), any()))
                .thenReturn(List.of(september, october));

        List<DailyPlanningService.WeeklySummaryRow> result = dailyPlanningService.getWeeklySummary(1L);
        assertEquals(1, result.size());
        assertEquals("2026-09 W5", result.get(0).weekKey);
    }

    @Test void partiallyOverlappingWeeksRetainUncoveredDaysWithoutMutatingFrozenDefinitions() {
        LocalDate start = LocalDate.of(2026, 9, 28);
        var september = new BusinessWeekDetail();
        september.setBusinessMonth(LocalDate.of(2026, 9, 1)); september.setWeekCode("W5");
        september.setStartDate(start); september.setEndDate(LocalDate.of(2026, 10, 4));
        var october = new BusinessWeekDetail();
        october.setId(20L); october.setVersionId(30L); october.setSortNo(1);
        october.setBusinessMonth(LocalDate.of(2026, 10, 1)); october.setWeekCode("W1");
        october.setStartDate(LocalDate.of(2026, 10, 1)); october.setEndDate(LocalDate.of(2026, 10, 7));
        when(batchMapper.selectBusinessWeeksByBatchAndRange(eq(1L), any(), any())).thenReturn(List.of(september, october));
        var normalized = dailyPlanningService.getBusinessWeeks(1L, start, october.getEndDate());
        assertEquals(2, normalized.size());
        assertEquals(LocalDate.of(2026, 10, 5), normalized.get(1).getStartDate());
        assertEquals(october.getEndDate(), normalized.get(1).getEndDate());
        assertEquals(october.getId(), normalized.get(1).getId());
        assertEquals(october.getVersionId(), normalized.get(1).getVersionId());
        assertEquals(october.getSortNo(), normalized.get(1).getSortNo());
        assertEquals(LocalDate.of(2026, 10, 1), october.getStartDate());
        assertNotSame(october, normalized.get(1));
        for (int i = 0; i < 10; i++) {
            LocalDate date = start.plusDays(i);
            assertEquals(1, normalized.stream().filter(w -> !date.isBefore(w.getStartDate()) && !date.isAfter(w.getEndDate())).count());
        }
        List<MrpDailyPlanDetail> plans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            var plan = new MrpDailyPlanDetail(); plan.setRunId(1L); plan.setBatchId(1L); plan.setFactoryCode("F001");
            plan.setRootMaterialCode("ROOT"); plan.setTerminalMaterialCode("TM001"); plan.setTerminalType("RAW");
            plan.setSupplyMode("SELF_MADE"); plan.setScheduleDate(start.plusDays(i)); plan.setSystemQuantity(BigDecimal.TEN); plans.add(plan);
        }
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(plans);
        var summary = dailyPlanningService.getWeeklySummary(1L);
        assertEquals(List.of("2026-09 W5", "2026-10 W1"), summary.stream().map(w -> w.weekKey).toList());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(summary.stream().map(w -> w.totalQuantity).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Test void monthlySummarySplitsTerminalUnitsAndKeepsUnknownMaterialsIndependent() {
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(capacityService.units(1L)).thenReturn(Map.of("TM001","件","KG","kg"));
        List<MrpDailyPlanDetail> plans = new ArrayList<>(List.of(testPlan));
        for (String code : List.of("KG","UNKNOWN_A","UNKNOWN_B")) {
            var plan = new MrpDailyPlanDetail(); plan.setRunId(1L); plan.setBatchId(1L); plan.setFactoryCode("F001");
            plan.setRootMaterialCode("ROOT"); plan.setTerminalMaterialCode(code); plan.setTerminalType("RAW"); plan.setSupplyMode("SELF_MADE");
            plan.setSourceMonth(testPlan.getSourceMonth()); plan.setScheduleDate(testPlan.getScheduleDate()); plan.setSystemQuantity(new BigDecimal("50")); plans.add(plan);
        }
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(plans);
        var summary = dailyPlanningService.getMonthlySummary(1L);
        assertEquals(4,summary.size());
        assertEquals(Set.of("UNIT|件","UNIT|kg","MATERIAL|UNKNOWN_A","MATERIAL|UNKNOWN_B"),
                summary.stream().map(row -> row.aggregationKey).collect(java.util.stream.Collectors.toSet()));
        assertTrue(summary.stream().allMatch(row -> "TERMINAL".equals(row.view)));
    }

    @Test
    void testGetMonthlySummary() {
        // Given
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));

        // When
        List<DailyPlanningService.MonthlySummaryRow> result = dailyPlanningService.getMonthlySummary(1L);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGetSourceMonthConservation() {
        // Given
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(terminalDemandMapper.selectByRunId(1L)).thenReturn(List.of(testDemand));
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));
        when(unallocatedMapper.selectByRunId(1L)).thenReturn(Collections.emptyList());

        // When
        DailyPlanningService.ConservationCheckResult result = dailyPlanningService.getSourceMonthConservation(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.totalDemands);
        assertEquals(0, result.mismatchCount); // Should match because quantities are equal
    }

    @Test
    void testGetSourceMonthConservationWithMismatch() {
        // Given
        testDemand.setQuantity(BigDecimal.valueOf(200)); // Different from plan quantity
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(terminalDemandMapper.selectByRunId(1L)).thenReturn(List.of(testDemand));
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));
        when(unallocatedMapper.selectByRunId(1L)).thenReturn(Collections.emptyList());

        // When
        DailyPlanningService.ConservationCheckResult result = dailyPlanningService.getSourceMonthConservation(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.totalDemands);
        assertEquals(1, result.mismatchCount); // Should have mismatch
    }

    @Test
    void testSourceMonthConservationIncludesPendingUnallocatedQuantity() {
        testDemand.setQuantity(BigDecimal.valueOf(200));
        MrpUnallocatedQuantity pending = new MrpUnallocatedQuantity();
        pending.setDemandKey("RM001|TM001|RAW|SELF|" +
                testDemand.getSourceMonth().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        pending.setQuantity(BigDecimal.valueOf(100));
        pending.setStatus("PENDING");

        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(terminalDemandMapper.selectByRunId(1L)).thenReturn(List.of(testDemand));
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));
        when(unallocatedMapper.selectByRunId(1L)).thenReturn(List.of(pending));

        DailyPlanningService.ConservationCheckResult result =
                dailyPlanningService.getSourceMonthConservation(1L);

        assertEquals(1, result.totalDemands);
        assertEquals(0, result.mismatchCount);
    }

    @Test
    void testSourceMonthConservationDoesNotDoubleCountResolvedUnallocatedQuantity() {
        MrpUnallocatedQuantity resolved = new MrpUnallocatedQuantity();
        resolved.setDemandKey("RM001|TM001|RAW|SELF|" +
                testDemand.getSourceMonth().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        resolved.setQuantity(BigDecimal.valueOf(100));
        resolved.setStatus("RESOLVED");

        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(terminalDemandMapper.selectByRunId(1L)).thenReturn(List.of(testDemand));
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));
        when(unallocatedMapper.selectByRunId(1L)).thenReturn(List.of(resolved));

        DailyPlanningService.ConservationCheckResult result =
                dailyPlanningService.getSourceMonthConservation(1L);

        assertEquals(0, result.mismatchCount);
    }

    @Test
    void testGetUnallocated() {
        // Given
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(unallocatedMapper.selectByRunId(1L)).thenReturn(Collections.emptyList());

        // When
        List<MrpUnallocatedQuantity> result = dailyPlanningService.getUnallocated(1L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void pendingUnallocatedPageUsesActiveRunAndPendingStatus() {
        testBatch.setActiveRunId(42L);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        MrpUnallocatedQuantity pending = new MrpUnallocatedQuantity();
        pending.setRunId(42L); pending.setStatus("PENDING");
        when(unallocatedMapper.selectPageByRunIdAndStatus(42L, "PENDING", "日历", 20, 20)).thenReturn(List.of(pending));
        when(unallocatedMapper.countByRunIdAndStatus(42L, "PENDING", "日历")).thenReturn(21);
        var result = dailyPlanningService.getPendingUnallocatedPage(1L, " 日历 ", 2, 20);
        assertEquals(21, result.total());
        assertEquals(List.of(pending), result.items());
        assertEquals(2, result.totalPages());
        verify(unallocatedMapper, never()).selectByRunId(anyLong());
    }

    @Test
    void pendingUnallocatedPageRejectsInvalidPagingAndEmptyRun() {
        assertThrows(IllegalArgumentException.class, () -> dailyPlanningService.getPendingUnallocatedPage(1L, "", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> dailyPlanningService.getPendingUnallocatedPage(1L, "", 1, 101));
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        testBatch.setActiveRunId(null);
        assertEquals(0, dailyPlanningService.getPendingUnallocatedPage(1L, "", 1, 20).total());
        verifyNoInteractions(unallocatedMapper);
    }

    @Test
    void testSaveOverride() {
        // Given
        MrpDailyPlanOverride override = new MrpDailyPlanOverride();
        override.setBatchId(1L);
        override.setScheduleDate(LocalDate.now());
        override.setTerminalMaterialCode("TM001");
        override.setFactoryCode(testPlan.getFactoryCode());
        override.setRootMaterialCode(testPlan.getRootMaterialCode());
        override.setTerminalType(testPlan.getTerminalType());
        override.setSupplyMode(testPlan.getSupplyMode());
        override.setPathGroupKey(testPlan.getPathGroupKey());
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));
        override.setManualQuantity(BigDecimal.valueOf(50));
        override.setReason("业务调整");
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        
        when(executionService.isDateLockedOrExecuted(1L, LocalDate.now())).thenReturn(false);

        // When
        dailyPlanningService.saveOverride(override);

        // Then
        verify(overrideMapper).insert(override);
    }

    @Test
    void testSaveOverrideUpdatesExistingBusinessKey() {
        MrpDailyPlanOverride override = new MrpDailyPlanOverride();
        override.setBatchId(1L);
        override.setScheduleDate(LocalDate.now());
        override.setTerminalMaterialCode("TM001");
        override.setFactoryCode(testPlan.getFactoryCode());
        override.setRootMaterialCode(testPlan.getRootMaterialCode());
        override.setTerminalType(testPlan.getTerminalType());
        override.setSupplyMode(testPlan.getSupplyMode());
        override.setPathGroupKey(testPlan.getPathGroupKey());
        when(dailyPlanMapper.selectByRunId(1L)).thenReturn(List.of(testPlan));
        override.setManualQuantity(BigDecimal.valueOf(60));
        override.setReason("第二次调整");

        MrpDailyPlanOverride existing = new MrpDailyPlanOverride();
        existing.setId(9L);
        existing.setRevision(0);
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(executionService.isDateLockedOrExecuted(1L, LocalDate.now())).thenReturn(false);
        when(overrideMapper.selectByBusinessKey(override)).thenReturn(existing);

        dailyPlanningService.saveOverride(override);

        assertEquals(1, existing.getRevision());
        assertEquals(BigDecimal.valueOf(60), existing.getManualQuantity());
        verify(overrideMapper).update(existing);
        verify(overrideMapper, never()).insert(any());
    }

    @Test
    void testSaveOverrideRequiresReason() {
        MrpDailyPlanOverride override = new MrpDailyPlanOverride();
        override.setBatchId(1L);
        override.setScheduleDate(LocalDate.now());
        override.setManualQuantity(BigDecimal.TEN);
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(executionService.isDateLockedOrExecuted(1L, LocalDate.now())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> dailyPlanningService.saveOverride(override));
    }

    @Test
    void testSaveOverrideOnLockedDate() {
        // Given
        MrpDailyPlanOverride override = new MrpDailyPlanOverride();
        override.setBatchId(1L);
        override.setScheduleDate(LocalDate.now());
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        
        when(executionService.isDateLockedOrExecuted(1L, LocalDate.now())).thenReturn(true);

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            dailyPlanningService.saveOverride(override);
        });
    }

    @Test
    void testRestoreAuto() {
        // Given
        MrpDailyPlanOverride existing = new MrpDailyPlanOverride();
        existing.setId(1L);
        existing.setBatchId(1L);
        when(overrideMapper.selectById(1L)).thenReturn(existing);
        lenient().when(batchMapper.selectById(1L)).thenReturn(testBatch);
        lenient().when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);

        // When
        dailyPlanningService.restoreAuto(1L);

        // Then
        verify(overrideMapper).deleteById(1L);
    }

    @Test
    void testRestoreAutoNotFound() {
        // Given
        when(overrideMapper.selectById(1L)).thenReturn(null);

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            dailyPlanningService.restoreAuto(1L);
        });
    }

    @Test
    void testGetOverrides() {
        // Given
        when(overrideMapper.selectByBatchId(1L)).thenReturn(Collections.emptyList());

        // When
        List<MrpDailyPlanOverride> result = dailyPlanningService.getOverrides(1L);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
