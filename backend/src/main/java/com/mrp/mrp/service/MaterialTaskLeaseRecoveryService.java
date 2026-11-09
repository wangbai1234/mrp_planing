package com.mrp.mrp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Recovers expired calculation claims without changing a newer worker's task. */
@Service
public class MaterialTaskLeaseRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(MaterialTaskLeaseRecoveryService.class);
    private final CalcTaskMapper tasks;
    private final MrpCalcBatchMapper batches;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    public MaterialTaskLeaseRecoveryService(CalcTaskMapper tasks, MrpCalcBatchMapper batches, ObjectMapper json,
            PlatformTransactionManager manager) {
        this.tasks = tasks;
        this.batches = batches;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
    }

    public void recoverExpired() {
        List<CalcTask> expired = tasks.selectExpiredRunning();
        for (CalcTask task : expired) {
            try { transaction.executeWithoutResult(status -> recoverOne(task)); }
            catch (Exception error) { log.error("Failed to recover expired task {}", task.getId(), error); }
        }
    }

    public void recoverOne(CalcTask task) {
        if (task.getAttemptCount() == null || task.getMaxAttempts() == null)
            throw new IllegalStateException("Task attempt limits missing: " + task.getId());
        if (task.getAttemptCount() < task.getMaxAttempts()) {
            if (tasks.requeueExpired(task.getId(), task.getVersion()) == 1)
                log.warn("Requeued expired task {} after attempt {}", task.getId(), task.getAttemptCount());
            return;
        }
        if (tasks.failExpired(task.getId(), task.getVersion()) != 1) return;
        if (!"MRP_MATERIAL_ANALYSIS".equals(task.getTaskType())) return;
        try {
            Map<String, Object> payload = json.readValue(task.getInputPayload(), new TypeReference<>() {});
            Long batchId = number(payload.get("batchId"));
            Long runId = number(payload.get("runId"));
            if (batchId == null || runId == null) throw new IllegalStateException("Missing material task identifiers");
            String target = Boolean.TRUE.equals(payload.get("materialRecalculation"))
                    ? "MATERIAL_RECALC_REQUIRED" : "SCHEDULE_READY";
            int restored = batches.restoreExpiredMaterialTask(batchId, runId, target);
            if (restored == 0) {
                MrpCalcBatch current = batches.selectById(batchId);
                log.warn("Expired material task {} left batch {} unchanged at {}", task.getId(), batchId,
                        current == null ? "MISSING" : current.getStatus());
            }
        } catch (Exception error) {
            // Roll back task failure as well: an unparseable payload needs operator repair.
            throw new IllegalStateException("Cannot recover material task " + task.getId(), error);
        }
    }

    private static Long number(Object value) {
        if (value == null) return null;
        return Long.valueOf(value.toString());
    }
}
