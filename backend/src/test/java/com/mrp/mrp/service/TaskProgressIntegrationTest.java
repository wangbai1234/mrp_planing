package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.task.repository.CalcTaskMapper;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class TaskProgressIntegrationTest extends BaseIntegrationTest {
    @MockBean MrpTaskWorkerService worker;
    @Autowired TaskProgressService service;
    @Autowired CalcTaskMapper tasks;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired @Qualifier("transactionManager") PlatformTransactionManager manager;

    private long task(String key) {
        jdbc.update("INSERT INTO calc_task(task_type,business_scope,request_key,status,worker_id,version,progress_current,progress_total,input_payload) VALUES('MRP_CALCULATION','PROGRESS-TEST',?,'RUNNING','test-worker',1,0,100,'{}')", key);
        return jdbc.queryForObject("SELECT id FROM calc_task WHERE request_key=?", Long.class, key);
    }

    @Test void progressIsVisibleBeforeOuterCommitAndSurvivesCandidateRollback() {
        long id = task("mrp-calc-900001-progress");
        var progress = service.listener(id, "test-worker", 1);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            progress.report(47, "保存物料需求", 1000, 3000);
            var observed = CompletableFuture.supplyAsync(() -> tasks.selectLatestCalculationForBatch(900001L)).join();
            assertEquals(47, observed.getProgressCurrent());
            assertEquals("保存物料需求（1000/3000）", observed.getPhase());
            status.setRollbackOnly();
        });
        assertEquals(47, tasks.selectById(id).getProgressCurrent());
        assertEquals(1, tasks.selectById(id).getVersion());
    }

    @Test void staleWorkersAndCompletedTasksCannotOverwriteProgress() {
        long id = task("mrp-calc-900002-progress");
        assertEquals(0, tasks.updateOwnedProgress(id, "old-worker", 1, 80, "错误"));
        assertEquals(0, tasks.updateOwnedProgress(id, "test-worker", 0, 80, "错误"));
        assertEquals(1, tasks.updateOwnedProgress(id, "test-worker", 1, 45, "展开"));
        assertEquals(1, tasks.updateOwnedProgress(id, "test-worker", 1, 20, "展开"));
        assertEquals(45, tasks.selectById(id).getProgressCurrent());
        jdbc.update("UPDATE calc_task SET status='SUCCEEDED' WHERE id=?", id);
        assertEquals(0, tasks.updateOwnedProgress(id, "test-worker", 1, 90, "错误"));
    }

    @Test void latestProgressQueryDoesNotSortLargeJsonPayloads() {
        for (int i = 0; i < 6; i++) {
            long id = task("mrp-calc-900003-" + i);
            jdbc.update("UPDATE calc_task SET input_payload=?,created_at='2026-10-19 12:00:00' WHERE id=?", "{\"data\":\"" + "x".repeat(400_000) + "\"}", id);
        }
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            jdbc.execute("SET SESSION sort_buffer_size=32768");
            try {
                var latest = tasks.selectLatestCalculationForBatch(900003L);
                assertNotNull(latest);
                assertEquals(jdbc.queryForObject("SELECT MAX(id) FROM calc_task WHERE request_key LIKE 'mrp-calc-900003-%'", Long.class), latest.getId());
                assertNull(latest.getInputPayload());
            } finally {
                jdbc.execute("SET SESSION sort_buffer_size=262144");
            }
        });
    }
}
