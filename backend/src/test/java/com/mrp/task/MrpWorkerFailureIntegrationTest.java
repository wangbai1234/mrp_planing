package com.mrp.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.repository.*;
import com.mrp.mrp.service.*;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL claim/version transition, with calculation deliberately failing before any result write. */
class MrpWorkerFailureIntegrationTest extends BaseIntegrationTest {
    @MockBean MrpTaskWorkerService scheduledWorker;
    @Autowired CalcTaskMapper tasks;
    @Autowired TaskProgressService taskProgressService;

    @Test void claimedCalculationFailureReachesTerminalStateInsteadOfRemainingRunning() {
        CalcTask task = task();
        tasks.insert(task);
        try {
            var batches = mock(MrpCalcBatchMapper.class); // batch missing: deterministic validation failure
            var worker = new MrpTaskWorkerService(tasks, batches, mock(MrpCalculationService.class),
                    mock(MrpRequirementDetailMapper.class), mock(MrpTerminalDemandMapper.class),
                    mock(MrpDailyPlanDetailMapper.class), mock(MrpUnallocatedQuantityMapper.class),
                    mock(MrpDecisionIssueMapper.class), mock(DailyPlanningService.class),
                    new ObjectMapper(), mock(MaterialPolicyNoticeService.class), taskProgressService,
                    mock(MrpWorkbenchReviewService.class), mock(MaterialAnalysisStageService.class),
                    mock(MaterialTaskLeaseRecoveryService.class));
            worker.pollAndExecute();
            CalcTask failed = tasks.selectById(task.getId());
            assertEquals("FAILED", failed.getStatus());
            assertEquals(2, failed.getVersion());
            assertEquals(1, failed.getAttemptCount());
            assertEquals("CALC_ERROR", failed.getErrorCode());
            assertNotNull(failed.getFinishedAt());
            assertNotNull(failed.getWorkerId());
            assertEquals(0, tasks.heartbeat(task.getId(), failed.getWorkerId(), 5));
        } finally { tasks.deleteById(task.getId()); }
    }


    @Test void failureRequiresExactOwnerVersionAndRunningState() {
        CalcTask task = task();tasks.insert(task);
        try {
            long id=task.getId();
            assertEquals(1,tasks.claimTask(id,"worker-a",5,0));
            assertEquals(0,tasks.failOwnedTask(id,"worker-b",1,"wrong owner"));
            assertEquals(0,tasks.failOwnedTask(id,"worker-a",0,"stale version"));
            assertEquals("RUNNING",tasks.selectById(id).getStatus());
            assertEquals(1,tasks.failOwnedTask(id,"worker-a",1,"expected failure"));
            assertEquals(0,tasks.failOwnedTask(id,"worker-a",2,"terminal overwrite"));
            assertEquals("expected failure",tasks.selectById(id).getErrorMessage());
            assertEquals(2,tasks.selectById(id).getVersion());
        } finally {tasks.deleteById(task.getId());}
    }

    @Test void failureCannotReplaceSuccessfulResult() {
        CalcTask task = task();tasks.insert(task);
        try {
            long id=task.getId();
            assertEquals(1,tasks.claimTask(id,"worker-a",5,0));
            assertEquals(1,tasks.updateResult(id,"SUCCEEDED",77L,1));
            assertEquals(0,tasks.failOwnedTask(id,"worker-a",2,"late exception"));
            assertEquals("SUCCEEDED",tasks.selectById(id).getStatus());
            assertEquals(77L,tasks.selectById(id).getResultResourceId());
        } finally {tasks.deleteById(task.getId());}
    }

    private CalcTask task() {
        var t = new CalcTask(); t.setTaskType("MRP_CALCULATION"); t.setBusinessScope("mrp");
        t.setRequestKey("worker-failure-" + java.util.UUID.randomUUID());t.setStatus("PENDING");
        t.setPriority(0); t.setAttemptCount(0);t.setMaxAttempts(3);t.setProgressCurrent(0);t.setProgressTotal(100);
        t.setInputPayload("{\"batchId\":9223372036854775806,\"bomSnapshotId\":1,\"inventorySnapshotId\":1}");
        t.setPhase("QUEUED");t.setSuccessRoots(0);t.setFailedRoots(0);return t;
    }
}
