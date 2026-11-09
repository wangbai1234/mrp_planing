package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.DailyDistributionEngine;
import com.mrp.mrp.repository.*;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DailyPlanningService {

    private static final Logger log = LoggerFactory.getLogger(DailyPlanningService.class);
    private static final int WRITE_BATCH_SIZE = 1000;

    private final com.mrp.audit.service.AuditService audit;
    private final MrpDecisionIssueMapper issueMapper;
    private final MrpDailyPlanDetailMapper dailyPlanMapper;
    private final MrpDailyPlanOverrideMapper overrideMapper;
    private final MrpUnallocatedQuantityMapper unallocatedMapper;
    private final MrpTerminalDemandMapper terminalDemandMapper;
    private final ProductionCalendarDayMapper calendarDayMapper;
    private final MaterialPolicyVersionService materialPolicyService;
    private final MrpExecutionService executionService;
    private final MrpDailyExecutionMapper executionMapper;
    private final MrpCalcBatchMapper batchMapper;
    private final Phase2ScopeConfigMapper scopeConfigMapper;
    private final DailyCapacityService capacityService;

    public DailyPlanningService(
            MrpDailyPlanDetailMapper dailyPlanMapper,
            MrpDailyPlanOverrideMapper overrideMapper,
            MrpUnallocatedQuantityMapper unallocatedMapper,
            MrpTerminalDemandMapper terminalDemandMapper,
            ProductionCalendarDayMapper calendarDayMapper,
            MaterialPolicyVersionService materialPolicyService,
            MrpExecutionService executionService,
            MrpDailyExecutionMapper executionMapper,
            MrpCalcBatchMapper batchMapper,
            Phase2ScopeConfigMapper scopeConfigMapper, com.mrp.audit.service.AuditService audit, MrpDecisionIssueMapper issueMapper, DailyCapacityService capacityService) {
        this.capacityService = capacityService;
        this.issueMapper = issueMapper;
        this.audit = audit;
        this.dailyPlanMapper = dailyPlanMapper;
        this.overrideMapper = overrideMapper;
        this.unallocatedMapper = unallocatedMapper;
        this.terminalDemandMapper = terminalDemandMapper;
        this.calendarDayMapper = calendarDayMapper;
        this.materialPolicyService = materialPolicyService;
        this.executionService = executionService;
        this.executionMapper = executionMapper;
        this.batchMapper = batchMapper;
        this.scopeConfigMapper = scopeConfigMapper;
    }

    /** Guard the legacy HTTP entry independently from the worker's candidate-run distribution. */
    @Transactional
    public DailyDistributionEngine.DistributionResult distributeFrozen(Long batchId, Long runId,
            Long calendarVersionId, Long materialPolicyVersionId) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) throw new IllegalArgumentException("排产版本不存在");
        if (!"WAITING_DAILY_DISTRIBUTION".equals(batch.getStatus()))
            throw new IllegalStateException("当前版本不允许直接日分配，请通过排产计算或修订操作");
        if (runId == null || !runId.equals(batch.getActiveRunId()))
            throw new IllegalStateException("计算结果已变化，请刷新后重试");
        if (calendarVersionId == null || !calendarVersionId.equals(batch.getProductionCalendarVersionId())
                || !Objects.equals(materialPolicyVersionId, batch.getMaterialPolicyVersionId()))
            throw new IllegalArgumentException("日分配必须使用当前批次冻结的生产日历和物料策略版本");
        // Serialize duplicate HTTP requests with the batch lock; never append a second allocation.
        if (dailyPlanMapper.hasDistributionResult(runId))
            throw new IllegalStateException("该计算结果已完成日分配，不能重复分配");
        return distribute(batchId, runId, calendarVersionId, materialPolicyVersionId);
    }

    @Transactional
    public DailyDistributionEngine.DistributionResult distribute(Long batchId, Long runId,
                                                                    Long calendarVersionId, Long materialPolicyVersionId) {
        return distribute(batchId, runId, calendarVersionId, materialPolicyVersionId,
                com.mrp.mrp.engine.CalculationProgressListener.NONE);
    }

    @Transactional
    public DailyDistributionEngine.DistributionResult distribute(Long batchId, Long runId,
            Long calendarVersionId, Long materialPolicyVersionId,
            com.mrp.mrp.engine.CalculationProgressListener progress) {
        return distributeSelected(batchId, runId, calendarVersionId, materialPolicyVersionId, progress, false);
    }

    @Transactional
    public DailyDistributionEngine.DistributionResult distributeMaterial(Long batchId, Long runId,
            Long calendarVersionId, Long materialPolicyVersionId,
            com.mrp.mrp.engine.CalculationProgressListener progress) {
        return distributeSelected(batchId, runId, calendarVersionId, materialPolicyVersionId, progress, true);
    }

    private DailyDistributionEngine.DistributionResult distributeSelected(Long batchId, Long runId,
            Long calendarVersionId, Long materialPolicyVersionId,
            com.mrp.mrp.engine.CalculationProgressListener progress, boolean materialOnly) {
        progress.report(65, "加载生产日历与末级需求");
        // Use runId for isolation - queries should use active run, not batch
        List<MrpTerminalDemand> terminalDemands = terminalDemandMapper.selectByRunId(runId).stream()
                .filter(d -> !materialOnly || !ProductionCapacityRules.MACHINE_OUTPUT.equals(d.getTerminalType())).toList();
        if (terminalDemands.isEmpty()) {
            log.warn("No terminal demands found for batch {} run {}", batchId, runId);
            return new DailyDistributionEngine.DistributionResult(List.of(), List.of());
        }

        List<ProductionCalendarDay> calendarDays = calendarDayMapper.selectByVersionId(calendarVersionId);
        if (calendarDays.isEmpty()) {
            throw new IllegalStateException("No production calendar days found for version " + calendarVersionId);
        }

        Map<String, MaterialPolicyDetail> materialPolicyMap =
                materialPolicyService.getDetailMap(materialPolicyVersionId);

        MrpCalcBatch distributionBatch = batchMapper.selectById(batchId);
        if (ProductionCapacityRules.dailyCapacity(distributionBatch)) {
            // Only the in-memory input is normalized. Historical snapshot weights remain immutable.
            calendarDays.forEach(day -> day.setAllocationWeight(BigDecimal.ONE));
        }
        DailyDistributionEngine.DistributionResult result;
        if (RollingPlanningQueryService.rolling(distributionBatch)) {
            LocalDate first = terminalDemands.stream().map(MrpTerminalDemand::getSourceMonth).min(LocalDate::compareTo).orElseThrow().withDayOfMonth(1);
            LocalDate last = terminalDemands.stream().map(MrpTerminalDemand::getSourceMonth).max(LocalDate::compareTo).orElseThrow().withDayOfMonth(1).plusMonths(1).minusDays(1);
            List<BusinessWeekDetail> weeks = batchMapper.selectBusinessWeeksByBatchAndRange(batchId, first.minusMonths(1), last.plusMonths(1));
            List<MrpDailyPlanDetail> details = new ArrayList<>();
            List<MrpUnallocatedQuantity> pending = new ArrayList<>();
            Map<String,List<MrpTerminalDemand>> factories = terminalDemands.stream().collect(Collectors.groupingBy(
                    MrpTerminalDemand::getFactoryCode, TreeMap::new, Collectors.toList()));
            int factoryIndex = 0;
            for (var factory : factories.entrySet()) {
                int startPercent = 65 + 10 * factoryIndex / factories.size();
                int endPercent = 65 + 10 * (factoryIndex + 1) / factories.size();
                var allocated = new com.mrp.mrp.engine.RollingBusinessWeekDistributionEngine(calendarDays, weeks, runId, batchId, factory.getKey(),
                                ProductionCapacityRules.shiftedBusinessWeeks(distributionBatch))
                        .distribute(factory.getValue(), (completed, total) -> progress.report(
                                com.mrp.mrp.engine.CalculationProgressListener.within(startPercent, endPercent, completed, total),
                                "生成日排产 · " + factory.getKey(), completed, total));
                factoryIndex++;
                details.addAll(allocated.details); pending.addAll(allocated.unallocated);
            }
            result = new DailyDistributionEngine.DistributionResult(details, pending);
        } else {
            String factoryCode = terminalDemands.get(0).getFactoryCode();
            DailyDistributionEngine engine = new DailyDistributionEngine(calendarDays, runId, batchId, factoryCode, materialPolicyMap);
            result = engine.distribute(terminalDemands, (completed, total) -> progress.report(
                    com.mrp.mrp.engine.CalculationProgressListener.within(65, 75, completed, total), "生成日排产", completed, total));
        }
        if (materialOnly) result = alignMaterialDates(batchId, runId, result);

        progress.report(75, "保护已锁定与已执行排产");
        List<MrpDailyPlanDetail> reconciledDetails = reconcileLockedAndExecuted(
                batchId, runId, terminalDemands, result.details, result.unallocated, materialPolicyMap);

        // SQL chunks share the surrounding transaction: later failures undo earlier chunks.
        progress.report(76, "保存日排产", 0, reconciledDetails.size());
        for (int start = 0; start < reconciledDetails.size(); start += WRITE_BATCH_SIZE) {
            dailyPlanMapper.insertBatch(reconciledDetails.subList(start,
                    Math.min(start + WRITE_BATCH_SIZE, reconciledDetails.size())));
            int completed = Math.min(start + WRITE_BATCH_SIZE, reconciledDetails.size());
            progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(76, 92, completed, reconciledDetails.size()),
                    "保存日排产", completed, reconciledDetails.size());
        }
        progress.report(92, "保存待分配明细", 0, result.unallocated.size());
        for (int start = 0; start < result.unallocated.size(); start += WRITE_BATCH_SIZE) {
            unallocatedMapper.insertBatch(result.unallocated.subList(start,
                    Math.min(start + WRITE_BATCH_SIZE, result.unallocated.size())));
            int completed = Math.min(start + WRITE_BATCH_SIZE, result.unallocated.size());
            progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(92, 94, completed, result.unallocated.size()),
                    "保存待分配明细", completed, result.unallocated.size());
        }

        log.info("Distributed {} daily plans, {} unallocated for batch {}",
                reconciledDetails.size(), result.unallocated.size(), batchId);
        return new DailyDistributionEngine.DistributionResult(reconciledDetails, result.unallocated);
    }

    private record MachineDemandKey(String factoryCode, String rootMaterialCode, LocalDate sourceMonth) {}

    /** Replays confirmed machine-date transfers onto newly generated terminal facts. */
    private DailyDistributionEngine.DistributionResult alignMaterialDates(Long batchId, Long runId,
            DailyDistributionEngine.DistributionResult result) {
        Map<MachineDemandKey,List<MrpDailyPlanDetail>> machines = getDailyPlans(batchId).stream()
                .filter(p -> ProductionCapacityRules.MACHINE_OUTPUT.equals(p.getTerminalType()))
                .collect(Collectors.groupingBy(p -> new MachineDemandKey(p.getFactoryCode(),
                        p.getRootMaterialCode(), p.getSourceMonth()), LinkedHashMap::new, Collectors.toList()));
        Map<String,List<MrpDailyPlanDetail>> groups = result.details.stream().collect(Collectors.groupingBy(
                DailyPlanningService::gridRowKey, LinkedHashMap::new, Collectors.toList()));
        List<MrpDailyPlanDetail> projected = new ArrayList<>();
        for (List<MrpDailyPlanDetail> group : groups.values()) {
            MrpDailyPlanDetail template = group.getFirst();
            List<MrpDailyPlanDetail> same = machines.getOrDefault(new MachineDemandKey(
                    template.getFactoryCode(), template.getRootMaterialCode(), template.getSourceMonth()), List.of());
            if (same.isEmpty()) throw new IllegalStateException("缺少已确认整机排产日事实");
            Map<LocalDate,BigDecimal> original = new TreeMap<>();
            Map<LocalDate,BigDecimal> current = new TreeMap<>();
            for (MrpDailyPlanDetail machine : same) {
                original.merge(machine.getScheduleDate(),machine.getSystemQuantity(),BigDecimal::add);
                current.merge(machine.getScheduleDate(),machine.getEffectiveQuantity(),BigDecimal::add);
            }
            BigDecimal originalTotal = original.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal currentTotal = current.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add);
            if (originalTotal.signum() <= 0 || originalTotal.compareTo(currentTotal) != 0)
                throw new IllegalStateException("整机调日后数量不守恒");
            if (original.equals(current)) { projected.addAll(group); continue; }
            BigDecimal materialTotal = group.stream().map(MrpDailyPlanDetail::getSystemQuantity)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);
            int scale = materialTotal.stripTrailingZeros().scale() <= 0 ? 0 : 6;
            BigDecimal assigned = BigDecimal.ZERO;
            List<Map.Entry<LocalDate,BigDecimal>> dates = current.entrySet().stream()
                    .filter(entry -> entry.getValue().signum() > 0).toList();
            for (int index=0; index<dates.size(); index++) {
                var date = dates.get(index);
                BigDecimal quantity = index == dates.size()-1 ? materialTotal.subtract(assigned)
                        : materialTotal.multiply(date.getValue()).divide(currentTotal,scale,java.math.RoundingMode.DOWN);
                assigned = assigned.add(quantity);
                if (quantity.signum() == 0) continue;
                MrpDailyPlanDetail plan = copyPlanToRun(template,batchId,runId);
                plan.setId(null); plan.setSourceDate(date.getKey()); plan.setScheduleDate(date.getKey());
                plan.setSystemQuantity(quantity); plan.setEffectiveQuantity(quantity);
                String identity = template.getPathGroupKey()+"|"+date.getKey();
                try {plan.setRowKey(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
                catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
                projected.add(plan);
            }
        }
        return new DailyDistributionEngine.DistributionResult(projected,result.unallocated);
    }

    private List<MrpDailyPlanDetail> reconcileLockedAndExecuted(
            Long batchId,
            Long runId,
            List<MrpTerminalDemand> demands,
            List<MrpDailyPlanDetail> generated,
            List<MrpUnallocatedQuantity> unallocated,
            Map<String, MaterialPolicyDetail> materialPolicyMap) {
        List<MrpDailyExecution> executions = executionMapper.selectByBatchId(batchId).stream()
                .filter(item -> "LOCKED".equals(item.getExecutionStatus())
                        || "EXECUTED".equals(item.getExecutionStatus()))
                .toList();
        if (executions.isEmpty()) {
            return generated;
        }

        List<MrpDailyPlanDetail> result = new ArrayList<>(generated);
        Map<String, MrpDailyPlanDetail> planByFactKey = result.stream()
                .collect(Collectors.toMap(this::factKey, item -> item, (left, right) -> left, LinkedHashMap::new));

        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Map<String, MrpDailyPlanDetail> sourceByFactKey = new LinkedHashMap<>();
        if (batch != null && batch.getRevisionOfBatchId() != null) {
            for (MrpDailyPlanDetail source : dailyPlanMapper.selectByBatchId(batch.getRevisionOfBatchId())) {
                sourceByFactKey.put(factKey(source), source);
            }
        }

        for (MrpDailyExecution execution : executions) {
            String key = factKey(execution.getRowKey(), execution.getScheduleDate());
            MrpDailyPlanDetail plan = planByFactKey.get(key);
            if (plan == null) {
                MrpDailyPlanDetail source = sourceByFactKey.get(key);
                if (source == null) {
                    throw new IllegalStateException("Locked/executed plan fact cannot be restored: " + key);
                }
                plan = copyPlanToRun(source, batchId, runId);
                result.add(plan);
                planByFactKey.put(key, plan);
            }
            BigDecimal frozenQuantity = execution.getPlannedQuantity() != null
                    ? execution.getPlannedQuantity() : plan.getSystemQuantity();
            plan.setSystemQuantity(frozenQuantity);
            plan.setEffectiveQuantity(null);
        }

        Map<String, BigDecimal> demandTotals = demands.stream().collect(Collectors.groupingBy(
                this::demandGroupKey, LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, MrpTerminalDemand::getQuantity, BigDecimal::add)));
        Map<String, BigDecimal> unallocatedTotals = unallocated.stream()
                .filter(item -> "PENDING".equals(item.getStatus()))
                .collect(Collectors.groupingBy(MrpUnallocatedQuantity::getDemandKey, LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, MrpUnallocatedQuantity::getQuantity, BigDecimal::add)));
        Set<String> frozenKeys = executions.stream()
                .map(item -> factKey(item.getRowKey(), item.getScheduleDate()))
                .collect(Collectors.toSet());

        for (Map.Entry<String, BigDecimal> demandEntry : demandTotals.entrySet()) {
            String groupKey = demandEntry.getKey();
            List<MrpDailyPlanDetail> groupPlans = result.stream()
                    .filter(item -> groupKey.equals(item.getPathGroupKey()))
                    .toList();
            BigDecimal frozenTotal = groupPlans.stream()
                    .filter(item -> frozenKeys.contains(factKey(item)))
                    .map(MrpDailyPlanDetail::getSystemQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal targetPlanned = demandEntry.getValue()
                    .subtract(unallocatedTotals.getOrDefault(groupKey, BigDecimal.ZERO));
            BigDecimal remaining = targetPlanned.subtract(frozenTotal);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Locked/executed quantity exceeds recalculated demand: " + groupKey);
            }
            List<MrpDailyPlanDetail> unlocked = groupPlans.stream()
                    .filter(item -> !frozenKeys.contains(factKey(item)))
                    .toList();
            if (unlocked.isEmpty()) {
                continue;
            }
            redistribute(unlocked, remaining);
        }
        return result;
    }

    private void redistribute(List<MrpDailyPlanDetail> plans, BigDecimal total) {
        int scale = !plans.isEmpty() && ProductionCapacityRules.MACHINE_OUTPUT.equals(plans.get(0).getTerminalType()) ? 0 : 6;
        if (total.stripTrailingZeros().scale() > scale) throw new IllegalStateException("锁定后剩余整机需求必须为整数台");
        if (scale == 0) {
            List<MrpDailyPlanDetail> ordered = plans.stream().sorted(Comparator.comparing(MrpDailyPlanDetail::getScheduleDate)).toList();
            BigDecimal base = total.divide(BigDecimal.valueOf(ordered.size()), 0, java.math.RoundingMode.FLOOR);
            int remainder = total.subtract(base.multiply(BigDecimal.valueOf(ordered.size()))).intValueExact();
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).setSystemQuantity(base.add(i < remainder ? BigDecimal.ONE : BigDecimal.ZERO));
                ordered.get(i).setEffectiveQuantity(null);
            }
            return;
        }
        BigDecimal originalTotal = plans.stream().map(MrpDailyPlanDetail::getSystemQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < plans.size(); i++) {
            BigDecimal quantity;
            if (i == plans.size() - 1) {
                quantity = total.subtract(allocated);
            } else if (originalTotal.compareTo(BigDecimal.ZERO) == 0) {
                quantity = BigDecimal.ZERO.setScale(scale);
            } else {
                quantity = total.multiply(plans.get(i).getSystemQuantity())
                        .divide(originalTotal, scale, java.math.RoundingMode.DOWN);
            }
            plans.get(i).setSystemQuantity(quantity);
            plans.get(i).setEffectiveQuantity(null);
            allocated = allocated.add(quantity);
        }
    }

    private String demandGroupKey(MrpTerminalDemand demand) {
        if (RollingPlanningQueryService.rolling(batchMapper.selectById(demand.getBatchId())))
            return com.mrp.mrp.engine.RollingBusinessWeekDistributionEngine.demandGroupKey(demand);
        return demand.getRootMaterialCode() + "|" + demand.getTerminalMaterialCode() + "|"
                + demand.getTerminalType() + "|" + demand.getSupplyMode() + "|"
                + demand.getSourceMonth().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String factKey(MrpDailyPlanDetail plan) {
        return factKey(plan.getRowKey(), plan.getScheduleDate());
    }

    private String factKey(String rowKey, LocalDate scheduleDate) {
        return rowKey + "|" + scheduleDate;
    }

    private MrpDailyPlanDetail copyPlanToRun(MrpDailyPlanDetail source, Long batchId, Long runId) {
        MrpDailyPlanDetail copy = new MrpDailyPlanDetail();
        copy.setRunId(runId);
        copy.setBatchId(batchId);
        copy.setFactoryCode(source.getFactoryCode());
        copy.setRootMaterialCode(source.getRootMaterialCode());
        copy.setTerminalMaterialCode(source.getTerminalMaterialCode());
        copy.setTerminalType(source.getTerminalType());
        copy.setSupplyMode(source.getSupplyMode());
        copy.setPathGroupKey(source.getPathGroupKey());
        copy.setSourceMonth(source.getSourceMonth());
        copy.setSourceDate(source.getSourceDate());
        copy.setScheduleDate(source.getScheduleDate());
        copy.setSystemQuantity(source.getSystemQuantity());
        copy.setIsProductionDay(source.getIsProductionDay());
        copy.setRevision(0);
        copy.setRowKey(source.getRowKey());
        return copy;
    }

    public List<MrpDailyPlanDetail> getDailyPlans(Long batchId, LocalDate startDate, LocalDate endDate) {
        // Use active run for isolation
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        return applyOverrides(batchId, dailyPlanMapper.selectByRunId(runId)).stream()
                .filter(p -> !p.getScheduleDate().isBefore(startDate) && !p.getScheduleDate().isAfter(endDate)).toList();
    }

    public List<MrpDailyPlanDetail> getDailyPlans(Long batchId) {
        // Use active run for isolation
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        return applyOverrides(batchId, dailyPlanMapper.selectByRunId(runId));
    }

    @Transactional
    public void saveOverride(MrpDailyPlanOverride override) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(override.getBatchId());
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + override.getBatchId());
        }
        if (!"READY".equals(batch.getStatus()) && !"WAITING_DECISION".equals(batch.getStatus())) {
            throw new IllegalStateException("Daily plan can only be adjusted before publication: " + batch.getStatus());
        }
        if (ProductionCapacityRules.dailyCapacity(batch))
            throw new IllegalStateException("请使用跨日调整，同时保存转出和转入以保持需求总量");
        // Check if the schedule date is LOCKED or EXECUTED
        if (executionService.isDateLockedOrExecuted(override.getBatchId(), override.getScheduleDate())) {
            throw new IllegalStateException("Cannot override locked/executed date: " + override.getScheduleDate());
        }

        if (override.getManualQuantity() == null || override.getManualQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Manual quantity must be a non-negative number");
        }
        if (override.getReason() == null || override.getReason().isBlank()) {
            throw new IllegalArgumentException("Override reason is required");
        }
        if (batch.getActiveRunId() == null) throw new IllegalStateException("计划尚未计算");
        boolean validKey = dailyPlanMapper.selectByRunId(batch.getActiveRunId()).stream().anyMatch(p ->
                overrideKey(p.getFactoryCode(), p.getRootMaterialCode(), p.getTerminalMaterialCode(), p.getTerminalType(),
                        p.getSupplyMode(), p.getPathGroupKey(), p.getScheduleDate()).equals(overrideKey(
                        override.getFactoryCode(), override.getRootMaterialCode(), override.getTerminalMaterialCode(),
                        override.getTerminalType(), override.getSupplyMode(), override.getPathGroupKey(), override.getScheduleDate())));
        if (!validKey) throw new IllegalArgumentException("调整必须对应真实需求；空白日期请使用跨日调整");
        MrpDailyPlanOverride existing = overrideMapper.selectByBusinessKey(override);
        if (existing == null) {
            override.setRevision(0);
            overrideMapper.insert(override);
        } else {
            existing.setManualQuantity(override.getManualQuantity());
            existing.setReason(override.getReason().trim());
            existing.setRevision(existing.getRevision() + 1);
            overrideMapper.update(existing);
            override.setId(existing.getId());
            override.setRevision(existing.getRevision());
        }
        refreshBatchGate(override.getBatchId());
        log.info("Saved override for {} on {}: manual={}",
                override.getTerminalMaterialCode(), override.getScheduleDate(), override.getManualQuantity());
    }

    @Transactional
    public void restoreAuto(Long overrideId) {
        MrpDailyPlanOverride existing = overrideMapper.selectById(overrideId);
        if (existing == null) {
            throw new IllegalStateException("Override not found: " + overrideId);
        }
        MrpCalcBatch batch = batchMapper.selectForUpdate(existing.getBatchId());
        if (batch == null || (!"READY".equals(batch.getStatus()) && !"WAITING_DECISION".equals(batch.getStatus()))) {
            throw new IllegalStateException("Daily plan can only be restored before publication");
        }
        if (ProductionCapacityRules.dailyCapacity(batch))
            throw new IllegalStateException("请使用跨日调整，不能单独恢复转出或转入数量");
        if (executionService.isDateLockedOrExecuted(existing.getBatchId(), existing.getScheduleDate()))
            throw new IllegalStateException("锁定/已执行日期不能恢复");
        overrideMapper.deleteById(overrideId);
        audit.log(com.mrp.common.security.CurrentUser.getUserId(), null, existing.getFactoryCode(), "RESTORE", "mrp_daily_plan_override",
                existing.getId().toString(), existing, null, null, null);
        refreshBatchGate(existing.getBatchId());
        log.info("Restored auto for override {}", overrideId);
    }

    private void refreshBatchGate(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getActiveRunId() == null
                || "PUBLISHED".equals(batch.getStatus()) || "STALE".equals(batch.getStatus())) {
            return;
        }
        ConservationCheckResult conservation = getSourceMonthConservation(batchId, batch.getActiveRunId());
        List<MrpUnallocatedQuantity> pending =
                unallocatedMapper.selectByRunIdAndStatus(batch.getActiveRunId(), "PENDING");
        String targetStatus = conservation.mismatchCount > 0 || !pending.isEmpty()
                || !issueMapper.selectByBatchIdAndStatus(batchId, "OPEN").isEmpty()
                ? "WAITING_DECISION" : "READY";
        if (ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())) {
            if ("SCHEDULE_READY".equals(batch.getStatus())) return;
            if ("MATERIAL_REVIEW".equals(batch.getStatus())) targetStatus = "MATERIAL_REVIEW";
        }
        if (!targetStatus.equals(batch.getStatus())) {
            batchMapper.updateStatusForce(batchId, targetStatus);
        }
    }

    public List<MrpDailyPlanOverride> getOverrides(Long batchId) {
        return overrideMapper.selectByBatchId(batchId);
    }

    public List<MrpUnallocatedQuantity> getUnallocated(Long batchId) {
        // P0-404: Use active run for isolation
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        return unallocatedMapper.selectByRunId(runId);
    }

    public com.mrp.common.response.PageResult<MrpUnallocatedQuantity> getPendingUnallocatedPage(Long batchId, String keyword, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch == null ? null : batch.getActiveRunId();
        if (runId == null) return new com.mrp.common.response.PageResult<>(List.of(), 0, page, pageSize);
        String search = keyword == null ? "" : keyword.trim();
        return new com.mrp.common.response.PageResult<>(
                unallocatedMapper.selectPageByRunIdAndStatus(runId, "PENDING", search, (page - 1) * pageSize, pageSize),
                unallocatedMapper.countByRunIdAndStatus(runId, "PENDING", search), page, pageSize);
    }

    public List<WeeklySummaryRow> getWeeklySummary(Long batchId) { return getWeeklySummary(batchId, null); }

    public List<WeeklySummaryRow> getWeeklySummary(Long batchId, String requestedView) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        String view = ProductionCapacityRules.view(batch, requestedView);
        Map<String, String> units = capacityService.units(batch == null ? null : batch.getProductionCalendarVersionId());
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        List<MrpDailyPlanDetail> plans = applyOverrides(batchId, dailyPlanMapper.selectByRunId(runId)).stream()
                .filter(plan -> ProductionCapacityRules.matches(view, plan)).toList();
        if (plans.isEmpty()) return Collections.emptyList();
        LocalDate startDate = plans.stream().map(MrpDailyPlanDetail::getScheduleDate)
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate endDate = plans.stream().map(MrpDailyPlanDetail::getScheduleDate)
                .max(LocalDate::compareTo).orElseThrow();
        List<BusinessWeekDetail> businessWeeks = getBusinessWeeks(batchId, startDate, endDate);
        List<WeeklySummaryRow> result = new ArrayList<>();
        for (BusinessWeekDetail businessWeek : businessWeeks) {
            List<MrpDailyPlanDetail> weekPlans = plans.stream()
                    .filter(plan -> !plan.getScheduleDate().isBefore(businessWeek.getStartDate())
                            && !plan.getScheduleDate().isAfter(businessWeek.getEndDate()))
                    .toList();
            for (var group : summaryGroups(weekPlans, units, view).entrySet()) {
                var groupedPlans = group.getValue();
                BigDecimal totalQty = groupedPlans.stream().map(MrpDailyPlanDetail::getEffectiveQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                result.add(new WeeklySummaryRow(businessWeekKey(businessWeek), businessWeek.getStartDate(), businessWeek.getEndDate(),
                        groupedPlans.size(), totalQty, summaryUnit(groupedPlans.get(0), units, view), view, ProductionCapacityRules.basis(batch), batch.getRuleVersion(), group.getKey()));
            }
        }
        return result;
    }

    public List<BusinessWeekDetail> getBusinessWeeks(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) return List.of();
        return normalizeBusinessWeeks(scopeConfigMapper.selectPublishedBusinessWeeksByRange(startDate, endDate));
    }

    public List<BusinessWeekDetail> getBusinessWeeks(Long batchId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) return List.of();
        if (batchId != null) {
            List<BusinessWeekDetail> locked = batchMapper.selectBusinessWeeksByBatchAndRange(
                    batchId, startDate, endDate);
            if (!locked.isEmpty() || RollingPlanningQueryService.rolling(batchMapper.selectById(batchId))) {
                if (RollingPlanningQueryService.rolling(batchMapper.selectById(batchId))) return locked.stream()
                        .sorted(Comparator.comparing(BusinessWeekDetail::getStartDate)).toList();
                return normalizeBusinessWeeks(locked);
            }
        }
        // Historical batches created before V033 have no scope rows. Keep them readable,
        // while all new batches are locked during creation.
        return getBusinessWeeks(startDate, endDate);
    }

    /**
     * A date must have exactly one reporting week. Older data may contain adjacent
     * monthly versions whose allowed cross-month ranges overlap at a boundary.
     * Keep the earliest-starting published week as the deterministic owner and
     * trim the covered prefix of a later week. Keep its uncovered days and identity;
     * the frozen database definition remains unchanged.
     */
    private List<BusinessWeekDetail> normalizeBusinessWeeks(List<BusinessWeekDetail> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<BusinessWeekDetail> sorted = source.stream()
                .filter(item -> item.getStartDate() != null && item.getEndDate() != null)
                .sorted(Comparator.comparing(BusinessWeekDetail::getStartDate)
                        .thenComparing(BusinessWeekDetail::getEndDate)
                        .thenComparing(BusinessWeekDetail::getBusinessMonth,
                                Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(BusinessWeekDetail::getWeekCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        List<BusinessWeekDetail> normalized = new ArrayList<>();
        LocalDate coveredThrough = null;
        for (BusinessWeekDetail item : sorted) {
            if (coveredThrough != null && !item.getStartDate().isAfter(coveredThrough)) {
                if (!item.getEndDate().isAfter(coveredThrough)) continue;
                BusinessWeekDetail uncovered = new BusinessWeekDetail();
                uncovered.setId(item.getId());
                uncovered.setVersionId(item.getVersionId());
                uncovered.setBusinessMonth(item.getBusinessMonth());
                uncovered.setWeekCode(item.getWeekCode());
                uncovered.setSortNo(item.getSortNo());
                uncovered.setStartDate(coveredThrough.plusDays(1));
                uncovered.setEndDate(item.getEndDate());
                normalized.add(uncovered);
            } else {
                normalized.add(item);
            }
            coveredThrough = item.getEndDate();
        }
        return normalized;
    }

    public BusinessWeekDetail findBusinessWeek(List<BusinessWeekDetail> weeks, LocalDate date) {
        if (date == null) return null;
        return weeks.stream()
                .filter(week -> !date.isBefore(week.getStartDate()) && !date.isAfter(week.getEndDate()))
                .findFirst().orElse(null);
    }

    public String businessWeekKey(BusinessWeekDetail week) {
        String month = week.getBusinessMonth() == null
                ? week.getStartDate().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                : week.getBusinessMonth().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return month + " " + week.getWeekCode();
    }

    public List<MonthlySummaryRow> getMonthlySummary(Long batchId) { return getMonthlySummary(batchId, null); }

    public List<MonthlySummaryRow> getMonthlySummary(Long batchId, String requestedView) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        String view = ProductionCapacityRules.view(batch, requestedView);
        Map<String, String> units = capacityService.units(batch == null ? null : batch.getProductionCalendarVersionId());
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        List<MrpDailyPlanDetail> plans = applyOverrides(batchId, dailyPlanMapper.selectByRunId(runId)).stream()
                .filter(plan -> ProductionCapacityRules.matches(view, plan)).toList();
        Map<String, List<MrpDailyPlanDetail>> monthGrouped = plans.stream()
                .collect(Collectors.groupingBy(
                        p -> formatSourceMonth(RollingPlanningQueryService.rolling(batch) ? p.getScheduleDate().withDayOfMonth(1) : p.getSourceMonth()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<MonthlySummaryRow> result = new ArrayList<>();
        for (Map.Entry<String, List<MrpDailyPlanDetail>> entry : monthGrouped.entrySet()) {
            String monthKey = entry.getKey();
            List<MrpDailyPlanDetail> monthPlans = entry.getValue();
            for (var group : summaryGroups(monthPlans, units, view).entrySet()) {
                var groupedPlans = group.getValue();
                BigDecimal totalQty = groupedPlans.stream().map(MrpDailyPlanDetail::getEffectiveQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                result.add(new MonthlySummaryRow(monthKey, groupedPlans.size(), totalQty,
                        summaryUnit(groupedPlans.get(0), units, view), view, ProductionCapacityRules.basis(batch), batch.getRuleVersion(), group.getKey()));
            }
        }
        return result;
    }

    private String summaryUnit(MrpDailyPlanDetail plan, Map<String, String> units, String view) {
        if (ProductionCapacityRules.MACHINE_OUTPUT.equals(view)) return "台";
        String unit = units.get(plan.getTerminalMaterialCode());
        return unit == null || unit.isBlank() ? null : unit;
    }
    private Map<String, List<MrpDailyPlanDetail>> summaryGroups(List<MrpDailyPlanDetail> plans, Map<String, String> units, String view) {
        return plans.stream().collect(Collectors.groupingBy(plan -> {
            String unit = summaryUnit(plan, units, view);
            return unit == null ? "MATERIAL|" + plan.getTerminalMaterialCode() : "UNIT|" + unit;
        }, LinkedHashMap::new, Collectors.toList()));
    }

    public ConservationCheckResult getSourceMonthConservation(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return new ConservationCheckResult(0, 0, Collections.emptyList());
        }
        return getSourceMonthConservation(batchId, runId);
    }

    /** Validates a candidate run before it becomes the batch's active run. */
    public ConservationCheckResult getSourceMonthConservation(Long batchId, Long runId) {
        if (runId == null) {
            return new ConservationCheckResult(0, 0, Collections.emptyList());
        }
        boolean rolling = RollingPlanningQueryService.rolling(batchMapper.selectById(batchId));
        return getSourceMonthConservation(batchId, runId, rolling
                ? dailyPlanMapper.selectConservationTotals(runId, batchId)
                : applyOverrides(batchId, dailyPlanMapper.selectByRunId(runId)));
    }

    /** Reuse the locked publication projection without reading all daily facts again. */
    public ConservationCheckResult getSourceMonthConservation(Long batchId, Long runId, List<MrpDailyPlanDetail> plans) {
        boolean rolling = RollingPlanningQueryService.rolling(batchMapper.selectById(batchId));
        List<MrpTerminalDemand> demands = terminalDemandMapper.selectByRunId(runId);
        List<MrpUnallocatedQuantity> unallocated = unallocatedMapper.selectByRunId(runId);

        // P0-403: Unify sourceMonth format to yyyy-MM for both demand and plan
        Map<String, BigDecimal> demandTotals = demands.stream()
                .collect(Collectors.groupingBy(
                        d -> (rolling ? d.getFactoryCode() + "|" : "") + d.getRootMaterialCode() + "|" + d.getTerminalMaterialCode() + "|"
                                + d.getTerminalType() + "|" + d.getSupplyMode() + "|"
                                + formatSourceMonth(d.getSourceMonth()),
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO, MrpTerminalDemand::getQuantity, BigDecimal::add)));

        Map<String, BigDecimal> planTotals = plans.stream()
                .collect(Collectors.groupingBy(
                        p -> (rolling ? p.getFactoryCode() + "|" : "") + p.getRootMaterialCode() + "|" + p.getTerminalMaterialCode() + "|"
                                + p.getTerminalType() + "|" + p.getSupplyMode() + "|"
                                + formatSourceMonth(p.getSourceMonth()),
                        LinkedHashMap::new,
                    Collectors.reducing(BigDecimal.ZERO, MrpDailyPlanDetail::getEffectiveQuantity, BigDecimal::add)));

        // A non-production target is not lost demand: it remains an explicit pending allocation.
        // Source-month conservation therefore covers both persisted daily facts and the
        // unallocated pool. Publication is independently blocked while any pool row is PENDING.
        for (MrpUnallocatedQuantity item : unallocated) {
            if ("PENDING".equals(item.getStatus())) {
                planTotals.merge(item.getDemandKey(), item.getQuantity(), BigDecimal::add);
            }
        }

        List<ConservationMismatch> mismatches = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : demandTotals.entrySet()) {
            BigDecimal demandQty = entry.getValue();
            BigDecimal planQty = planTotals.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (demandQty.compareTo(planQty) != 0) {
                mismatches.add(new ConservationMismatch(entry.getKey(), demandQty, planQty));
            }
        }
        // A v4 fact or pending group without a corresponding demand is also excess production.
        // Keep historical rules on their original validation behavior.
        if (rolling) {
            for (Map.Entry<String, BigDecimal> entry : planTotals.entrySet()) {
                if (!demandTotals.containsKey(entry.getKey()) && entry.getValue().signum() != 0) {
                    mismatches.add(new ConservationMismatch(entry.getKey(), BigDecimal.ZERO, entry.getValue()));
                }
            }
        }

        return new ConservationCheckResult(demandTotals.size(), mismatches.size(), mismatches);
    }

    /** Projects overrides, including a zero-system virtual cell on a previously empty target date. */
    public List<MrpDailyPlanDetail> applyOverrides(Long batchId, List<MrpDailyPlanDetail> plans) {
        if (plans == null || plans.isEmpty()) return plans == null ? List.of() : plans;
        var overrides = overrideMapper.selectByBatchId(batchId);
        List<MrpDailyPlanDetail> result = new ArrayList<>(plans);
        for (var plan : result) plan.setEffectiveQuantity(plan.getSystemQuantity());
        // Automatic batches need no identity maps or virtual-cell projection.
        if (overrides.isEmpty()) return result;
        boolean rolling = RollingPlanningQueryService.rolling(batchMapper.selectById(batchId));
        Map<String, MrpDailyPlanDetail> cells = new HashMap<>();
        Map<String, MrpDailyPlanDetail> groups = new HashMap<>();
        for (var plan : result) {
            cells.put(planOverrideKey(plan, plan.getScheduleDate()), plan);
            groups.put(planOverrideKey(plan, null), plan);
        }
        for (var override : overrides) {
            String group = overrideKey(override.getFactoryCode(), override.getRootMaterialCode(), override.getTerminalMaterialCode(),
                    override.getTerminalType(), override.getSupplyMode(), override.getPathGroupKey(), null);
            String key = overrideKey(override.getFactoryCode(), override.getRootMaterialCode(), override.getTerminalMaterialCode(),
                    override.getTerminalType(), override.getSupplyMode(), override.getPathGroupKey(), override.getScheduleDate());
            MrpDailyPlanDetail plan = cells.get(key);
            if (plan == null && groups.containsKey(group)) {
                var source = groups.get(group);
                plan = copyPlanToRun(source, batchId, source.getRunId());
                plan.setId(null);
                plan.setScheduleDate(override.getScheduleDate());
                plan.setSourceDate(rolling
                        ? override.getScheduleDate() : override.getScheduleDate().plusDays(7));
                plan.setSystemQuantity(BigDecimal.ZERO);
                plan.setIsProductionDay(true);
                plan.setRowKey(virtualRowKey(plan));
                result.add(plan); cells.put(key, plan);
            }
            if (plan != null) plan.setEffectiveQuantity(override.getManualQuantity());
        }
        return result;
    }

    private String planOverrideKey(MrpDailyPlanDetail p, LocalDate date) {
        return overrideKey(p.getFactoryCode(), p.getRootMaterialCode(), p.getTerminalMaterialCode(), p.getTerminalType(),
                p.getSupplyMode(), p.getPathGroupKey(), date);
    }
    private String virtualRowKey(MrpDailyPlanDetail p) {
        String raw = planOverrideKey(p, null) + p.getSourceMonth() + "|" + p.getScheduleDate();
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public record TransferRequest(String rowKey, LocalDate sourceDate, LocalDate targetDate,
            BigDecimal quantity, String reason, Long expectedRunId, BigDecimal expectedSourceQuantity,
            BigDecimal expectedTargetQuantity, Integer expectedSourceRevision, Integer expectedTargetRevision) {}

    @Transactional
    public void transfer(Long batchId, TransferRequest request) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (RollingPlanningQueryService.rolling(batch))
            throw new IllegalStateException("请使用整机与末级联动调整，先预览再确认");
        transferLinkedLine(batchId, request);
    }

    // Called by LinkedDailyTransferService inside its single outer transaction.
    void transferLinkedLine(Long batchId, TransferRequest request) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null || !("READY".equals(batch.getStatus()) || "WAITING_DECISION".equals(batch.getStatus())
                || "SCHEDULE_READY".equals(batch.getStatus())))
            throw new IllegalStateException("仅未发布计划可调整；已发布计划请生成修订版");
        if (!Objects.equals(batch.getActiveRunId(), request.expectedRunId())) throw new com.mrp.common.exception.ConcurrentChangeException("计划已重算，请刷新后重试");
        if (request.sourceDate() == null || request.targetDate() == null || request.sourceDate().equals(request.targetDate()))
            throw new IllegalArgumentException("请选择不同的转出和转入日期");
        if (request.quantity() == null || request.quantity().signum() <= 0 || request.quantity().stripTrailingZeros().scale() > 0 || request.quantity().precision() - request.quantity().scale() > 12)
            throw new IllegalArgumentException("转移数量须为正整数台/件，最多12位");
        if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 512)
            throw new IllegalArgumentException("请填写调整原因（最多512字）");
        for (LocalDate date : List.of(request.sourceDate(), request.targetDate())) {
            if (executionService.isDateLockedOrExecuted(batchId, date)) throw new IllegalStateException("锁定/已执行日期不可调整");
        }
        List<MrpDailyPlanDetail> effective = getDailyPlans(batchId);
        var source = effective.stream().filter(p -> request.sourceDate().equals(p.getScheduleDate()) &&
                Objects.equals(gridRowKey(p), request.rowKey())).findFirst().orElseThrow(() -> new IllegalArgumentException("转出需求不存在"));
        var targetCalendar = calendarDayMapper.selectByVersionId(batch.getProductionCalendarVersionId()).stream()
                .filter(d -> Objects.equals(source.getFactoryCode(), d.getFactoryCode()) && request.targetDate().equals(d.getCalendarDate()))
                .findFirst().orElse(null);
        if (targetCalendar == null || !Boolean.TRUE.equals(targetCalendar.getIsProductionDay()))
            throw new IllegalArgumentException("目标日期须为本工厂冻结日历中的生产日");
        var target = effective.stream().filter(p -> request.targetDate().equals(p.getScheduleDate()) &&
                Objects.equals(gridRowKey(p), request.rowKey())).findFirst().orElse(null);
        BigDecimal sourceQty = source.getEffectiveQuantity();
        BigDecimal targetQty = target == null ? BigDecimal.ZERO : target.getEffectiveQuantity();
        if (request.expectedSourceQuantity() == null || request.expectedTargetQuantity() == null
                || sourceQty.compareTo(request.expectedSourceQuantity()) != 0 || targetQty.compareTo(request.expectedTargetQuantity()) != 0)
            throw new com.mrp.common.exception.ConcurrentChangeException("排产数量已变化，请刷新后重试");
        if (sourceQty.compareTo(request.quantity()) < 0) throw new IllegalArgumentException("转移量超过转出日数量");
        if (targetQty.add(request.quantity()).precision() - targetQty.add(request.quantity()).scale() > 12)
            throw new IllegalArgumentException("调整后数量超过12位整数上限");
        MrpDailyPlanOverride from = transferOverride(source, request.sourceDate(), sourceQty.subtract(request.quantity()), request.reason());
        MrpDailyPlanOverride to = transferOverride(source, request.targetDate(), targetQty.add(request.quantity()), request.reason());
        writeTransferOverride(from, request.expectedSourceRevision());
        writeTransferOverride(to, request.expectedTargetRevision());
        refreshBatchGate(batchId);
        audit.log(com.mrp.common.security.CurrentUser.getUserId(), null, batch.getFactoryCode(), "TRANSFER", "mrp_daily_plan_override",
                batchId.toString(), Map.of("source", sourceQty, "target", targetQty), Map.of("request", request, "from", from, "to", to), null, null);
    }
    public static String gridRowKey(MrpDailyPlanDetail p) {
        return String.join("|", p.getFactoryCode(), p.getRootMaterialCode(), p.getTerminalMaterialCode(), p.getTerminalType(), p.getSupplyMode(), p.getPathGroupKey());
    }
    private MrpDailyPlanOverride transferOverride(MrpDailyPlanDetail p, LocalDate date, BigDecimal qty, String reason) {
        MrpDailyPlanOverride o = new MrpDailyPlanOverride();
        o.setBatchId(p.getBatchId()); o.setFactoryCode(p.getFactoryCode()); o.setRootMaterialCode(p.getRootMaterialCode());
        o.setTerminalMaterialCode(p.getTerminalMaterialCode()); o.setTerminalType(p.getTerminalType()); o.setSupplyMode(p.getSupplyMode());
        o.setPathGroupKey(p.getPathGroupKey()); o.setScheduleDate(date); o.setManualQuantity(qty); o.setReason(reason.trim());
        o.setOperatorId(com.mrp.common.security.CurrentUser.getUserId()); return o;
    }
    private void writeTransferOverride(MrpDailyPlanOverride o, Integer expectedRevision) {
        var existing = overrideMapper.selectByBusinessKey(o);
        int actualRevision = existing == null ? -1 : existing.getRevision();
        if (expectedRevision == null || actualRevision != expectedRevision) throw new com.mrp.common.exception.ConcurrentChangeException("排产已被修改，请刷新后重试");
        if (existing == null) { o.setRevision(0); overrideMapper.insert(o); }
        else { o.setId(existing.getId()); o.setRevision(actualRevision + 1); overrideMapper.update(o); }
    }

    private String overrideKey(String factory, String root, String terminal, String type, String supply,
                               String path, LocalDate date) {
        return String.join("|", Objects.toString(factory, ""), Objects.toString(root, ""),
                Objects.toString(terminal, ""), Objects.toString(type, ""), Objects.toString(supply, ""),
                Objects.toString(path, ""), Objects.toString(date, ""));
    }

    private String formatSourceMonth(Object sourceMonth) {
        if (sourceMonth instanceof LocalDate) {
            return ((LocalDate) sourceMonth).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return sourceMonth.toString();
    }

    public static class WeeklySummaryRow {
        public final String weekKey;
        public final LocalDate weekStart;
        public final LocalDate weekEnd;
        public final int planCount;
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        public final BigDecimal totalQuantity;
        public final String materialUnit, view, capacityBasis, ruleVersion, aggregationKey;

        public WeeklySummaryRow(String weekKey, LocalDate weekStart, LocalDate weekEnd,
                                int planCount, BigDecimal totalQuantity, String materialUnit, String view, String capacityBasis, String ruleVersion, String aggregationKey) {
            this.materialUnit = materialUnit; this.view = view; this.capacityBasis = capacityBasis; this.ruleVersion = ruleVersion; this.aggregationKey = aggregationKey;
            this.weekKey = weekKey;
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            this.planCount = planCount;
            this.totalQuantity = totalQuantity;
        }
    }

    public static class MonthlySummaryRow {
        public final String monthKey;
        public final int planCount;
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        public final BigDecimal totalQuantity;
        public final String materialUnit, view, capacityBasis, ruleVersion, aggregationKey;

        public MonthlySummaryRow(String monthKey, int planCount, BigDecimal totalQuantity, String materialUnit, String view, String capacityBasis, String ruleVersion, String aggregationKey) {
            this.materialUnit = materialUnit; this.view = view; this.capacityBasis = capacityBasis; this.ruleVersion = ruleVersion; this.aggregationKey = aggregationKey;
            this.monthKey = monthKey;
            this.planCount = planCount;
            this.totalQuantity = totalQuantity;
        }
    }

    public static class ConservationCheckResult {
        public final int totalDemands;
        public final int mismatchCount;
        public final List<ConservationMismatch> mismatches;

        public ConservationCheckResult(int totalDemands, int mismatchCount, List<ConservationMismatch> mismatches) {
            this.totalDemands = totalDemands;
            this.mismatchCount = mismatchCount;
            this.mismatches = mismatches;
        }
    }

    public static class ConservationMismatch {
        public final String demandKey;
        public final BigDecimal demandQuantity;
        public final BigDecimal planQuantity;

        public ConservationMismatch(String demandKey, BigDecimal demandQuantity, BigDecimal planQuantity) {
            this.demandKey = demandKey;
            this.demandQuantity = demandQuantity;
            this.planQuantity = planQuantity;
        }
    }
}
