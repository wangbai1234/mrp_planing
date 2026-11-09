package com.mrp.mrp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** A dedicated scheduler keeps expensive publishing off HTTP and calculation polling threads. */
@Service
public class MrpLifecycleWorkerService {
    private static final Logger log = LoggerFactory.getLogger(MrpLifecycleWorkerService.class);
    private static final int LEASE_MINUTES = 5;
    private final CalcTaskMapper tasks;
    private final MrpCalcBatchMapper batches;
    private final BatchOrchestratorService orchestrator;
    private final TaskProgressService progress;
    private final ObjectMapper json;
    private final TransactionTemplate businessTransaction;
    private final String workerId = "mrp-lifecycle-" + ProcessHandle.current().pid() + "-" + java.util.UUID.randomUUID();

    public MrpLifecycleWorkerService(CalcTaskMapper tasks, MrpCalcBatchMapper batches,
            BatchOrchestratorService orchestrator, TaskProgressService progress, ObjectMapper json,
            @Qualifier("transactionManager") PlatformTransactionManager manager) {
        this.tasks = tasks; this.batches = batches; this.orchestrator = orchestrator;
        this.progress = progress; this.json = json;
        this.businessTransaction = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 5000, scheduler = "mrpLifecycleScheduler")
    public void poll() {
        try {
            List<CalcTask> pending = tasks.selectPendingLifecycle();
            for (CalcTask task : pending) {
                if (run(task)) break;
            }
        } catch (Exception error) { log.error("MRP lifecycle polling failed", error); }
    }

    @Scheduled(fixedDelay = 30000, scheduler = "mrpHeartbeatScheduler")
    public void heartbeat() {
        try { tasks.heartbeatForWorker(workerId, LEASE_MINUTES); }
        catch (Exception error) { log.error("MRP lifecycle heartbeat failed", error); }
    }

    boolean run(CalcTask pending) {
        if (tasks.claimTask(pending.getId(), workerId, LEASE_MINUTES, pending.getVersion()) != 1) return false;
        int claimVersion = pending.getVersion() + 1;
        try {
            CalcTask claimed = tasks.selectById(pending.getId());
            JsonNode payload = json.readTree(claimed.getInputPayload());
            long batchId = payload.path("batchId").asLong();
            String fingerprint = payload.path("capacityFingerprint").asText("");
            MrpCalcBatch source = batches.selectById(batchId);
            if (source == null || claimed.getInputChecksum() != null
                    && !claimed.getInputChecksum().equals(source.getInputChecksum()))
                throw new IllegalStateException("排产输入版本已变化，请重新发起任务");
            var report = progress.listener(claimed.getId(), workerId, claimVersion);
            report.report(5, MrpLifecycleTaskService.PUBLISH.equals(claimed.getTaskType()) ? "校验发布条件" : "准备修订输入");
            businessTransaction.executeWithoutResult(status -> {
                MrpCalcBatch result;
                if (MrpLifecycleTaskService.PUBLISH.equals(claimed.getTaskType())) {
                    result = orchestrator.publish(batchId, fingerprint.isEmpty() ? null : fingerprint, claimed.getCreatedBy(), report);
                } else if (MrpLifecycleTaskService.REVISION.equals(claimed.getTaskType())) {
                    result = orchestrator.revise(batchId, claimed.getCreatedBy(), report);
                } else throw new IllegalStateException("未知任务类型: " + claimed.getTaskType());
                // Commit the result and terminal task state together. A lost lease rolls
                // back the complete business copy instead of leaving an orphan result.
                if (tasks.finishOwnedLifecycle(claimed.getId(), workerId, claimVersion, result.getId()) != 1)
                    throw new IllegalStateException("任务所有权已变化，发布或修订结果已回滚");
            });
            return true;
        } catch (Exception error) {
            log.error("MRP lifecycle task {} failed", pending.getId(), error);
            tasks.failOwnedTask(pending.getId(), workerId, claimVersion,
                    error.getMessage() == null ? "任务执行失败" : error.getMessage().substring(0, Math.min(500, error.getMessage().length())));
            return false;
        }
    }
}
