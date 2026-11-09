package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
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
class BatchOrchestratorServiceTest {

    @Mock
    private MrpCalcBatchMapper batchMapper;
    @Mock
    private MrpCalcRunMapper runMapper;
    @Mock
    private MrpDecisionIssueMapper issueMapper;
    @Mock
    private MrpCalculationService calculationService;
    @Mock
    private DailyPlanningService dailyPlanningService;
    @Mock
    private MrpDailyPlanDetailMapper dailyPlanMapper;
    @Mock
    private MrpUnallocatedQuantityMapper unallocatedMapper;
    @Mock
    private MrpInventoryPoolMapper poolMapper;
    @Mock
    private MrpInventoryAllocationMapper allocationMapper;
    @Mock
    private BomSnapshotService bomSnapshotService;
    @Mock
    private InventoryNormalizationService inventoryService;
    @Mock
    private MrpInventoryBalanceMapper balanceMapper;
    @Mock
    private CategoryRoleVersionService categoryRoleService;
    @Mock
    private MaterialPolicyVersionService materialPolicyService;
    @Mock
    private CalcTaskMapper calcTaskMapper;
    @Mock
    private ForecastMapper forecastMapper;
    @Mock
    private MrpDailyExecutionMapper executionMapper;
    @Mock
    private MrpRequirementDetailMapper requirementDetailMapper;
    @Mock
    private MrpTerminalDemandMapper terminalDemandMapper;

    @Mock private PlanningReleaseValidationService releaseValidation;
    @Mock private MrpWorkbenchReviewService workbenchReviews;
    @Mock private PlanningCalendarValidationService calendarValidation;
    @Mock private MrpStockRiskMapper stockRisks;
    @Mock private MaterialPolicyNoticeService policyNotices;
    @Mock private ConfigSnapshotService configSnapshots;
    @Mock private DailyCapacityService capacityService;
    @Mock private com.mrp.audit.service.AuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private BatchOrchestratorService orchestratorService;

    @Mock
    private AutomaticCapacityReviewService automaticCapacityReview;

    private MrpCalcBatch testBatch;
    private MrpCalcRun testRun;

    @BeforeEach void calendarIsComplete() {
        lenient().when(calendarValidation.check(any(MrpCalcBatch.class), eq(false)))
                .thenReturn(new PlanningCalendarValidationService.CalendarCheck(0, List.of(), List.of()));
    }

    @Test void precheckStopsBeforeAnalysisWhenCalendarIsIncomplete() {
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(calendarValidation.check(testBatch, false)).thenReturn(new PlanningCalendarValidationService.CalendarCheck(
                1, List.of(new PlanningCalendarValidationService.MissingDay("F001", LocalDate.parse("2026-10-29"))), List.of()));
        var failure = assertThrows(IllegalStateException.class, () -> orchestratorService.precheck(1L));
        assertTrue(failure.getMessage().contains("2026-10-29"));
        verify(batchMapper, never()).updateStatusForce(eq(1L), eq("ANALYZING"));
    }

    @Test void frozenWarehousePriorityAndMaintenanceOrderChooseGoodPools() {
        var frozen = new ArrayList<FactoryWarehouseMappingDetail>();
        frozen.add(warehouse("WH-LATE", null, 8L, true));
        frozen.add(warehouse("WH-DEFECTIVE", 1, 1L, false));
        frozen.add(warehouse("WH-SECOND", 2, 3L, true));
        frozen.add(warehouse("WH-FIRST", 1, 5L, true));
        frozen.add(warehouse("WH-EARLY", null, 2L, true));
        var pools = List.of(pool("WH-LATE"), pool("WH-DEFECTIVE"), pool("WH-SECOND"),
                pool("WH-FIRST"), pool("WH-EARLY"));

        var ordered = BatchOrchestratorService.orderedGoodPools(frozen, pools).get("F001|M001");
        assertEquals(List.of("WH-FIRST", "WH-SECOND", "WH-EARLY", "WH-LATE"),
                ordered.stream().map(MrpInventoryPool::getWarehouseCode).toList());

        // Changing live master data after batch creation cannot change this frozen ordering.
        var live = List.of(warehouse("WH-LATE", 1, 1L, true));
        assertEquals("WH-FIRST", BatchOrchestratorService.orderedGoodPools(frozen, pools)
                .get("F001|M001").getFirst().getWarehouseCode());
        assertEquals(List.of("WH-LATE"), BatchOrchestratorService.orderedGoodPools(live, pools)
                .get("F001|M001").stream().map(MrpInventoryPool::getWarehouseCode).toList());
    }

