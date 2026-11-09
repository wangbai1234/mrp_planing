package com.mrp.mrp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable, per-batch lifecycle submissions. The request never waits for a full fact copy. */
@Service
public class MrpLifecycleTaskService {
    public static final String PUBLISH = "MRP_PUBLISH";
    public static final String REVISION = "MRP_REVISION";
    private final CalcTaskMapper tasks;
    private final MrpCalcBatchMapper batches;
    private final ObjectMapper json;
    private final PlanningReleaseValidationService releaseValidation;

    public MrpLifecycleTaskService(CalcTaskMapper tasks, MrpCalcBatchMapper batches, ObjectMapper json, PlanningReleaseValidationService releaseValidation) {
        this.releaseValidation = releaseValidation;
        this.tasks = tasks; this.batches = batches; this.json = json;
    }

    public CalcTask latest(Long batchId, String kind) {
        if (!PUBLISH.equals(kind) && !REVISION.equals(kind)) throw new IllegalArgumentException("未知任务类型");
        return tasks.selectLatestLifecycleForBatch(batchId, kind, prefix(kind));
    }

    @Transactional
    public CalcTask submit(Long batchId, String kind, String capacityFingerprint, Long operatorId) {
        if (!PUBLISH.equals(kind) && !REVISION.equals(kind)) throw new IllegalArgumentException("未知任务类型");
        MrpCalcBatch batch = batches.selectForUpdate(batchId);
        if (batch == null) throw new IllegalArgumentException("排产版本不存在");
        CalcTask previous = latest(batchId, kind);
        // Pending/running tasks and usable successes remain idempotent. A cancelled
        // revision is historical, so an explicit new submission may create a new task.
        boolean cancelledRevision = false;
        if (previous != null && REVISION.equals(kind) && "SUCCEEDED".equals(previous.getStatus())
                && previous.getResultResourceId() != null) {
            MrpCalcBatch result = batches.selectById(previous.getResultResourceId());
            cancelledRevision = result != null && "CANCELLED".equals(result.getStatus())
                    && batchId.equals(result.getRevisionOfBatchId());
        }
        if (previous != null && ("PENDING".equals(previous.getStatus()) || "RUNNING".equals(previous.getStatus())
                || "SUCCEEDED".equals(previous.getStatus()) && !cancelledRevision))
            return tasks.selectById(previous.getId());
        String expected = PUBLISH.equals(kind) ? "READY" : "PUBLISHED";
        if (!expected.equals(batch.getStatus()))
            throw new IllegalStateException("当前版本状态为 " + batch.getStatus() + "，不能执行" + (PUBLISH.equals(kind) ? "发布" : "修订"));
        if (PUBLISH.equals(kind)) releaseValidation.requirePublishable(batch);
        CalcTask task = new CalcTask();
        task.setTaskType(kind); task.setBusinessScope("mrp");
        task.setRequestKey(prefix(kind) + batchId + "-" + java.util.UUID.randomUUID());
        task.setStatus(CalcTask.STATUS_PENDING); task.setPriority(5);
        task.setProgressCurrent(0); task.setProgressTotal(100);
        task.setAttemptCount(0); task.setMaxAttempts(1); task.setVersion(0);
        task.setInputChecksum(batch.getInputChecksum());
        task.setCreatedBy(operatorId); task.setCreatedAt(Instant.now());
        task.setPhase(PUBLISH.equals(kind) ? "等待发布" : "等待生成修订版");
        try {
            task.setInputPayload(json.writeValueAsString(Map.of("batchId", batchId,
                    "capacityFingerprint", capacityFingerprint == null ? "" : capacityFingerprint)));
        } catch (JsonProcessingException e) { throw new IllegalStateException("无法提交任务", e); }
        tasks.insert(task);
        return task;
    }

    private static String prefix(String kind) { return PUBLISH.equals(kind) ? "mrp-publish-" : "mrp-revision-"; }
}
