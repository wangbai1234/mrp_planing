package com.mrp.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.repository.*;
import com.mrp.mrp.service.*;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real task claims and batch SQL; failures cannot overwrite already valid results. */
class MrpWorkerBatchStateIntegrationTest extends BaseIntegrationTest {
    @MockBean MrpTaskWorkerService scheduledWorker;
    @Autowired CalcTaskMapper tasks;
    @Autowired TaskProgressService taskProgressService;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;

    @Test void failedTaskPreservesValidAndNonCalculationBatchStatesWithOrWithoutRunId() {
        for (String status : List.of("READY", "PUBLISHED", "WAITING_DECISION", "STALE", "CANCELLED",
                "SUPERSEDED", "DRAFT", "CHECKING", "ANALYZING", "REANALYZING", "READY_TO_CALCULATE")) {
            for (boolean payloadHasActiveRun : List.of(false, true)) {
                long batch = batch(status, 77L);
                var before = jdbc.queryForMap("SELECT * FROM mrp_calc_batch WHERE id=?", batch);
                var task = task(batch, payloadHasActiveRun ? 77L : null);
                tasks.insert(task);
                try {
                    worker().pollAndExecute();
                    assertEquals("FAILED", tasks.selectById(task.getId()).getStatus());
                    assertEquals(before, jdbc.queryForMap("SELECT * FROM mrp_calc_batch WHERE id=?", batch),
                            status + " must be preserved; payloadHasActiveRun=" + payloadHasActiveRun);
                } finally { cleanup(task.getId(), batch); }
            }
        }
    }

    @Test void calculationFailureCanResetOnlyItsUnchangedBatchAndPreservesActiveRun() {
        for (String status : List.of("CALCULATING", "WAITING_DAILY_DISTRIBUTION", "WRITING", "FAILED")) {
            for (Long active : new Long[]{null, 77L}) {
                long batch = batch(status, active);
                var task = task(batch, active);
                tasks.insert(task);
                try {
                    worker().pollAndExecute();
                    assertEquals("FAILED", tasks.selectById(task.getId()).getStatus());
                    var current = batches.selectById(batch);
                    assertEquals("READY_TO_CALCULATE", current.getStatus());
                    assertEquals(1, current.getVersion());
                    assertEquals(active, current.getActiveRunId());
                } finally { cleanup(task.getId(), batch); }
            }
        }
    }

    @Test void batchResetRejectsStaleVersionOrChangedActiveRunIncludingNullTransitions() {
        long batch = batch("CALCULATING", null);
        try {
            var observed = batches.selectById(batch);
            batches.updateStatusForce(batch, "CALCULATING");
            assertEquals(0, batches.resetAfterCalculationFailure(batch, observed.getVersion(), null));
            var newer = batches.selectById(batch);
            jdbc.update("UPDATE mrp_calc_batch SET active_run_id=77 WHERE id=?", batch);
            assertEquals(0, batches.resetAfterCalculationFailure(batch, newer.getVersion(), null));
            assertEquals(0, batches.resetAfterCalculationFailure(batch, newer.getVersion(), 78L));
            jdbc.update("UPDATE mrp_calc_batch SET active_run_id=NULL WHERE id=?", batch);
            assertEquals(0, batches.resetAfterCalculationFailure(batch, newer.getVersion(), 77L));
            assertEquals("CALCULATING", batches.selectById(batch).getStatus());
            assertEquals(newer.getVersion(), batches.selectById(batch).getVersion());
            assertEquals(1, batches.resetAfterCalculationFailure(batch, newer.getVersion(), null));
            assertEquals(0, batches.resetAfterCalculationFailure(batch, newer.getVersion(), null));
        } finally { cleanup(null, batch); }
    }

    @Test void batchResetRejectsProtectedStatusEvenWhenVersionAndActiveRunMatch() {
        long batch = batch("PUBLISHED", 77L);
        try {
            assertEquals(0, batches.resetAfterCalculationFailure(batch, 0, 77L));
            assertEquals("PUBLISHED", batches.selectById(batch).getStatus());
            assertEquals(0, batches.selectById(batch).getVersion());
        } finally { cleanup(null, batch); }
    }

    private MrpTaskWorkerService worker() {
        var calculation = mock(MrpCalculationService.class);
        when(calculation.executeCalculation(anyLong(), anyLong(), anyLong(), anyList(), any()))
                .thenThrow(new IllegalStateException("Controlled batch-state failure"));
        return new MrpTaskWorkerService(tasks, batches, calculation,
                mock(MrpRequirementDetailMapper.class), mock(MrpTerminalDemandMapper.class),
                mock(MrpDailyPlanDetailMapper.class), mock(MrpUnallocatedQuantityMapper.class),
                mock(MrpDecisionIssueMapper.class), mock(DailyPlanningService.class),
                new ObjectMapper(), mock(MaterialPolicyNoticeService.class), taskProgressService,
                mock(MrpWorkbenchReviewService.class), mock(MaterialAnalysisStageService.class),
                mock(MaterialTaskLeaseRecoveryService.class));
    }

    private long batch(String status, Long active) {
        String key = "batch-state-" + UUID.randomUUID();
        jdbc.update("INSERT INTO mrp_calc_batch(factory_code,status,version_no,active_run_id,active_request_key) VALUES('永惠',?,1,?,?)",
                status, active, key);
        return jdbc.queryForObject("SELECT id FROM mrp_calc_batch WHERE active_request_key=?", Long.class, key);
    }

    private CalcTask task(long batch, Long run) {
        var t = new CalcTask();
        t.setTaskType("MRP_CALCULATION"); t.setBusinessScope("mrp");
        t.setRequestKey("batch-state-" + UUID.randomUUID()); t.setStatus("PENDING");
        t.setPriority(0); t.setAttemptCount(0); t.setMaxAttempts(3);
        t.setProgressCurrent(0); t.setProgressTotal(100);
        t.setInputPayload("{\"batchId\":" + batch + ",\"bomSnapshotId\":1,\"inventorySnapshotId\":1"
                + (run == null ? "" : ",\"runId\":" + run) + "}");
        t.setPhase("QUEUED"); t.setSuccessRoots(0); t.setFailedRoots(0);
        return t;
    }

    private void cleanup(Long task, long batch) {
        if (task != null) tasks.deleteById(task);
        jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?", batch);
    }
}
