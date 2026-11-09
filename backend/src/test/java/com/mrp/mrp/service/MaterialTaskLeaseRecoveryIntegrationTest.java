package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.repository.CalcTaskMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Separate committed transactions model a worker crash and subsequent scanner poll. */
class MaterialTaskLeaseRecoveryIntegrationTest extends BaseIntegrationTest {
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired CalcTaskMapper tasks;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired MaterialTaskLeaseRecoveryService recovery;
    @MockBean MrpTaskWorkerService scheduledWorker;

    @Test void expiredMaterialClaimRetriesThenRestoresTheSameBatchAndRun() {
        long batch = batch();
        long run = run(batch);
        long task = task(batch, run, true);
        try {
            for (int attempt = 1; attempt <= 3; attempt++) {
                var pending = tasks.selectById(task);
                assertEquals(1, tasks.claimTask(task, "crashed-worker-" + attempt, 5, pending.getVersion()));
                jdbc.update("UPDATE calc_task SET lease_until=DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE id=?", task);
                recovery.recoverExpired();
                var after = tasks.selectById(task);
                if (attempt < 3) {
                    assertEquals("PENDING", after.getStatus());
                    assertNull(after.getWorkerId());
                    assertEquals("CALCULATING", batches.selectById(batch).getStatus());
                    assertEquals(0, tasks.heartbeat(task, "crashed-worker-" + attempt, 5));
                } else {
                    assertEquals("FAILED", after.getStatus());
                    assertEquals("LEASE_EXPIRED", after.getErrorCode());
                    assertEquals("MATERIAL_RECALC_REQUIRED", batches.selectById(batch).getStatus());
                    assertEquals(run, batches.selectById(batch).getActiveRunId());
                }
            }
        } finally { cleanup(task, run, batch); }
    }

    @Test void initialMaterialAnalysisRestoresScheduleReadyAndStaleClaimCannotChangeIt() {
        long batch = batch();
        long run = run(batch);
        long task = task(batch, run, false);
        try {
            jdbc.update("UPDATE calc_task SET status='RUNNING', worker_id='crashed', attempt_count=3,"
                    + " lease_until=DATE_SUB(NOW(), INTERVAL 1 MINUTE), version=1 WHERE id=?", task);
            var expired = tasks.selectById(task);
            recovery.recoverExpired();
            assertEquals(0, tasks.failExpired(task, expired.getVersion()));
            assertEquals("FAILED", tasks.selectById(task).getStatus());
            assertEquals("SCHEDULE_READY", batches.selectById(batch).getStatus());
            assertEquals(run, batches.selectById(batch).getActiveRunId());
        } finally { cleanup(task, run, batch); }
    }

    private long batch() {
        String key = "lease-batch-" + UUID.randomUUID();
        jdbc.update("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,active_request_key)"
                + " VALUES('爱培科','CALCULATING',1,'v5-shifted-business-weeks',?)", key);
        return jdbc.queryForObject("SELECT id FROM mrp_calc_batch WHERE active_request_key=?", Long.class, key);
    }

    private long run(long batch) {
        jdbc.update("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'SCHEDULE_READY')", batch);
        long run = jdbc.queryForObject("SELECT id FROM mrp_calc_run WHERE batch_id=? ORDER BY id DESC LIMIT 1", Long.class, batch);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?", run, batch);
        return run;
    }

    private long task(long batch, long run, boolean recalculation) {
        String key = "lease-task-" + UUID.randomUUID();
        jdbc.update("INSERT INTO calc_task(task_type,business_scope,request_key,status,attempt_count,max_attempts,input_payload)"
                + " VALUES('MRP_MATERIAL_ANALYSIS','mrp',?,'PENDING',0,3,?)", key,
                "{\"batchId\":" + batch + ",\"runId\":" + run
                + ",\"materialRecalculation\":" + recalculation + "}");
        return tasks.selectByRequestKey(key).getId();
    }

    private void cleanup(long task, long run, long batch) {
        tasks.deleteById(task);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=NULL WHERE id=?", batch);
        jdbc.update("DELETE FROM mrp_calc_run WHERE id=?", run);
        jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?", batch);
    }
}