    @Test void automaticAllocationConsumesFrozenGoodWarehousesInPriorityOrder() {
        var frozen = List.of(warehouse("WH-NONE-2", null, 9L, true),
                warehouse("WH-DEFECTIVE", 1, 1L, false),
                warehouse("WH-PRIORITY-2", 2, 4L, true),
                warehouse("WH-PRIORITY-1", 1, 7L, true),
                warehouse("WH-NONE-1", null, 2L, true));
        var pools = List.of(pool(1L, "WH-NONE-2"), pool(2L, "WH-DEFECTIVE"),
                pool(3L, "WH-PRIORITY-2"), pool(4L, "WH-PRIORITY-1"), pool(5L, "WH-NONE-1"));
        var demand = new MrpRequirementDetail();
        demand.setRootMaterialCode("ROOT"); demand.setCurrentMaterialCode("M001");
        demand.setSourceMonth(LocalDate.of(2027, 1, 1));
        demand.setGrossQty(new BigDecimal("35")); demand.setInventoryUsedQty(new BigDecimal("35"));
        demand.setPathHash("path");
        var allocations = new ArrayList<MrpInventoryAllocation>();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(orchestratorService,
                "collectAutomaticAllocations", List.of(demand), "F001",
                BatchOrchestratorService.orderedGoodPools(frozen, pools), Set.of(), Map.of("ROOT|2027-01-01", 1), allocations);
        assertEquals(List.of(4L, 3L, 5L, 1L), allocations.stream().map(MrpInventoryAllocation::getPoolId).toList());
        assertEquals(List.of("10", "10", "10", "5"), allocations.stream()
                .map(row -> row.getAllocatedQty().toPlainString()).toList());
    }

    private static FactoryWarehouseMappingDetail warehouse(String code, Integer priority, Long order, boolean good) {
        var row = new FactoryWarehouseMappingDetail();
        row.setFactoryCode("F001"); row.setWarehouseCode(code); row.setPriorityNo(priority);
        row.setMaintenanceOrder(order); row.setGoodWarehouse(good);
        return row;
    }

    private static MrpInventoryPool pool(String code) {
        var row = new MrpInventoryPool();
        row.setFactoryCode("F001"); row.setWarehouseCode(code); row.setMaterialCode("M001");
        return row;
    }

    private static MrpInventoryPool pool(Long id, String code) {
        var row = pool(code);
        row.setId(id); row.setAvailableQty(new BigDecimal("10"));
        row.setReservedQty(BigDecimal.ZERO); row.setAllocatedQty(BigDecimal.ZERO);
        return row;
    }

