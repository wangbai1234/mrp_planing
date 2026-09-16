package com.mrp.planning.service;

import com.mrp.planning.domain.PlanVersion;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AsyncCalculationService {

    private static final Logger log = LoggerFactory.getLogger(AsyncCalculationService.class);

    private final PlanningService planningService;
    private final CalcTaskMapper calcTaskMapper;

    public AsyncCalculationService(PlanningService planningService, CalcTaskMapper calcTaskMapper) {
        this.planningService = planningService;
        this.calcTaskMapper = calcTaskMapper;
    }

    @Async
    public void executeRecalculation(Long taskId, Long forecastVersionId, Long inventorySnapshotId,
                                      Long shipmentBatchId, Long capacityVersionId,
                                      String factoryCode, LocalDate currentWeekStart, Long userId) {
        log.info("异步重算开始: taskId={}, factory={}", taskId, factoryCode);
        try {
            PlanVersion version = planningService.recalculate(
                    forecastVersionId, inventorySnapshotId, shipmentBatchId,
                    capacityVersionId, factoryCode, currentWeekStart, userId);
            calcTaskMapper.updateResult(taskId, CalcTask.STATUS_SUCCEEDED, version.id(), 0);
            calcTaskMapper.updateProgress(taskId, 1, 1, "SUCCEEDED", 1, 0);
            log.info("异步重算完成: taskId={}, planVersionId={}", taskId, version.id());
        } catch (Exception e) {
            log.error("异步重算失败: taskId={}, error={}", taskId, e.getMessage(), e);
            calcTaskMapper.updateFailure(taskId, CalcTask.STATUS_FAILED, "MRP_CALC_ERROR", e.getMessage(), 0);
            calcTaskMapper.updateProgress(taskId, 0, 1, "FAILED", 0, 1);
        }
    }

    @Async
    public void executeRecalculationWithBomExplosion(Long taskId, Long forecastVersionId, Long inventorySnapshotId,
                                                      Long shipmentBatchId, Long capacityVersionId,
                                                      String factoryCode, LocalDate currentWeekStart,
                                                      List<String> splitCategories, Long userId) {
        log.info("异步BOM拆分重算开始: taskId={}, factory={}, categories={}", taskId, factoryCode, splitCategories);
        try {
            // 更新进度：开始
            calcTaskMapper.updateProgress(taskId, 0, 0, "LOADING", 0, 0);

            PlanVersion version = planningService.recalculateWithBomExplosion(
                    forecastVersionId, inventorySnapshotId, shipmentBatchId,
                    capacityVersionId, factoryCode, currentWeekStart, splitCategories, userId, taskId);

            calcTaskMapper.updateResult(taskId, CalcTask.STATUS_SUCCEEDED, version.id(), 0);
            calcTaskMapper.updateProgress(taskId, 1, 1, "SUCCEEDED", 1, 0);
            log.info("异步BOM拆分重算完成: taskId={}, planVersionId={}", taskId, version.id());
        } catch (Exception e) {
            log.error("异步BOM拆分重算失败: taskId={}, error={}", taskId, e.getMessage(), e);
            calcTaskMapper.updateFailure(taskId, CalcTask.STATUS_FAILED, "BOM_EXPLOSION_FAILED", e.getMessage(), 0);
            calcTaskMapper.updateProgress(taskId, 0, 1, "FAILED", 0, 1);
        }
    }

    @Async
    public void executeRecalculationAllFactories(Long taskId, Long forecastVersionId, Long inventorySnapshotId,
                                                   Long shipmentBatchId, Long capacityVersionId,
                                                   LocalDate currentWeekStart, Long userId) {
        log.info("异步重算开始(全工厂): taskId={}", taskId);
        try {
            List<PlanVersion> versions = planningService.recalculateAllFactories(
                    forecastVersionId, inventorySnapshotId, shipmentBatchId,
                    capacityVersionId, currentWeekStart, userId);
            PlanVersion lastVersion = versions.get(versions.size() - 1);
            calcTaskMapper.updateResult(taskId, CalcTask.STATUS_SUCCEEDED, lastVersion.id(), 0);
            calcTaskMapper.updateProgress(taskId, 1, 1, "SUCCEEDED", 1, 0);
            log.info("异步重算完成(全工厂): taskId={}, versions={}", taskId, versions.size());
        } catch (Exception e) {
            log.error("异步重算失败(全工厂): taskId={}, error={}", taskId, e.getMessage(), e);
            calcTaskMapper.updateFailure(taskId, CalcTask.STATUS_FAILED, "MRP_CALC_ERROR", e.getMessage(), 0);
            calcTaskMapper.updateProgress(taskId, 0, 1, "FAILED", 0, 1);
        }
    }

    @Async
    public void executeRecalculationAllFactoriesWithBomExplosion(Long taskId, Long forecastVersionId, Long inventorySnapshotId,
                                                                   Long shipmentBatchId, Long capacityVersionId,
                                                                   LocalDate currentWeekStart,
                                                                   List<String> splitCategories, Long userId) {
        log.info("异步BOM拆分重算开始(全工厂): taskId={}, categories={}", taskId, splitCategories);
        try {
            calcTaskMapper.updateProgress(taskId, 0, 0, "LOADING", 0, 0);

            List<String> factories = planningService.getDistinctFactories(forecastVersionId);
            List<PlanVersion> versions = new java.util.ArrayList<>();
            int totalFactories = factories.size();
            for (int i = 0; i < totalFactories; i++) {
                String fc = factories.get(i);
                log.info("BOM拆分重算: factory={} ({}/{})", fc, i + 1, totalFactories);
                PlanVersion version = planningService.recalculateWithBomExplosion(
                        forecastVersionId, inventorySnapshotId, shipmentBatchId,
                        capacityVersionId, fc, currentWeekStart, splitCategories, userId, taskId);
                versions.add(version);
            }
            PlanVersion lastVersion = versions.get(versions.size() - 1);
            calcTaskMapper.updateResult(taskId, CalcTask.STATUS_SUCCEEDED, lastVersion.id(), 0);
            calcTaskMapper.updateProgress(taskId, 1, 1, "SUCCEEDED", 1, 0);
            log.info("异步BOM拆分重算完成(全工厂): taskId={}, versions={}", taskId, versions.size());
        } catch (Exception e) {
            log.error("异步BOM拆分重算失败(全工厂): taskId={}, error={}", taskId, e.getMessage(), e);
            calcTaskMapper.updateFailure(taskId, CalcTask.STATUS_FAILED, "BOM_EXPLOSION_FAILED", e.getMessage(), 0);
            calcTaskMapper.updateProgress(taskId, 0, 1, "FAILED", 0, 1);
        }
    }

    @Scheduled(fixedDelay = 60000) // 每分钟检查
    public void cleanupStuckTasks() {
        try {
            int count = calcTaskMapper.failStuckTasks(30);
            if (count > 0) {
                log.warn("清理卡住的任务: {}个任务被标记为FAILED", count);
            }
        } catch (Exception e) {
            log.error("清理卡住任务失败: {}", e.getMessage());
        }
    }
}
