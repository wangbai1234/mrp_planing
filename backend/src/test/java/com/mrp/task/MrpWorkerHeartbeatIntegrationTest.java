package com.mrp.task;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.service.MrpCalculationService;
import com.mrp.mrp.service.MrpTaskWorkerService;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real scheduled methods and MySQL lease while calculation is blocked. */
class MrpWorkerHeartbeatIntegrationTest extends BaseIntegrationTest {
    @Autowired CalcTaskMapper tasks;
    @Autowired MrpTaskWorkerService worker;
    @MockBean MrpCalculationService calculation;
    @MockBean MrpCalcBatchMapper batches;

    @Test void scheduledHeartbeatRenewsOnlyOwnedRunningTasksDuringLongCalculation() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var batch = new MrpCalcBatch();
        batch.setId(9223372036854775806L);
        batch.setStatus("CALCULATING");
        when(batches.selectById(batch.getId())).thenReturn(batch);
        when(calculation.executeCalculation(eq(batch.getId()), eq(1L), eq(1L), anyList(), any()))
                .thenAnswer(invocation -> {
                    entered.countDown();
                    if (!release.await(55, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test did not release calculation");
                    }
                    throw new IllegalStateException("Controlled heartbeat test failure");
                });
        var owned = task();
        var foreign = task();
        tasks.insert(foreign);
        assertEquals(1, tasks.claimTask(foreign.getId(), "another-worker", 5, 0));
        var foreignBefore = tasks.selectById(foreign.getId());
        tasks.insert(owned);
        try {
            assertTrue(entered.await(15, TimeUnit.SECONDS), "Scheduled worker must claim task");
            var before = tasks.selectById(owned.getId());
            long deadline = System.nanoTime() + Duration.ofSeconds(38).toNanos();
            var renewed = before;
            while (System.nanoTime() < deadline) {
                renewed = tasks.selectById(owned.getId());
                if (renewed.getLeaseUntil().isAfter(before.getLeaseUntil())) break;
                Thread.sleep(100);
            }
            assertTrue(renewed.getLeaseUntil().isAfter(before.getLeaseUntil()),
                    "Lease must advance while calculation still blocks the polling scheduler");
            assertTrue(renewed.getHeartbeatAt().isAfter(before.getHeartbeatAt()));
            assertEquals("RUNNING", renewed.getStatus());
            assertEquals(before.getVersion(), renewed.getVersion());
            assertEquals(1, renewed.getAttemptCount());
            assertEquals(before.getWorkerId(), renewed.getWorkerId());
            assertEquals(foreignBefore.getLeaseUntil(), tasks.selectById(foreign.getId()).getLeaseUntil());
            assertEquals(foreignBefore.getHeartbeatAt(), tasks.selectById(foreign.getId()).getHeartbeatAt());
        } finally {
            release.countDown();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline && "RUNNING".equals(tasks.selectById(owned.getId()).getStatus())) {
                Thread.sleep(50);
            }
            var failed = tasks.selectById(owned.getId());
            try {
                assertEquals("FAILED", failed.getStatus());
                worker.heartbeat();
                assertEquals(failed.getLeaseUntil(), tasks.selectById(owned.getId()).getLeaseUntil(),
                        "Terminal tasks must not renew");
                verify(calculation, times(1)).executeCalculation(eq(batch.getId()), eq(1L), eq(1L), anyList(), any());
            } finally {
                tasks.deleteById(owned.getId());
                tasks.deleteById(foreign.getId());
            }
        }
    }

    private CalcTask task() {
        var t = new CalcTask();
        t.setTaskType("MRP_CALCULATION"); t.setBusinessScope("mrp");
        t.setRequestKey("heartbeat-" + UUID.randomUUID()); t.setStatus("PENDING");
        t.setPriority(0); t.setAttemptCount(0); t.setMaxAttempts(3);
        t.setProgressCurrent(0); t.setProgressTotal(100);
        t.setInputPayload("{\"batchId\":9223372036854775806,\"bomSnapshotId\":1,\"inventorySnapshotId\":1}");
        t.setPhase("QUEUED"); t.setSuccessRoots(0); t.setFailedRoots(0);
        return t;
    }
}