    @Test void capacityFingerprintBindsFrozenRuleAndCalendarEvenWithIdenticalFactsAndLoads() {
        testBatch.setActiveRunId(10L);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(dailyPlanningService.getDailyPlans(1L)).thenReturn(List.of());
        var original = orchestratorService.capacityReview(1L).fingerprint();
        testBatch.setProductionCalendarVersionId(2L);
        var changedCalendar = orchestratorService.capacityReview(1L).fingerprint();
        assertNotEquals(original,changedCalendar);
        testBatch.setRuleVersion(ProductionCapacityRules.VERSION);
        assertNotEquals(changedCalendar,orchestratorService.capacityReview(1L).fingerprint());
    }

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(orchestratorService, "objectMapper", objectMapper);
        testBatch = new MrpCalcBatch();
        testBatch.setId(1L);
        testBatch.setStatus("DRAFT");
        testBatch.setFactoryCode("F001");
        testBatch.setForecastVersionId(1L);
        testBatch.setInventorySnapshotId(1L);
        testBatch.setBomSnapshotId(1L);
        testBatch.setCategoryRoleVersionId(1L);
        testBatch.setMaterialPolicyVersionId(1L);
        testBatch.setProductionCalendarVersionId(1L);
        testBatch.setFactoryWarehouseMappingVersionId(2L);
        testBatch.setStockPolicyVersionId(3L);
        testBatch.setBusinessWeekVersionId(4L);
        testBatch.setInputChecksum("test-checksum");
        testBatch.setVersionNo(1);
        testBatch.setActiveRequestKey(UUID.randomUUID().toString());
        testBatch.setIssueCount(0);
        testBatch.setUnresolvedCount(0);
        testBatch.setVersion(0);
        testBatch.setRuleVersion("v2");
        testBatch.setRevisionNo(0);
        testBatch.setCreatedBy(1L);
        testBatch.setCreatedAt(LocalDateTime.now());
        testBatch.setUpdatedAt(LocalDateTime.now());
        lenient().when(allocationMapper.selectByBatchId(1L)).thenReturn(Collections.emptyList());
        lenient().when(inventoryService.getPoolsByBatch(1L)).thenReturn(Collections.emptyList());

