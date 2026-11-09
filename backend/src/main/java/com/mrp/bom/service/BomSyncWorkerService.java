package com.mrp.bom.service;

import com.mrp.bom.domain.BomSyncTask;
import com.mrp.bom.local.repository.BomSyncTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BomSyncWorkerService {
    private static final Logger log = LoggerFactory.getLogger(BomSyncWorkerService.class);
    private final String workerId = "bom-" + UUID.randomUUID();
    private final BomSyncTaskMapper tasks;
    private final BomService bomService;
    private final BomSyncCommitService commitService;

    public BomSyncWorkerService(BomSyncTaskMapper tasks, BomService bomService, BomSyncCommitService commitService) {
        this.tasks = tasks;
        this.bomService = bomService;
        this.commitService = commitService;
    }

    @Scheduled(fixedDelay = 2000, scheduler = "bomSyncScheduler")
    public void poll() {
        try {
            tasks.failStale();
            BomSyncTask pending = tasks.selectPending();
            if (pending == null || tasks.claim(pending.getId(), workerId) != 1) return;
            long taskId = pending.getId();
            try {
                var details = bomService.fetchFromOracle((current, total) -> {
                    if (tasks.updateProgress(taskId, workerId, current, total, "从NC拉取BOM") != 1) {
                        throw new IllegalStateException("BOM同步任务已失去执行权");
                    }
                });
                if (tasks.updateProgress(taskId, workerId, details.size(), details.size(), "保存本地BOM") != 1) {
                    throw new IllegalStateException("BOM同步任务已失去执行权");
                }
                commitService.replaceAndFinish(taskId, workerId, details);
            } catch (Exception e) {
                log.error("BOM同步任务 {} 失败", taskId, e);
                tasks.fail(taskId, workerId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("BOM同步任务轮询失败", e);
        }
    }

    @Scheduled(fixedDelay = 30000, scheduler = "mrpHeartbeatScheduler")
    public void heartbeat() {
        try { tasks.heartbeat(workerId); }
        catch (Exception e) { log.error("BOM同步任务心跳失败", e); }
    }
}
