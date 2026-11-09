package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.domain.MrpDailyExecution;
import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDailyExecutionMapper;
import com.mrp.mrp.repository.MrpDailyPlanDetailMapper;
import com.mrp.mrp.repository.MrpCalcExceptionMapper;
import com.mrp.mrp.domain.MrpCalcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;

@Service
public class MrpExecutionService {

    private static final Logger log = LoggerFactory.getLogger(MrpExecutionService.class);

    private final DailyPlanningService dailyPlanning;
    private final MrpDailyExecutionMapper executionMapper;
    private final MrpDailyPlanDetailMapper dailyPlanMapper;
    private final MrpCalcBatchMapper batchMapper;
    private final MrpCalcExceptionMapper exceptionMapper;

    public MrpExecutionService(
            MrpDailyExecutionMapper executionMapper,
            MrpDailyPlanDetailMapper dailyPlanMapper,
            MrpCalcBatchMapper batchMapper,
            MrpCalcExceptionMapper exceptionMapper, @org.springframework.context.annotation.Lazy DailyPlanningService dailyPlanning) {
        this.dailyPlanning = dailyPlanning;
        this.executionMapper = executionMapper;
        this.dailyPlanMapper = dailyPlanMapper;
        this.batchMapper = batchMapper;
        this.exceptionMapper = exceptionMapper;
    }

    @Transactional
    public MrpDailyExecution confirmExecution(Long batchId, String rowKey, BigDecimal actualQty,
                                               Long operatorId, String remark) {
        // Get batch and active run
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        Long runId = batch.getActiveRunId();
        if (runId == null) {
            throw new IllegalStateException("Batch has no active run");
        }

        // Validate rowKey belongs to batch's active run
        MrpDailyPlanDetail plan = dailyPlanning.getDailyPlans(batchId).stream()
                .filter(p -> java.util.Objects.equals(p.getRowKey(), rowKey)).findFirst().orElse(null);
        if (plan == null) {
            throw new IllegalArgumentException("RowKey not found in active run: " + rowKey);
        }

        LocalDate scheduleDate = plan.getScheduleDate();

        // Executed facts cannot be changed through this endpoint.
        MrpDailyExecution existing = executionMapper.selectByBatchRowDate(batchId, rowKey, scheduleDate);
        if (existing != null) {
            if ("EXECUTED".equals(existing.getExecutionStatus())) {
                throw new IllegalStateException("Cannot confirm execution twice: date " + scheduleDate + " is already EXECUTED");
            }
        }

        // Create or update execution record with quantity difference check
        MrpDailyExecution execution;
        if (existing == null) {
            execution = new MrpDailyExecution();
            execution.setBatchId(batchId);
            execution.setRowKey(rowKey);
            execution.setScheduleDate(scheduleDate);
            execution.setExecutionStatus("EXECUTED");
            execution.setActualQuantity(actualQty);
            execution.setPlannedQuantity(plan.getEffectiveQuantity());
            execution.setConfirmedBy(operatorId);
            execution.setConfirmedAt(LocalDateTime.now());
            execution.setRemark(remark);
            execution.setRevision(0);
            executionMapper.insert(execution);
            log.info("Created execution record for batch {} rowKey {} date {}", batchId, rowKey, scheduleDate);
        } else {
            if (executionMapper.freezeStatus(existing.getId(), "EXECUTED", plan.getEffectiveQuantity(),
                    actualQty, operatorId, remark, existing.getRevision()) != 1) {
                throw new IllegalStateException("Execution record changed; refresh and retry");
            }
            execution = executionMapper.selectByBatchRowDate(batchId, rowKey, scheduleDate);
            log.info("Updated execution record {} for batch {} rowKey {} date {}",
                    existing.getId(), batchId, rowKey, scheduleDate);
        }

        // Check quantity mismatch
        if (actualQty.compareTo(plan.getEffectiveQuantity()) != 0) {
            MrpCalcException mismatch = new MrpCalcException();
            mismatch.setRunId(runId);
            mismatch.setBatchId(batchId);
            mismatch.setSeverity("WARNING");
            mismatch.setErrorCode("EXECUTION_QUANTITY_MISMATCH");
            mismatch.setScopeKey(rowKey);
            mismatch.setMessage("Actual qty " + actualQty.toPlainString() + " != planned qty " + plan.getEffectiveQuantity().toPlainString());
            exceptionMapper.insert(mismatch);
            log.warn("Quantity mismatch for batch {} rowKey {}: actual={}, planned={}",
                    batchId, rowKey, actualQty, plan.getEffectiveQuantity());
        }

        return execution;
    }

