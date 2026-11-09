package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpCalcRun;
import com.mrp.mrp.domain.MrpDecisionIssue;
import com.mrp.mrp.domain.MrpUnallocatedQuantity;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.mrp.engine.CalculationProgressListener;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDecisionIssueMapper;
import com.mrp.mrp.repository.MrpRequirementDetailMapper;
import com.mrp.mrp.repository.MrpTerminalDemandMapper;
import com.mrp.mrp.repository.MrpDailyPlanDetailMapper;
import com.mrp.mrp.repository.MrpUnallocatedQuantityMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MrpTaskWorkerService {

    private static final Logger log = LoggerFactory.getLogger(MrpTaskWorkerService.class);

    private static final int LEASE_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 3;

    private final CalcTaskMapper calcTaskMapper;
    private final MrpCalcBatchMapper batchMapper;
    private final MrpWorkbenchReviewService reviews;
    private final MaterialAnalysisStageService materialAnalysis;
    private final MaterialTaskLeaseRecoveryService leaseRecovery;
    private final MrpCalculationService calculationService;
    private final MrpRequirementDetailMapper requirementDetailMapper;
    private final MrpTerminalDemandMapper terminalDemandMapper;
    private final MrpDailyPlanDetailMapper dailyPlanMapper;
    private final MrpUnallocatedQuantityMapper unallocatedMapper;
    private final MrpDecisionIssueMapper issueMapper;
    private final DailyPlanningService dailyPlanningService;
    private final MaterialPolicyNoticeService policyNotices;
    private final ObjectMapper objectMapper;
    private final TaskProgressService taskProgressService;
    private final String workerId;

    public MrpTaskWorkerService(
            CalcTaskMapper calcTaskMapper,
            MrpCalcBatchMapper batchMapper,
            MrpCalculationService calculationService,
            MrpRequirementDetailMapper requirementDetailMapper,
            MrpTerminalDemandMapper terminalDemandMapper,
            MrpDailyPlanDetailMapper dailyPlanMapper,
            MrpUnallocatedQuantityMapper unallocatedMapper,
            MrpDecisionIssueMapper issueMapper,
            DailyPlanningService dailyPlanningService,
            ObjectMapper objectMapper, MaterialPolicyNoticeService policyNotices,
            TaskProgressService taskProgressService, MrpWorkbenchReviewService reviews,
            MaterialAnalysisStageService materialAnalysis, MaterialTaskLeaseRecoveryService leaseRecovery) {
        this.reviews = reviews;
        this.materialAnalysis = materialAnalysis;
        this.leaseRecovery = leaseRecovery;
        this.policyNotices = policyNotices;
        this.taskProgressService = taskProgressService;
        this.calcTaskMapper = calcTaskMapper;
        this.batchMapper = batchMapper;
        this.calculationService = calculationService;
        this.requirementDetailMapper = requirementDetailMapper;
        this.terminalDemandMapper = terminalDemandMapper;
        this.dailyPlanMapper = dailyPlanMapper;
        this.unallocatedMapper = unallocatedMapper;
        this.issueMapper = issueMapper;
        this.dailyPlanningService = dailyPlanningService;
        this.objectMapper = objectMapper;
        this.workerId = generateWorkerId();
    }

    private String generateWorkerId() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            long pid = ProcessHandle.current().pid();
            return hostname + "-" + pid;
        } catch (Exception e) {
            return "worker-" + System.currentTimeMillis();
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void pollAndExecute() {
        try {
            leaseRecovery.recoverExpired();

            // Find a PENDING task
            List<CalcTask> pendingTasks = findPendingTasks();
            if (pendingTasks.isEmpty()) {
                return;
            }

            for (CalcTask task : pendingTasks) {
                if (tryClaimAndExecute(task)) {
                    break; // Process one task per poll cycle
                }
            }
        } catch (Exception e) {
            log.error("Error in task worker poll cycle", e);
        }
    }

    @Scheduled(fixedDelay = 30000, scheduler = "mrpHeartbeatScheduler")
    public void heartbeat() {
        try {
            int updated = calcTaskMapper.heartbeatForWorker(workerId, LEASE_MINUTES);
            if (updated > 0) {
                log.debug("Heartbeat sent for {} running tasks owned by worker {}", updated, workerId);
            }
        } catch (Exception e) {
            log.error("Error sending heartbeat for worker {}", workerId, e);
        }
    }

    private List<CalcTask> findPendingTasks() {
        // We'll add a selectPending method to the mapper
        // For now, we'll use a workaround by querying through the existing interface
        // This requires adding selectPending to CalcTaskMapper
        try {
            return calcTaskMapper.selectPending();
        } catch (Exception e) {
            log.debug("selectPending not available yet, skipping");
            return Collections.emptyList();
        }
    }

    private boolean tryClaimAndExecute(CalcTask task) {
        // CAS claim with lease: PENDING -> RUNNING with worker_id, lease_until, etc.
        int claimed = calcTaskMapper.claimTask(task.getId(), workerId, LEASE_MINUTES, task.getVersion());
        if (claimed == 0) {
            log.debug("Task {} already claimed by another worker", task.getId());
            return false;
        }

        log.info("Claimed task {} for execution by worker {}", task.getId(), workerId);

        // claimTask increments the CAS version. Keep this claim's token even if reload fails;
        // reloading in the catch block could accidentally adopt a newer worker's token.
        int claimedVersion = task.getVersion() + 1;

        try {
            // Reload to get updated version
            CalcTask claimedTask = calcTaskMapper.selectById(task.getId());

            // Parse input payload
            Map<String, Object> payload = objectMapper.readValue(
                    claimedTask.getInputPayload(), new TypeReference<Map<String, Object>>() {});

            Long batchId = toLong(payload.get("batchId"));
            Long bomSnapshotId = toLong(payload.get("bomSnapshotId"));
            Long inventorySnapshotId = toLong(payload.get("inventorySnapshotId"));
            boolean materialStage = "MRP_MATERIAL_ANALYSIS".equals(claimedTask.getTaskType());

            // Validate batch exists and is in correct state
            MrpCalcBatch batch = batchMapper.selectById(batchId);
            if (batch == null) {
                throw new IllegalStateException("Batch not found: " + batchId);
            }

            // Validate input checksum matches
            if (claimedTask.getInputChecksum() != null && batch.getInputChecksum() != null
                    && !claimedTask.getInputChecksum().equals(batch.getInputChecksum())) {
                throw new IllegalStateException("Input checksum mismatch: task=" +
                        claimedTask.getInputChecksum() + " batch=" + batch.getInputChecksum());
            }

            @SuppressWarnings("unchecked")
            List<NetRequirementEngine.RootDemand> rootDemands = objectMapper.convertValue(
                    payload.getOrDefault("rootDemands", Collections.emptyList()),
                    new TypeReference<List<NetRequirementEngine.RootDemand>>() {});
            if (rootDemands == null) {
                rootDemands = Collections.emptyList();
            }
            int rootCount = rootDemands.size();
            CalculationProgressListener progress = taskProgressService.listener(
                    task.getId(), workerId, claimedVersion);

            // Execute calculation
            if (materialStage) {
                Long expectedRun = toLong(payload.get("runId"));
                if (!reviews.matchesSchedule(batchId, expectedRun, (String) payload.get("scheduleFingerprint")))
                    throw new IllegalStateException("整机排产已变化，物料分析任务已失效");
            }
            MrpCalcRun run = materialStage
                    ? materialAnalysis.execute(batchId, toLong(payload.get("runId")),
                            (String) payload.get("scheduleFingerprint"), bomSnapshotId,
                            inventorySnapshotId, rootDemands, progress)
                    : calculationService.executeCalculation(batchId, bomSnapshotId, inventorySnapshotId, rootDemands, progress);

            // Store runId in task payload for failure cleanup
            Map<String, Object> resultPayload = new LinkedHashMap<>(payload);
            resultPayload.put("runId", run.getId());
            claimedTask.setInputPayload(objectMapper.writeValueAsString(resultPayload));
            calcTaskMapper.updateInputPayload(claimedTask.getId(), claimedTask.getInputPayload());
            if (materialStage) {
                completeTask(claimedTask,run.getId(),rootCount,0);
                return true;
            }

            // Check if calculation had blocking issues
            if ("COMPLETED_WITH_ISSUES".equals(run.getStatus()) && !materialStage) {
                activateRunOrThrow(batchId, run.getId(), "WAITING_DECISION");
                log.info("Batch {} has blocking issues; candidate run {} activated for traceability and decision handling",
                        batchId, run.getId());
                completeTask(claimedTask, run.getId(), rootCount, 0);
                return true;
            }

            // Only distribute if no blocking issues
            setStatusOrThrow(batchId, "WAITING_DAILY_DISTRIBUTION");

            Long calendarVersionId = batch.getProductionCalendarVersionId();
            Long materialPolicyVersionId = batch.getMaterialPolicyVersionId();

            if (calendarVersionId != null) {
                dailyPlanningService.distribute(batchId, run.getId(), calendarVersionId, materialPolicyVersionId, progress);
                log.info("Auto-distributed daily plans for batch {}", batchId);
            } else {
                log.warn("Batch {} has no production calendar version, skipping distribution", batchId);
            }

            progress.report(94, "校验异常与待办");
            // P0-402: READY gate - must satisfy all invariants
            // 1. Check for OPEN/BLOCKING/ERROR issues
            List<MrpDecisionIssue> openIssues = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN");
            boolean hasBlockingIssues = openIssues.stream()
                    .anyMatch(i -> "BLOCKING".equals(i.getSeverity()) || "ERROR".equals(i.getSeverity()));
            
            if (!openIssues.isEmpty() || hasBlockingIssues) {
                log.warn("Batch {} has {} open issues (blocking: {}), setting WAITING_DECISION",
                        batchId, openIssues.size(), hasBlockingIssues);
                activateRunOrThrow(batchId, run.getId(), materialStage ? "MATERIAL_REVIEW" : "WAITING_DECISION");
                completeTask(claimedTask, run.getId(), rootCount, 0);
                return true;
            }

            progress.report(95, "校验待分配数量");
            // 2. Check for pending unallocated quantities
            List<MrpUnallocatedQuantity> pendingUnallocated =
                    unallocatedMapper.selectByRunIdAndStatus(run.getId(), "PENDING");
            if (!pendingUnallocated.isEmpty()) {
                log.warn("Batch {} has {} pending unallocated quantities, setting WAITING_DECISION",
                        batchId, pendingUnallocated.size());
                activateRunOrThrow(batchId, run.getId(), materialStage ? "MATERIAL_REVIEW" : "WAITING_DECISION");
                completeTask(claimedTask, run.getId(), rootCount, 0);
                return true;
            }

            progress.report(96, "校验排产数量守恒");
            // 3. Check conservation
            DailyPlanningService.ConservationCheckResult conservationResult =
                    dailyPlanningService.getSourceMonthConservation(batchId, run.getId());
            if (conservationResult.mismatchCount > 0) {
                log.warn("Batch {} has {} conservation mismatches, setting WAITING_DECISION",
                        batchId, conservationResult.mismatchCount);
                activateRunOrThrow(batchId, run.getId(), materialStage ? "MATERIAL_REVIEW" : "WAITING_DECISION");
                completeTask(claimedTask, run.getId(), rootCount, 0);
                return true;
            }
            if (!materialStage && com.mrp.mrp.service.ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())) {
                activateRunOrThrow(batchId, run.getId(), "SCHEDULE_READY");
                completeTask(claimedTask, run.getId(), rootCount, 0);
                return true;
            }

            progress.report(99, "确认计算结果");
            // All invariants satisfied - set READY
            activateRunOrThrow(batchId, run.getId(), materialStage ? "MATERIAL_REVIEW" : "READY");

            // Mark task as succeeded
            completeTask(claimedTask, run.getId(), rootCount, 0);
            log.info("Task {} completed successfully, run {}", task.getId(), run.getId());
            return true;

        } catch (Exception e) {
            log.error("Task {} execution failed", task.getId(), e);

            // Persist failure before cleanup. A stale worker must neither overwrite a
            // terminal/newer task nor delete its candidate data.
            int failed = calcTaskMapper.failOwnedTask(task.getId(), workerId, claimedVersion,
                    e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown error");
            if (failed == 1) {
                if ("MRP_MATERIAL_ANALYSIS".equals(task.getTaskType())) {
                    Long batchId = toBatchId(task.getId());
                    if (batchId != null) {
                        MrpCalcBatch current = batchMapper.selectById(batchId);
                        if (current != null && "CALCULATING".equals(current.getStatus()))
                            batchMapper.updateStatusForce(current.getId(),
                                    Boolean.TRUE.equals(payloadWasRecalculation(task.getId()))
                                            ? "MATERIAL_RECALC_REQUIRED" : "SCHEDULE_READY");
                    }
                } else cleanupHalfFinishedData(task.getId());
            } else {
                log.warn("Task {} no longer belongs to this claim; skipping failure cleanup", task.getId());
            }

            return false;
        }
    }

    private Long toBatchId(Long taskId) {
        try {
            CalcTask task = calcTaskMapper.selectById(taskId);
            Map<String,Object> payload = objectMapper.readValue(task.getInputPayload(),
                    new TypeReference<Map<String,Object>>() {});
            return toLong(payload.get("batchId"));
        } catch (Exception error) {
            log.warn("Cannot recover batch id from task {}", taskId, error);
            return null;
        }
    }

    private Boolean payloadWasRecalculation(Long taskId) {
        try {
            CalcTask task = calcTaskMapper.selectById(taskId);
            Map<String,Object> payload = objectMapper.readValue(task.getInputPayload(),
                    new TypeReference<Map<String,Object>>() {});
            return Boolean.TRUE.equals(payload.get("materialRecalculation"));
        } catch (Exception error) {
            log.warn("Cannot recover material recalculation state from task {}", taskId, error);
            return false;
        }
    }

    private void completeTask(CalcTask task, Long runId, int successRoots, int failedRoots) {
        calcTaskMapper.updateProgress(task.getId(), 100, 100,
                "COMPLETED", successRoots, failedRoots);
        calcTaskMapper.updateResult(task.getId(), CalcTask.STATUS_SUCCEEDED, runId, task.getVersion());
    }

    private void setStatusOrThrow(Long batchId, String status) {
        if (batchMapper.updateStatusForce(batchId, status) != 1)
            throw new IllegalStateException("排产批次已失效，不能写入计算状态: " + batchId);
    }

    private void activateRunOrThrow(Long batchId, Long runId, String status) {
        if (batchMapper.activateRunAndStatus(batchId, runId, status) != 1)
            throw new IllegalStateException("排产批次已失效，不能激活计算结果: " + batchId);
    }

    private void cleanupHalfFinishedData(Long taskId) {
        try {
            CalcTask task = calcTaskMapper.selectById(taskId);
            if (task == null || task.getInputPayload() == null) {
                return;
            }

            Map<String, Object> payload = objectMapper.readValue(
                    task.getInputPayload(), new TypeReference<Map<String, Object>>() {});
            Long batchId = toLong(payload.get("batchId"));
            Long taskRunId = toLong(payload.get("runId"));

            if (batchId == null) {
                return;
            }

            MrpCalcBatch batch = batchMapper.selectById(batchId);
            if (batch == null) {
                return;
            }

            Long activeRunId = batch.getActiveRunId();

            // Only cleanup if this task's runId is NOT the active run
            // This prevents deleting a previous successful run's data
            if (taskRunId != null && !taskRunId.equals(activeRunId)) {
                log.info("Cleaning up half-finished data for batch {} task run {} (active run: {})",
                        batchId, taskRunId, activeRunId);

                // Delete requirements for this task's run
                requirementDetailMapper.deleteByRunId(taskRunId);
                policyNotices.deleteRun(taskRunId);
                // Delete terminal demands for this task's run
                terminalDemandMapper.deleteByRunId(taskRunId);
                // Delete daily plans for this task's run
                dailyPlanMapper.deleteByRunId(taskRunId);
                // Delete unallocated quantities for this task's run
                unallocatedMapper.deleteByRunId(taskRunId);

                // Reset batch status back to READY_TO_CALCULATE only if active run is null
                if (activeRunId == null) {
                    resetBatchAfterFailure(batch);
                }
            } else if (taskRunId == null) {
                // A failed calculation may throw before its run id can be persisted in the task.
                // Never infer that the batch's active run belongs to the failed task: it may be
                // the previous successful result and must remain queryable.
                log.warn("Task {} has no candidate run id for batch {}; preserving active run {}",
                        taskId, batchId, activeRunId);
                resetBatchAfterFailure(batch);
            } else {
                // An active run may already be a valid result; preserve its data and
                // allow reset only if the batch is still in an eligible calculation state.
                log.warn("Task {} run {} is the active run for batch {}, preserving data.",
                        taskId, taskRunId, batchId);
                if ("MRP_MATERIAL_ANALYSIS".equals(task.getTaskType())) {
                    batchMapper.updateStatusForce(batchId, Boolean.TRUE.equals(payloadWasRecalculation(taskId))
                            ? "MATERIAL_RECALC_REQUIRED" : "SCHEDULE_READY");
                } else resetBatchAfterFailure(batch);
            }

        } catch (Exception cleanupEx) {
            log.error("Error cleaning up half-finished data for task {}", taskId, cleanupEx);
        }
    }

    private void resetBatchAfterFailure(MrpCalcBatch observed) {
        int updated = batchMapper.resetAfterCalculationFailure(
                observed.getId(), observed.getVersion(), observed.getActiveRunId());
        if (updated == 1) {
            log.info("Reset batch {} status to READY_TO_CALCULATE after failed calculation", observed.getId());
        } else {
            log.info("Preserving batch {} after failure cleanup; state or version no longer eligible (observed: {})",
                    observed.getId(), observed.getStatus());
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }
}
