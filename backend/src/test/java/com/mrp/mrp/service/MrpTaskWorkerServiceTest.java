package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpCalcRun;
import com.mrp.mrp.domain.MrpDecisionIssue;
import com.mrp.mrp.domain.MrpUnallocatedQuantity;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDecisionIssueMapper;
import com.mrp.mrp.repository.MrpUnallocatedQuantityMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MrpTaskWorkerServiceTest {

    @Mock
    private CalcTaskMapper calcTaskMapper;
    @Mock
    private MrpCalcBatchMapper batchMapper;
    @Mock
    private MrpCalculationService calculationService;
    @Mock
    private MrpDecisionIssueMapper issueMapper;
    @Mock
    private MrpUnallocatedQuantityMapper unallocatedMapper;
    @Mock
    private DailyPlanningService dailyPlanningService;
    @Mock
    private ObjectMapper objectMapper;

    @Mock private MaterialPolicyNoticeService policyNotices;
    @Mock private TaskProgressService taskProgressService;
    @Mock private MaterialTaskLeaseRecoveryService leaseRecovery;

    @InjectMocks
    private MrpTaskWorkerService taskWorkerService;

    private MrpCalcBatch testBatch;
    private CalcTask testTask;

    @BeforeEach
    void setUp() {
        lenient().when(taskProgressService.listener(anyLong(), anyString(), anyInt()))
                .thenReturn(com.mrp.mrp.engine.CalculationProgressListener.NONE);
        lenient().when(batchMapper.updateStatusForce(anyLong(), anyString())).thenReturn(1);
        lenient().when(batchMapper.activateRunAndStatus(anyLong(), anyLong(), anyString())).thenReturn(1);
        testBatch = new MrpCalcBatch();
        testBatch.setId(1L);
        testBatch.setStatus("CALCULATING");
        testBatch.setFactoryCode("F001");
        testBatch.setForecastVersionId(1L);
        testBatch.setInventorySnapshotId(1L);
        testBatch.setBomSnapshotId(1L);
        testBatch.setCategoryRoleVersionId(1L);
        testBatch.setMaterialPolicyVersionId(1L);
        testBatch.setProductionCalendarVersionId(1L);
        testBatch.setInputChecksum("test-checksum");
        testBatch.setVersion(0);

        testTask = new CalcTask();
        testTask.setId(1L);
        testTask.setTaskType("MRP_CALCULATION");
        testTask.setStatus(CalcTask.STATUS_PENDING);
        testTask.setVersion(0);
        testTask.setInputPayload("{\"batchId\":1,\"bomSnapshotId\":1,\"inventorySnapshotId\":1}");
        testTask.setInputChecksum("test-checksum");
    }

    @Test
    void testReadyGateWithOpenIssues() throws Exception {
        // Given
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenReturn(testTask);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("batchId", 1);
                    put("bomSnapshotId", 1);
                    put("inventorySnapshotId", 1);
                }});

        MrpCalcRun completedRun = new MrpCalcRun();
        completedRun.setId(1L);
        completedRun.setStatus("COMPLETED");
        when(calculationService.executeCalculation(eq(1L), eq(1L), eq(1L), any(), any())).thenReturn(completedRun);

        // Mock open issues
        MrpDecisionIssue openIssue = new MrpDecisionIssue();
        openIssue.setSeverity("BLOCKING");
        openIssue.setStatus("OPEN");
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(List.of(openIssue));

        // When
        taskWorkerService.pollAndExecute();

        // Then - should set WAITING_DECISION, not READY
        verify(batchMapper).activateRunAndStatus(1L, 1L, "WAITING_DECISION");
        verify(batchMapper, never()).activateRunAndStatus(1L, 1L, "READY");
    }

    @Test
    void testCalculationIssuesActivateCandidateRunForDecisionTraceability() throws Exception {
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenReturn(testTask);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("batchId", 1);
                    put("bomSnapshotId", 1);
                    put("inventorySnapshotId", 1);
                }});
        MrpCalcRun completedRun = new MrpCalcRun();
        completedRun.setId(7L);
        completedRun.setStatus("COMPLETED_WITH_ISSUES");
        when(calculationService.executeCalculation(eq(1L), eq(1L), eq(1L), any(), any())).thenReturn(completedRun);

        taskWorkerService.pollAndExecute();

        verify(batchMapper).activateRunAndStatus(1L, 7L, "WAITING_DECISION");
        verify(dailyPlanningService, never()).distribute(anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void testReadyGateWithPendingUnallocated() throws Exception {
        // Given
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenReturn(testTask);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("batchId", 1);
                    put("bomSnapshotId", 1);
                    put("inventorySnapshotId", 1);
                }});

        MrpCalcRun completedRun = new MrpCalcRun();
        completedRun.setId(1L);
        completedRun.setStatus("COMPLETED");
        when(calculationService.executeCalculation(eq(1L), eq(1L), eq(1L), any(), any())).thenReturn(completedRun);

        // Mock no open issues
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // Mock pending unallocated
        MrpUnallocatedQuantity pendingUnallocated = new MrpUnallocatedQuantity();
        pendingUnallocated.setStatus("PENDING");
        when(unallocatedMapper.selectByRunIdAndStatus(1L, "PENDING")).thenReturn(List.of(pendingUnallocated));

        // When
        taskWorkerService.pollAndExecute();

        // Then - should set WAITING_DECISION, not READY
        verify(batchMapper).activateRunAndStatus(1L, 1L, "WAITING_DECISION");
        verify(batchMapper, never()).activateRunAndStatus(1L, 1L, "READY");
    }

    @Test
    void testReadyGateWithConservationMismatch() throws Exception {
        // Given
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenReturn(testTask);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("batchId", 1);
                    put("bomSnapshotId", 1);
                    put("inventorySnapshotId", 1);
                }});

        MrpCalcRun completedRun = new MrpCalcRun();
        completedRun.setId(1L);
        completedRun.setStatus("COMPLETED");
        when(calculationService.executeCalculation(eq(1L), eq(1L), eq(1L), any(), any())).thenReturn(completedRun);

        // Mock no open issues
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // Mock no pending unallocated
        when(unallocatedMapper.selectByRunIdAndStatus(1L, "PENDING")).thenReturn(Collections.emptyList());

        // Mock conservation mismatch
        DailyPlanningService.ConservationCheckResult conservationResult =
                new DailyPlanningService.ConservationCheckResult(1, 1, List.of(
                        new DailyPlanningService.ConservationMismatch("key1", BigDecimal.valueOf(100), BigDecimal.valueOf(80))));
        when(dailyPlanningService.getSourceMonthConservation(1L, 1L)).thenReturn(conservationResult);

        // When
        taskWorkerService.pollAndExecute();

        // Then - should set WAITING_DECISION, not READY
        verify(batchMapper).activateRunAndStatus(1L, 1L, "WAITING_DECISION");
        verify(batchMapper, never()).activateRunAndStatus(1L, 1L, "READY");
    }

    @Test
    void testReadyGatePassesWithAllInvariantsSatisfied() throws Exception {
        // Given
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenReturn(testTask);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("batchId", 1);
                    put("bomSnapshotId", 1);
                    put("inventorySnapshotId", 1);
                }});

        MrpCalcRun completedRun = new MrpCalcRun();
        completedRun.setId(1L);
        completedRun.setStatus("COMPLETED");
        when(calculationService.executeCalculation(eq(1L), eq(1L), eq(1L), any(), any())).thenReturn(completedRun);

        // Mock no open issues
        when(issueMapper.selectByBatchIdAndStatus(1L, "OPEN")).thenReturn(Collections.emptyList());

        // Mock no pending unallocated
        when(unallocatedMapper.selectByRunIdAndStatus(1L, "PENDING")).thenReturn(Collections.emptyList());

        // Mock no conservation mismatch
        DailyPlanningService.ConservationCheckResult conservationResult =
                new DailyPlanningService.ConservationCheckResult(1, 0, Collections.emptyList());
        when(dailyPlanningService.getSourceMonthConservation(1L, 1L)).thenReturn(conservationResult);

        // When
        taskWorkerService.pollAndExecute();

        // Then - should set READY
        verify(batchMapper).activateRunAndStatus(1L, 1L, "READY");
        verify(calcTaskMapper).updateProgress(1L, 100, 100, "COMPLETED", 0, 0);
        verify(calcTaskMapper).updateResult(1L, CalcTask.STATUS_SUCCEEDED, 1L, 0);
    }

    @Test
    void testFailureCleanupDoesNotDeleteActiveRun() throws Exception {
        when(calcTaskMapper.failOwnedTask(eq(1L), anyString(), eq(1), anyString())).thenReturn(1);
        // Given
        testTask.setInputPayload("{\"batchId\":1,\"bomSnapshotId\":1,\"inventorySnapshotId\":1,\"runId\":1}");
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenReturn(testTask);
        when(batchMapper.selectById(1L)).thenReturn(testBatch);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(
                new java.util.LinkedHashMap<String, Object>() {{
                    put("batchId", 1);
                    put("bomSnapshotId", 1);
                    put("inventorySnapshotId", 1);
                    put("runId", 1);
                }});

        // Set active run to same as task run
        testBatch.setActiveRunId(1L);

        // Make calculation fail
        when(calculationService.executeCalculation(eq(1L), eq(1L), eq(1L), any(), any()))
                .thenThrow(new RuntimeException("Calculation failed"));

        // When
        taskWorkerService.pollAndExecute();

        // Preserve the active data and request a guarded reset, never a forced state change.
        verify(policyNotices, never()).deleteRun(any());
        verify(batchMapper).resetAfterCalculationFailure(1L, 0, 1L);
        verify(batchMapper, never()).updateStatusForce(1L, "READY_TO_CALCULATE");
    }
    @Test
    void staleClaimFailureCannotCleanUpOrChangeBatch() throws Exception {
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(0))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenThrow(new IllegalStateException("Reload failed"));
        when(calcTaskMapper.failOwnedTask(eq(1L), anyString(), eq(1), eq("Reload failed"))).thenReturn(0);
        taskWorkerService.pollAndExecute();
        verify(calcTaskMapper).failOwnedTask(eq(1L), anyString(), eq(1), eq("Reload failed"));
        verifyNoInteractions(batchMapper, policyNotices);
        verify(calcTaskMapper, times(1)).selectById(1L);
    }

    @Test
    void failureUsesCurrentClaimVersionEvenForNonzeroPendingVersion() throws Exception {
        testTask.setVersion(7);
        when(calcTaskMapper.selectPending()).thenReturn(List.of(testTask));
        when(calcTaskMapper.claimTask(eq(1L), anyString(), eq(5), eq(7))).thenReturn(1);
        when(calcTaskMapper.selectById(1L)).thenThrow(new IllegalStateException("Reload failed"));
        taskWorkerService.pollAndExecute();
        verify(calcTaskMapper).failOwnedTask(eq(1L), anyString(), eq(8), eq("Reload failed"));
        verify(calcTaskMapper, never()).updateFailure(anyLong(), anyString(), anyString(), anyString(), anyInt());
        verifyNoInteractions(batchMapper, policyNotices);
    }

}
