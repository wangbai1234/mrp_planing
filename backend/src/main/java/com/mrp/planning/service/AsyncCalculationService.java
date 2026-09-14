package com.mrp.planning.service;

import com.mrp.planning.domain.PlanVersion;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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
            log.info("异步重算完成: taskId={}, planVersionId={}", taskId, version.id());
        } catch (Exception e) {
            log.error("异步重算失败: taskId={}, error={}", taskId, e.getMessage(), e);
            calcTaskMapper.updateFailure(taskId, CalcTask.STATUS_FAILED, "MRP_CALC_ERROR", e.getMessage(), 0);
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
            log.info("异步重算完成(全工厂): taskId={}, versions={}", taskId, versions.size());
        } catch (Exception e) {
            log.error("异步重算失败(全工厂): taskId={}, error={}", taskId, e.getMessage(), e);
            calcTaskMapper.updateFailure(taskId, CalcTask.STATUS_FAILED, "MRP_CALC_ERROR", e.getMessage(), 0);
        }
    }
}