    @Transactional
    public void lockDay(Long batchId, LocalDate scheduleDate) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }
        Long runId = batch.getActiveRunId();
        if (runId == null) {
            throw new IllegalStateException("Batch has no active run");
        }

        List<MrpDailyPlanDetail> plans = dailyPlanning.getDailyPlans(batchId, scheduleDate, scheduleDate);

        if (plans.isEmpty()) {
            throw new IllegalStateException("No daily plans found for batch " + batchId + " on " + scheduleDate);
        }

        for (MrpDailyPlanDetail plan : plans) {
            // Use real SHA-256 rowKey from daily plan detail
            String rowKey = plan.getRowKey();
            MrpDailyExecution existing = executionMapper.selectByBatchRowDate(batchId, rowKey, scheduleDate);

            if (existing == null) {
                // Create LOCKED record with planned quantity snapshot
                MrpDailyExecution execution = new MrpDailyExecution();
                execution.setBatchId(batchId);
                execution.setRowKey(rowKey);
                execution.setScheduleDate(scheduleDate);
                execution.setExecutionStatus("LOCKED");
                execution.setPlannedQuantity(plan.getEffectiveQuantity());
                execution.setRevision(0);
                executionMapper.insert(execution);
            } else if ("PLANNED".equals(existing.getExecutionStatus())) {
                if (executionMapper.freezeStatus(existing.getId(), "LOCKED", plan.getEffectiveQuantity(),
                        null, null, null, existing.getRevision()) != 1) {
                    throw new IllegalStateException("Execution record changed; refresh and retry");
                }
            }
            // If already LOCKED or EXECUTED, skip
        }

        log.info("Locked {} plans for batch {} on date {}", plans.size(), batchId, scheduleDate);
    }

    @Transactional
    public void unlockDay(Long batchId, LocalDate scheduleDate) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("Batch not found: " + batchId);
        }

        List<MrpDailyExecution> executions = executionMapper.selectByBatchId(batchId);

        for (MrpDailyExecution exec : executions) {
            if (scheduleDate.equals(exec.getScheduleDate())) {
                if ("EXECUTED".equals(exec.getExecutionStatus())) {
                    throw new IllegalStateException("Cannot unlock date " + scheduleDate +
                            ": plans are already EXECUTED");
                }
                if ("LOCKED".equals(exec.getExecutionStatus())) {
                    executionMapper.updateStatus(exec.getId(), "PLANNED", null, null, null, exec.getRevision());
                }
            }
        }

        log.info("Unlocked plans for batch {} on date {}", batchId, scheduleDate);
    }

    public Map<String, Object> getExecutionStatus(Long batchId) {
        Map<String, MrpDailyExecution> saved = new HashMap<>();
        for (MrpDailyExecution execution : executionMapper.selectByBatchId(batchId)) {
            saved.put(execution.getRowKey() + "|" + execution.getScheduleDate(), execution);
        }
        List<MrpDailyExecution> executions = new java.util.ArrayList<>();
        for (MrpDailyPlanDetail plan : dailyPlanning.getDailyPlans(batchId)) {
            if (!"MACHINE_OUTPUT".equals(plan.getTerminalType())) continue;
            MrpDailyExecution execution = saved.get(plan.getRowKey() + "|" + plan.getScheduleDate());
            if (execution == null) {
                execution = new MrpDailyExecution();
                execution.setBatchId(batchId);
                execution.setRowKey(plan.getRowKey());
                execution.setScheduleDate(plan.getScheduleDate());
                execution.setExecutionStatus("PLANNED");
                execution.setPlannedQuantity(plan.getEffectiveQuantity());
            }
            execution.setFactoryCode(plan.getFactoryCode());
            execution.setRootMaterialCode(plan.getRootMaterialCode());
            executions.add(execution);
        }

        long plannedCount = 0;
        long lockedCount = 0;
        long executedCount = 0;

        for (MrpDailyExecution exec : executions) {
            switch (exec.getExecutionStatus()) {
                case "PLANNED": plannedCount++; break;
                case "LOCKED": lockedCount++; break;
                case "EXECUTED": executedCount++; break;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchId", batchId);
        summary.put("totalRecords", executions.size());
        summary.put("planned", plannedCount);
        summary.put("locked", lockedCount);
        summary.put("executed", executedCount);
        summary.put("records", executions);

        return summary;
    }

    public PageResult<MrpDailyExecution> getExecutionPage(Long batchId, String keyword,
            LocalDate scheduleDate, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new IllegalArgumentException("Batch not found: " + batchId);
        if (batch.getActiveRunId() == null) return new PageResult<>(List.of(), 0, page, pageSize);

        String search = keyword == null ? "" : keyword.trim();
        int total = executionMapper.countPage(batchId, batch.getActiveRunId(), search, scheduleDate);
        if (total == 0 || (long) (page - 1) * pageSize >= total) {
            return new PageResult<>(List.of(), total, page, pageSize);
        }
        List<MrpDailyExecution> items = executionMapper.selectPage(batchId, batch.getActiveRunId(),
                search, scheduleDate, (page - 1) * pageSize, pageSize);
        return new PageResult<>(items, total, page, pageSize);
    }

    public boolean isDateLockedOrExecuted(Long batchId, LocalDate scheduleDate) {
        List<MrpDailyExecution> executions = executionMapper.selectByBatchId(batchId);
        for (MrpDailyExecution exec : executions) {
            if (scheduleDate.equals(exec.getScheduleDate())) {
                if ("LOCKED".equals(exec.getExecutionStatus()) || "EXECUTED".equals(exec.getExecutionStatus())) {
                    return true;
                }
            }
        }
        return false;
    }
}