        testRun = new MrpCalcRun();
        testRun.setId(1L);
        testRun.setBatchId(1L);
        testRun.setStatus("RUNNING");
        testRun.setStartedAt(LocalDateTime.now());
    }

    @Test
    void testPrecheckWithNoForecastData() {
        // Given
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(bomSnapshotService.edgesForBatch(testBatch)).thenReturn(Collections.emptyList());
        when(materialPolicyService.getDetailMap(1L)).thenReturn(Collections.emptyMap());
        when(forecastMapper.selectDetailsByVersionId(1L)).thenReturn(Collections.emptyList());
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // When
        MrpCalcBatch result = orchestratorService.precheck(1L);

        // Then
        assertNotNull(result);
        assertEquals("WAITING_DECISION", result.getStatus());
        verify(issueMapper).insert(argThat(issue -> 
            "BLOCKING".equals(issue.getSeverity()) && 
            "NO_FORECAST_DATA".equals(issue.getIssueType())));
    }

    @Test
    void testPrecheckRejectsLegacyBatchWithoutLockedPhase2Configuration() {
        testBatch.setFactoryWarehouseMappingVersionId(null);
        testBatch.setStockPolicyVersionId(null);
        testBatch.setBusinessWeekVersionId(null);
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> orchestratorService.precheck(1L));

        assertTrue(error.getMessage().contains("未锁定当前排产所需配置"));
        verify(batchMapper, never()).updateStatusForce(anyLong(), anyString());
    }

    @Test
    void testPrecheckWithBlockingIssues() {
        // Given
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(bomSnapshotService.edgesForBatch(testBatch)).thenReturn(Collections.emptyList());
        when(categoryRoleService.getMaterialRoleMap(eq(1L), anyCollection())).thenReturn(Collections.emptyMap());
        when(materialPolicyService.getDetailMap(1L)).thenReturn(Collections.emptyMap());
        
        // Return forecast data
        ForecastDetail forecast = new ForecastDetail(
            1L, 1L, "F001", "M001", "Material 1", "BL001", "FORM001", 
            "PRJ001", "PLT001", "MOLD001", "ACTIVE",
            LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100)
        );
        when(forecastMapper.selectDetailsByVersionId(1L)).thenReturn(List.of(forecast));
        
        // Return blocking issue
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // When
        MrpCalcBatch result = orchestratorService.precheck(1L);

        // Then
        assertNotNull(result);
        // Should be WAITING_DECISION due to no forecast data blocking issue
        assertEquals("WAITING_DECISION", result.getStatus());
        assertEquals(2, result.getIssueCount());
        assertEquals(2, result.getUnresolvedCount());
    }

    @Test
    void testSubmitCalculationWithOpenIssues() {
        // Given
        testBatch.setStatus("READY_TO_CALCULATE");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        
        MrpDecisionIssue openIssue = new MrpDecisionIssue();
        openIssue.setStatus("OPEN");
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(List.of(openIssue));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orchestratorService.submitCalculation(1L, 1L);
        });
    }

    @Test
    void testSubmitCalculationWithPendingUnallocated() {
        // Given
        testBatch.setStatus("READY_TO_CALCULATE");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());
        
        MrpUnallocatedQuantity pendingUnallocated = new MrpUnallocatedQuantity();
        pendingUnallocated.setStatus("PENDING");
        when(unallocatedMapper.selectByBatchIdAndStatus(1L, "PENDING")).thenReturn(List.of(pendingUnallocated));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orchestratorService.submitCalculation(1L, 1L);
        });
    }

    @Test
    void testPublishWithOpenIssues() {
        // Given
        testBatch.setStatus("READY");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        
        MrpDecisionIssue openIssue = new MrpDecisionIssue();
        openIssue.setStatus("OPEN");
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(List.of(openIssue));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orchestratorService.publish(1L);
        });
    }

    @Test
    void testPublishWithPendingUnallocated() {
        // Given
        testBatch.setStatus("READY");
        testBatch.setActiveRunId(1L);
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());
        
        MrpUnallocatedQuantity pendingUnallocated = new MrpUnallocatedQuantity();
        pendingUnallocated.setStatus("PENDING");
        when(unallocatedMapper.selectByRunIdAndStatus(1L, "PENDING")).thenReturn(List.of(pendingUnallocated));

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orchestratorService.publish(1L);
        });
    }

    @Test
    void testPublishSuccess() {
        // Given
        testBatch.setStatus("READY");
        testBatch.setActiveRunId(1L);
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());
        when(unallocatedMapper.selectByRunIdAndStatus(1L, "PENDING")).thenReturn(Collections.emptyList());
        when(dailyPlanningService.getSourceMonthConservation(eq(1L), eq(1L), anyList())).thenReturn(
                new DailyPlanningService.ConservationCheckResult(1, 0, Collections.emptyList()));
        when(requirementDetailMapper.selectByRunId(1L)).thenReturn(Collections.emptyList());
        when(terminalDemandMapper.selectByRunId(1L)).thenReturn(Collections.emptyList());
        when(executionMapper.selectByBatchId(1L)).thenReturn(Collections.emptyList());
        when(batchMapper.insert(any())).thenReturn(1);

        // When
        MrpCalcBatch result = orchestratorService.publish(1L);

        // Then
        assertNotNull(result);
        assertEquals("PUBLISHED", result.getStatus());
        verify(batchMapper, atLeastOnce()).insert(any());
        verify(runMapper).insert(any());
    }

    @Test
    void testReviseSuccess() {
        // Given
        testBatch.setStatus("PUBLISHED");
        testBatch.setActiveRunId(1L);
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(executionMapper.selectByBatchId(1L)).thenReturn(Collections.emptyList());
        when(batchMapper.insert(any())).thenReturn(1);
        var frozenWarehouse = new FactoryWarehouseMappingDetail();
        frozenWarehouse.setFactoryCode("F001"); frozenWarehouse.setWarehouseCode("WH-A");
        when(batchMapper.selectFactoryWarehousesByBatchId(any())).thenReturn(List.of(frozenWarehouse));
        when(inventoryService.snapshotIdForWarehouse(isNull(), eq("WH-A"), eq(1L))).thenReturn(1L);
        when(batchMapper.update(any())).thenReturn(1);

        // When
        MrpCalcBatch result = orchestratorService.revise(1L);

        // Then
        assertNotNull(result);
        assertEquals("DRAFT", result.getStatus());
        assertEquals(1L, result.getRevisionOfBatchId());
        verify(batchMapper, atLeastOnce()).insert(any());
        verify(batchMapper, atLeastOnce()).update(any());
        verify(batchMapper).copyFactoryScopes(1L, result.getId());
        verify(batchMapper).copyBusinessWeekScopes(1L, result.getId());
        verify(inventoryService).createPoolsFromSnapshot(result.getId(), 1L, "F001", 3L, Set.of("WH-A"));
    }

    @Test
    void publishAndReviseCopyLockedAndExecutedFacts() {
        testBatch.setStatus("READY");
        testBatch.setActiveRunId(1L);
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());
        when(unallocatedMapper.selectByRunIdAndStatus(1L, "PENDING")).thenReturn(Collections.emptyList());
        when(dailyPlanningService.getSourceMonthConservation(eq(1L), eq(1L), anyList())).thenReturn(
                new DailyPlanningService.ConservationCheckResult(1, 0, Collections.emptyList()));
        when(requirementDetailMapper.selectByRunId(1L)).thenReturn(Collections.emptyList());
        when(terminalDemandMapper.selectByRunId(1L)).thenReturn(Collections.emptyList());
        when(batchMapper.insert(any())).thenAnswer(invocation -> {
            MrpCalcBatch inserted = invocation.getArgument(0);
            if (inserted.getId() == null) inserted.setId(2L);
            return 1;
        });
        MrpDailyExecution executed = new MrpDailyExecution();
        executed.setRowKey("rk");
        executed.setScheduleDate(LocalDate.of(2026, 8, 3));
        executed.setExecutionStatus("EXECUTED");
        executed.setActualQuantity(BigDecimal.valueOf(8));
        executed.setPlannedQuantity(BigDecimal.valueOf(9));
        when(executionMapper.selectByBatchId(1L)).thenReturn(List.of(executed));

        MrpCalcBatch published = orchestratorService.publish(1L);
        verify(policyNotices).copyRun(eq(1L), eq(published.getId()), eq(published.getActiveRunId()));
        verify(stockRisks).copyRun(eq(1L), eq(published.getId()), eq(published.getActiveRunId()));
        verify(releaseValidation).requirePublishable(testBatch);
        verify(executionMapper).insert(argThat(item -> item.getBatchId().equals(published.getId())
                && "EXECUTED".equals(item.getExecutionStatus())
                && item.getActualQuantity().compareTo(BigDecimal.valueOf(8)) == 0
                && item.getPlannedQuantity().compareTo(BigDecimal.valueOf(9)) == 0));
    }

    @Test
    void testGetBatchSummary() {
        // Given
        testBatch.setActiveRunId(1L);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(batchMapper.selectSummaryCounts(1L,1L)).thenReturn(new MrpCalcBatchMapper.SummaryCounts(
                3, 2, 224100, 5, 4, 10, new BigDecimal("123.500001"), new BigDecimal("100.250001")));

        // When
        BatchOrchestratorService.BatchSummary summary = orchestratorService.getBatchSummary(1L);

        // Then
        assertNotNull(summary);
        assertEquals(1L, summary.batchId);
        assertEquals("DRAFT", summary.status);
        assertEquals("F001", summary.factoryCode);
        assertEquals(224100, summary.dailyPlanCount);
        assertEquals(new BigDecimal("123.500001"), summary.totalAvailable);
        verify(dailyPlanMapper, never()).selectByRunId(any());
    }

    @Test
    void testTransitionStatus() {
        // Given
        testBatch.setStatus("DRAFT");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);

        // When
        MrpCalcBatch result = orchestratorService.transitionStatus(1L, "DRAFT", "ANALYZING");

        // Then
        assertNotNull(result);
        assertEquals("ANALYZING", result.getStatus());
    }

    @Test
    void directStatusTransitionCannotBypassPublication() {
        testBatch.setStatus("READY");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        assertThrows(IllegalStateException.class, () -> orchestratorService.transitionStatus(1L, "READY", "PUBLISHED"));
        verify(batchMapper, never()).update(any());
        assertEquals("READY", testBatch.getStatus());
    }

    @Test
    void testTransitionStatusWithWrongStatus() {
        // Given
        testBatch.setStatus("DRAFT");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            orchestratorService.transitionStatus(1L, "READY", "PUBLISHED");
        });
    }

    @Test
    void testCancelDraftBatchKeepsHistoryAndReleasesCurrentWork() {
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);

        MrpCalcBatch result = orchestratorService.cancel(1L);

        assertEquals("CANCELLED", result.getStatus());
        verify(batchMapper).updateStatusForce(1L, "CANCELLED");
    }

    @Test
    void testPublishedBatchCannotBeCancelled() {
        testBatch.setStatus("PUBLISHED");
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);

        assertThrows(IllegalStateException.class, () -> orchestratorService.cancel(1L));
        verify(batchMapper, never()).updateStatusForce(anyLong(), eq("CANCELLED"));
    }
    @Test void rootsAreFactoryIsolatedAndChronologicalWithFrozenImportOrderWithinMonth() {
        LocalDate october = LocalDate.of(2026, 10, 1), november = october.plusMonths(1);
        List<ForecastDetail> rows = List.of(forecast(1L, "永惠", "B", november, "20"),
                forecast(2L, "爱培科", "A", october, "999"), forecast(3L, "永惠", "C", october, "30"),
                forecast(4L, "永惠", "B", october, "40"), forecast(5L, "永惠", "C", october, "5"));
        var roots = BatchOrchestratorService.rootDemandsForFactory(rows, "永惠");
        assertEquals(List.of("C", "B", "B"), roots.stream().map(root -> root.materialCode).toList());
        assertEquals(List.of(october, october, november), roots.stream().map(root -> root.sourceMonth).toList());
        assertEquals(new BigDecimal("35"), roots.getFirst().quantity);
    }

    @Test void rootsCarryFrozenForecastPriorityIntoAutomaticAllocationOrder() {
        LocalDate october = LocalDate.of(2026, 10, 1);
        ForecastDetail laterPriority = new ForecastDetail(10L, 1L, "永惠", "B", null, null, null,
                null, null, null, null, october, BigDecimal.TEN, 20);
        ForecastDetail earlierPriority = new ForecastDetail(11L, 1L, "永惠", "A", null, null, null,
                null, null, null, null, october, BigDecimal.TEN, 5);

        var roots = BatchOrchestratorService.rootDemandsForFactory(
                List.of(laterPriority, earlierPriority), "永惠");

        assertEquals(List.of("A", "B"), roots.stream().map(root -> root.materialCode).toList());
        assertEquals(List.of(5, 20), roots.stream().map(root -> root.prioritySeq).toList());
    }
    private ForecastDetail forecast(Long id, String factory, String material, LocalDate month, String qty) {
        return new ForecastDetail(id, 1L, factory, material, null, null, null, null, null, null, null, month, new BigDecimal(qty));
    }

    @Test void publishRechecksBadgesInsideWorkerBeforeCopyingFacts() {
        testBatch.setStatus("READY"); testBatch.setActiveRunId(1L);
        when(batchMapper.selectForUpdate(1L)).thenReturn(testBatch);
        doThrow(new IllegalStateException("库存风险待处理")).when(releaseValidation).requirePublishable(testBatch);
        assertTrue(assertThrows(IllegalStateException.class, () -> orchestratorService.publish(1L)).getMessage().contains("库存风险"));
        verify(batchMapper, never()).insert(any());
    }

}
