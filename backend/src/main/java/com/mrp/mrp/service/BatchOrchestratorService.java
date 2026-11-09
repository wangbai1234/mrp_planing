package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.mrp.repository.*;
import com.mrp.mrp.repository.MrpDailyExecutionMapper;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(BatchOrchestratorService.class);

    private final ConfigSnapshotService configSnapshots;
    private final DailyCapacityService capacityService;
    private final com.mrp.audit.service.AuditService audit;
    private final MrpCalcBatchMapper batchMapper;
    private final MrpCalcRunMapper runMapper;
    private final MrpDecisionIssueMapper issueMapper;
    private final MrpCalculationService calculationService;
    private final DailyPlanningService dailyPlanningService;
    private final MrpDailyPlanDetailMapper dailyPlanMapper;
    private final MrpUnallocatedQuantityMapper unallocatedMapper;
    private final MrpInventoryPoolMapper poolMapper;
    private final BomSnapshotService bomSnapshotService;
    private final InventoryNormalizationService inventoryService;
    private final MrpInventoryBalanceMapper balanceMapper;
    private final CategoryRoleVersionService categoryRoleService;
    private final MaterialPolicyVersionService materialPolicyService;
    private final CalcTaskMapper calcTaskMapper;
    private final ObjectMapper objectMapper;
    private final ForecastMapper forecastMapper;
    private final MrpDailyExecutionMapper executionMapper;
    private final MrpRequirementDetailMapper requirementDetailMapper;
    private final MaterialPolicyNoticeService policyNotices;
    private final MrpTerminalDemandMapper terminalDemandMapper;
    private final AutomaticCapacityReviewService automaticCapacityReview;
    private final PlanningReleaseValidationService releaseValidation;
    private final MrpStockRiskMapper stockRisks;
    private final MrpInventoryAllocationMapper allocationMapper;
    private final MrpWorkbenchReviewService workbenchReviews;
    private final PlanningCalendarValidationService calendarValidation;

    public BatchOrchestratorService(
            MrpCalcBatchMapper batchMapper,
            MrpCalcRunMapper runMapper,
            MrpDecisionIssueMapper issueMapper,
            MrpCalculationService calculationService,
            DailyPlanningService dailyPlanningService,
            MrpDailyPlanDetailMapper dailyPlanMapper,
            MrpUnallocatedQuantityMapper unallocatedMapper,
            MrpInventoryPoolMapper poolMapper,
            BomSnapshotService bomSnapshotService,
            InventoryNormalizationService inventoryService,
            MrpInventoryBalanceMapper balanceMapper,
            CategoryRoleVersionService categoryRoleService,
            MaterialPolicyVersionService materialPolicyService,
            CalcTaskMapper calcTaskMapper,
            ObjectMapper objectMapper,
            ForecastMapper forecastMapper,
            MrpDailyExecutionMapper executionMapper,
            MrpRequirementDetailMapper requirementDetailMapper,
            MrpTerminalDemandMapper terminalDemandMapper, MaterialPolicyNoticeService policyNotices,
            ConfigSnapshotService configSnapshots, DailyCapacityService capacityService, com.mrp.audit.service.AuditService audit,
            AutomaticCapacityReviewService automaticCapacityReview, PlanningReleaseValidationService releaseValidation,
            MrpStockRiskMapper stockRisks, MrpInventoryAllocationMapper allocationMapper,
            MrpWorkbenchReviewService workbenchReviews, PlanningCalendarValidationService calendarValidation) {
        this.workbenchReviews = workbenchReviews;
        this.calendarValidation = calendarValidation;
        this.releaseValidation = releaseValidation; this.stockRisks = stockRisks;
        this.allocationMapper = allocationMapper;
        this.configSnapshots = configSnapshots; this.capacityService = capacityService; this.audit = audit;
        this.policyNotices = policyNotices;
        this.batchMapper = batchMapper;
        this.runMapper = runMapper;
        this.issueMapper = issueMapper;
        this.calculationService = calculationService;
        this.dailyPlanningService = dailyPlanningService;
        this.dailyPlanMapper = dailyPlanMapper;
        this.unallocatedMapper = unallocatedMapper;
        this.poolMapper = poolMapper;
        this.bomSnapshotService = bomSnapshotService;
        this.inventoryService = inventoryService;
        this.balanceMapper = balanceMapper;
        this.categoryRoleService = categoryRoleService;
        this.materialPolicyService = materialPolicyService;
        this.calcTaskMapper = calcTaskMapper;
        this.objectMapper = objectMapper;
        this.forecastMapper = forecastMapper;
        this.executionMapper = executionMapper;
        this.requirementDetailMapper = requirementDetailMapper;
        this.terminalDemandMapper = terminalDemandMapper;
        this.automaticCapacityReview = automaticCapacityReview;
    }

    @Transactional
    public MrpCalcBatch precheck(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        if (!"DRAFT".equals(batch.getStatus()) && !"REANALYZING".equals(batch.getStatus())) {
            throw new IllegalStateException("Batch not in DRAFT or REANALYZING status: " + batch.getStatus());
        }
        if (batch.getFactoryWarehouseMappingVersionId() == null
                || batch.getStockPolicyVersionId() == null
                || batch.getBusinessWeekVersionId() == null) {
            throw new IllegalStateException(
                    "该旧批次未锁定当前排产所需配置，请先放弃当前排产，再按当前规则重新创建批次");
        }
        var calendar = calendarValidation.check(batch, false);
        if (calendar.total() > 0) {
            String example = calendar.missingDays().isEmpty() ? String.join("、", calendar.configurationIssues())
                    : calendar.missingDays().getFirst().factoryCode() + " " + calendar.missingDays().getFirst().date();
            throw new IllegalStateException("生产日历或业务周缺失 " + calendar.total()
                    + " 项（" + example + "），请先维护配置，再重新生成排产");
        }

        batchMapper.updateStatusForce(batchId, "ANALYZING");

        List<BomSnapshotDetail> edges = bomSnapshotService.edgesForBatch(batch);
        boolean scheduleStage = ProductionCapacityRules.VERSION.equals(batch.getRuleVersion());

        List<MrpDecisionIssue> generatedIssues = new ArrayList<>();
        List<MrpInventoryAllocation> existingAllocations = allocationMapper.selectByBatchId(batchId);
        Map<String, MrpInventoryAllocation> manualAllocations = existingAllocations.stream()
                .filter(allocation -> "MANUAL_OVERRIDE".equals(allocation.getAllocationType()))
                .collect(Collectors.toMap(MrpInventoryAllocation::getDemandKey, allocation -> allocation,
                        (left, right) -> left, LinkedHashMap::new));
        // Rebuild the automatic preview on every precheck while retaining explicit business overrides.
        allocationMapper.deleteAutoByBatchId(batchId);
        poolMapper.rebuildAllocatedQuantitiesByBatchId(batchId);
        List<FactoryWarehouseMappingDetail> frozenWarehouses = batchMapper.selectFactoryWarehousesByBatchId(batchId);
        Map<String, List<MrpInventoryPool>> poolsByFactoryMaterial = orderedGoodPools(
                frozenWarehouses, inventoryService.getPoolsByBatch(batchId));
        List<MrpInventoryAllocation> generatedAllocations = new ArrayList<>();
        List<ForecastDetail> forecast = forecastMapper.selectDetailsByVersionId(batch.getForecastVersionId());
        Map<String, MaterialPolicyDetail> materialPolicyMap = materialPolicyService.getDetailMap(batch.getMaterialPolicyVersionId());
        for (String factory : batchFactories(batch)) {
            List<NetRequirementEngine.RootDemand> roots = rootDemandsForFactory(forecast, factory);
            // A factory with no imported demand contributes no output; its calendar/warehouse
            // still belongs to the unified version and is validated during creation.
            if (roots.isEmpty()) continue;
            List<MrpInventoryBalance> allBalances = new ArrayList<>();
            for (Long snapshotId : snapshotsForFactory(batch, factory)) allBalances.addAll(balanceMapper.selectAllBySnapshotId(snapshotId));
            Map<String, BigDecimal> inventoryMap = inventoryMapFromPools(poolsByFactoryMaterial, factory);
            Set<String> materialsWithInventory = allBalances.stream()
                    .filter(balance -> factory.equals(balance.getFactoryCode())
                            && !Boolean.FALSE.equals(balance.getIncludedFlag())
                            && !"DEFECTIVE".equals(balance.getInventoryQuality()))
                    .map(MrpInventoryBalance::getMaterialCode).collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> calculationMaterials = scheduleStage ? roots.stream()
                    .map(root -> root.materialCode).collect(Collectors.toCollection(LinkedHashSet::new))
                    : collectCalculationMaterials(edges, roots);
            Map<String, CategoryRoleDetail> roles = categoryRoleService.getMaterialRoleMap(
                    batch.getCategoryRoleVersionId(), calculationMaterials);
            Set<String> existingMaterials = categoryRoleService.getExistingMaterialCodes(calculationMaterials);
            List<MrpInventoryAllocation> factoryOverrides = manualAllocations.values().stream()
                    .filter(allocation -> {
                        MrpInventoryPool pool = inventoryService.getPoolById(allocation.getPoolId());
                        return pool != null && factory.equals(pool.getFactoryCode());
                    }).toList();
            NetRequirementEngine engine = new NetRequirementEngine(scheduleStage ? List.of() : edges, inventoryMap, roles,
                    materialPolicyMap, factoryOverrides, materialsWithInventory, factory, null, null, batchId, existingMaterials,
                    ProductionCapacityRules.VERSION.equals(batch.getRuleVersion()) && "ALL".equals(batch.getFactoryCode()));
            NetRequirementEngine.ExplosionResult result = scheduleStage ? engine.explodeRoots(roots) : engine.explode(roots);
            generatedIssues.addAll(result.issues);
            Map<String, Integer> priorityByRoot = roots.stream().collect(Collectors.toMap(
                    root -> root.materialCode + "|" + root.sourceMonth,
                    root -> root.prioritySeq == null ? 0 : root.prioritySeq,
                    Math::min, LinkedHashMap::new));
            collectAutomaticAllocations(result.details, factory, poolsByFactoryMaterial,
                    manualAllocations.keySet(), priorityByRoot, generatedAllocations);
        }
        if (!generatedAllocations.isEmpty()) allocationMapper.insertBatch(generatedAllocations);
        poolMapper.rebuildAllocatedQuantitiesByBatchId(batchId);
        if (batchFactories(batch).stream().allMatch(factory -> rootDemandsForFactory(forecast, factory).isEmpty())) {
            MrpDecisionIssue issue = new MrpDecisionIssue();
            issue.setBatchId(batchId); issue.setIssueKey("NO_FORECAST_DATA|" + batch.getForecastVersionId());
            issue.setIssueType("NO_FORECAST_DATA"); issue.setSeverity("BLOCKING");
            issue.setScopeType("BATCH"); issue.setScopeKey(String.valueOf(batch.getForecastVersionId()));
            issue.setStatus("OPEN"); issue.setRevision(0); generatedIssues.add(issue);
        }

        // Clear existing OPEN issues for idempotent re-precheck
        int existingOpen = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN").size();
        if (existingOpen > 0) {
            log.info("Clearing {} existing OPEN issues for re-precheck", existingOpen);
            issueMapper.deleteByBatchIdAndStatus(batchId, "OPEN");
        }

        Map<String, MrpDecisionIssue> uniqueIssues = new LinkedHashMap<>();
        for (MrpDecisionIssue issue : generatedIssues) {
            uniqueIssues.putIfAbsent(issue.getIssueKey(), issue);
        }
        int issueCount = uniqueIssues.size();
        int unresolvedCount = 0;

        for (MrpDecisionIssue issue : uniqueIssues.values()) {
            // Idempotent check: skip if issueKey already exists
            MrpDecisionIssue existing = issueMapper.selectByBatchIdAndKey(batchId, issue.getIssueKey());
            if (existing != null) {
                log.debug("Issue {} already exists for batch {}, skipping", issue.getIssueKey(), batchId);
                continue;
            }
            issue.setBatchId(batchId);
            issue.setStatus("OPEN");
            issue.setRevision(0);
            issue.setCreatedAt(LocalDateTime.now());
            issueMapper.insert(issue);
            unresolvedCount++;
        }

        batch = batchMapper.selectById(batchId);
        batch.setIssueCount(issueCount);
        batch.setUnresolvedCount(unresolvedCount);

        if (unresolvedCount > 0) {
            batch.setStatus("WAITING_DECISION");
        } else {
            batch.setStatus("READY_TO_CALCULATE");
        }
        batchMapper.update(batch);

        log.info("Precheck completed for batch {}: {} issues generated, {} unresolved", batchId, issueCount, unresolvedCount);
        return batch;
    }

    /** Sort only warehouses frozen into this batch; legacy null quality retains its historical behavior. */
    static Map<String, List<MrpInventoryPool>> orderedGoodPools(
            List<FactoryWarehouseMappingDetail> frozen, List<MrpInventoryPool> pools) {
        Map<String, FactoryWarehouseMappingDetail> mappings = new LinkedHashMap<>();
        for (FactoryWarehouseMappingDetail mapping : frozen) mappings.put(mapping.getWarehouseCode(), mapping);
        Map<String, List<MrpInventoryPool>> grouped = new LinkedHashMap<>();
        for (MrpInventoryPool pool : pools) {
            FactoryWarehouseMappingDetail mapping = mappings.get(pool.getWarehouseCode());
            if (mapping == null || !pool.getFactoryCode().equals(mapping.getFactoryCode())
                    || Boolean.FALSE.equals(mapping.getGoodWarehouse())) continue;
            grouped.computeIfAbsent(pool.getFactoryCode() + "|" + pool.getMaterialCode(), ignored -> new ArrayList<>()).add(pool);
        }
        for (List<MrpInventoryPool> candidates : grouped.values()) candidates.sort(
                Comparator.comparing((MrpInventoryPool pool) -> {
                    Integer priority = mappings.get(pool.getWarehouseCode()).getPriorityNo();
                    return priority == null ? 1 : 0;
                }).thenComparing(pool -> {
                    Integer priority = mappings.get(pool.getWarehouseCode()).getPriorityNo();
                    return priority == null ? Integer.MAX_VALUE : priority;
                }).thenComparingLong(pool -> {
                    FactoryWarehouseMappingDetail mapping = mappings.get(pool.getWarehouseCode());
                    return mapping.getMaintenanceOrder() == null ? Long.MAX_VALUE : mapping.getMaintenanceOrder();
                }).thenComparing(MrpInventoryPool::getWarehouseCode));
        return grouped;
    }

    private void collectAutomaticAllocations(List<MrpRequirementDetail> details, String factory,
                                              Map<String, List<MrpInventoryPool>> poolsByFactoryMaterial,
                                              Set<String> manualDemandKeys,
                                              Map<String, Integer> priorityByRoot,
                                              List<MrpInventoryAllocation> target) {
        record Key(Long poolId, String demandKey) {}
        Map<Key, MrpInventoryAllocation> aggregated = new LinkedHashMap<>();
        Map<Long, BigDecimal> remaining = new LinkedHashMap<>();
        for (var pools : poolsByFactoryMaterial.values()) for (MrpInventoryPool pool : pools)
            remaining.put(pool.getId(), pool.getAvailableQty().subtract(pool.getReservedQty() == null ? BigDecimal.ZERO : pool.getReservedQty())
                    .subtract(pool.getAllocatedQty() == null ? BigDecimal.ZERO : pool.getAllocatedQty()).max(BigDecimal.ZERO));
        for (MrpRequirementDetail detail : details) {
            if (detail.getInventoryUsedQty() == null || detail.getInventoryUsedQty().signum() <= 0) continue;
            String demandKey = detail.getRootMaterialCode() + "|" + detail.getCurrentMaterialCode() + "|" + detail.getSourceMonth();
            if (manualDemandKeys.contains(demandKey)) continue;
            BigDecimal needed = detail.getInventoryUsedQty();
            for (MrpInventoryPool pool : poolsByFactoryMaterial.getOrDefault(factory + "|" + detail.getCurrentMaterialCode(), List.of())) {
                BigDecimal available = remaining.getOrDefault(pool.getId(), BigDecimal.ZERO);
                BigDecimal taken = needed.min(available);
                if (taken.signum() <= 0) continue;
                remaining.put(pool.getId(), available.subtract(taken));
                Key key = new Key(pool.getId(), demandKey);
                MrpInventoryAllocation allocation = aggregated.computeIfAbsent(key, ignored -> {
                MrpInventoryAllocation value = new MrpInventoryAllocation();
                value.setPoolId(pool.getId());
                value.setDemandKey(demandKey);
                value.setRootMaterialCode(detail.getRootMaterialCode());
                value.setSourceMonth(detail.getSourceMonth());
                value.setPrioritySeq(priorityByRoot.getOrDefault(
                        detail.getRootMaterialCode() + "|" + detail.getSourceMonth(), 0));
                value.setPathHash(detail.getPathHash());
                value.setAllocationType("AUTO_PRIORITY");
                value.setManualConfirmed(false);
                value.setRevision(0);
                return value;
            });
                allocation.setGrossQty((allocation.getGrossQty() == null ? BigDecimal.ZERO : allocation.getGrossQty()).add(detail.getGrossQty()));
                allocation.setAllocatedQty((allocation.getAllocatedQty() == null ? BigDecimal.ZERO : allocation.getAllocatedQty()).add(taken));
                needed = needed.subtract(taken);
                if (needed.signum() == 0) break;
            }
            if (needed.signum() > 0) throw new IllegalStateException("冻结库存池无法覆盖已扣库存: " + factory + "/" + detail.getCurrentMaterialCode());
        }
        target.addAll(aggregated.values());
    }

    private Set<String> collectCalculationMaterials(List<BomSnapshotDetail> edges,
                                                     List<NetRequirementEngine.RootDemand> roots) {
        Set<String> materials = new LinkedHashSet<>();
        for (NetRequirementEngine.RootDemand root : roots) materials.add(root.materialCode);
        for (BomSnapshotDetail edge : edges) {
            materials.add(edge.getParentCode());
            materials.add(edge.getChildCode());
        }
        return materials;
    }

    private List<NetRequirementEngine.RootDemand> loadRootDemandsFromForecast(MrpCalcBatch batch) {
        List<ForecastDetail> forecast = forecastMapper.selectDetailsByVersionId(batch.getForecastVersionId());
        List<NetRequirementEngine.RootDemand> roots = new ArrayList<>();
        for (String factory : batchFactories(batch)) roots.addAll(rootDemandsForFactory(forecast, factory));
        return roots;
    }

    /** Stable sort by source month preserves frozen mapper import priority for ties. */
    static List<NetRequirementEngine.RootDemand> rootDemandsForFactory(List<ForecastDetail> rows, String factory) {
        if (rows == null) return List.of();
        Map<String, ForecastAggregate> aggregated = new LinkedHashMap<>();
        rows.stream().filter(row -> factory.equals(row.factoryCode()) && row.materialId() != null
                        && row.forecastQty() != null && row.planMonth() != null)
                .sorted(Comparator.comparing(ForecastDetail::planMonth)
                        .thenComparingInt(BatchOrchestratorService::effectivePriority)
                        .thenComparing(ForecastDetail::materialId)
                        .thenComparing(detail -> detail.id() == null ? Long.MAX_VALUE : detail.id()))
                .forEach(row -> {
                    String key = row.materialId() + "|" + row.planMonth().withDayOfMonth(1);
                    ForecastAggregate current = aggregated.get(key);
                    int priority = effectivePriority(row);
                    if (current == null) aggregated.put(key, new ForecastAggregate(row.forecastQty(), priority));
                    else aggregated.put(key, new ForecastAggregate(current.quantity().add(row.forecastQty()),
                            Math.min(current.prioritySeq(), priority)));
                });
        List<NetRequirementEngine.RootDemand> roots = new ArrayList<>();
        for (var entry : aggregated.entrySet()) {
            if (entry.getValue().quantity().signum() <= 0) continue;
            String[] key = entry.getKey().split("\\|", 2);
            roots.add(new NetRequirementEngine.RootDemand(key[0], entry.getValue().quantity(),
                    LocalDate.parse(key[1]), entry.getValue().prioritySeq()));
        }
        roots.sort(Comparator.comparing((NetRequirementEngine.RootDemand root) -> root.sourceMonth)
                .thenComparingInt(root -> root.prioritySeq == null ? 0 : root.prioritySeq)
                .thenComparing(root -> root.materialCode)
                .thenComparing(root -> root.materialCode + "|" + root.sourceMonth));
        return roots;
    }

    private static int effectivePriority(ForecastDetail row) {
        return row.prioritySeq() != null && row.prioritySeq() > 0
                ? row.prioritySeq()
                : row.id() != null && row.id() <= Integer.MAX_VALUE ? row.id().intValue() : Integer.MAX_VALUE;
    }

    private record ForecastAggregate(BigDecimal quantity, int prioritySeq) {}

    @Transactional
    public CalcTask submitCalculation(Long batchId, Long operatorId) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        if (!"READY_TO_CALCULATE".equals(batch.getStatus())) {
            throw new IllegalStateException("Batch not ready for calculation: " + batch.getStatus());
        }

        List<MrpDecisionIssue> openIssues = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN");
        if (!openIssues.isEmpty()) {
            throw new IllegalStateException("Cannot calculate: " + openIssues.size() + " unresolved issues");
        }
        // Recheck after business decisions: a new NC sync invalidates this batch.
        bomSnapshotService.edgesForBatch(batch);

        List<MrpUnallocatedQuantity> pendingUnallocated = unallocatedMapper.selectByBatchIdAndStatus(batchId, "PENDING");
        if (!pendingUnallocated.isEmpty()) {
            throw new IllegalStateException("Cannot calculate: " + pendingUnallocated.size() + " pending unallocated quantities");
        }

        // Load root demands from batch's forecast version (not from request)
        List<NetRequirementEngine.RootDemand> rootDemands = loadRootDemandsFromForecast(batch);
        if (rootDemands.isEmpty()) {
            throw new IllegalStateException("No root demands found for forecast version " + batch.getForecastVersionId());
        }

        batchMapper.updateStatusForce(batchId, "CALCULATING");

        // Build full input payload with all version IDs from batch (not from request)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", batchId);
        payload.put("bomSnapshotId", batch.getBomSnapshotId());
        payload.put("inventorySnapshotId", batch.getInventorySnapshotId());
        payload.put("forecastVersionId", batch.getForecastVersionId());
        payload.put("categoryRoleVersionId", batch.getCategoryRoleVersionId());
        payload.put("materialPolicyVersionId", batch.getMaterialPolicyVersionId());
        payload.put("productionCalendarVersionId", batch.getProductionCalendarVersionId());
        payload.put("rootDemands", rootDemands);

        String inputPayloadJson;
        try {
            inputPayloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize input payload", e);
        }

        CalcTask task = new CalcTask();
        task.setTaskType("MRP_CALCULATION");
        task.setBusinessScope("mrp");
        task.setRequestKey("mrp-calc-" + batchId + "-" + System.currentTimeMillis());
        task.setStatus(CalcTask.STATUS_PENDING);
        task.setPriority(5);
        task.setProgressCurrent(0);
        task.setProgressTotal(100);
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setInputPayload(inputPayloadJson);
        task.setInputChecksum(batch.getInputChecksum());
        task.setPhase("QUEUED");
        task.setSuccessRoots(0);
        task.setFailedRoots(0);
        task.setCreatedBy(operatorId);
        task.setCreatedAt(Instant.now());
        task.setVersion(0);
        calcTaskMapper.insert(task);

        log.info("Calculation task {} submitted for batch {} (async)", task.getId(), batchId);
        return task;
    }

    /** The material controller starts only after the planner confirms the active machine facts. */
    @Transactional
    public CalcTask submitMaterialAnalysis(Long batchId, Long operatorId) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null || !ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())
                || !Set.of("SCHEDULE_READY", "MATERIAL_RECALC_REQUIRED").contains(batch.getStatus())
                || batch.getActiveRunId() == null)
            throw new IllegalStateException("请先完成整机排产计算");
        MrpWorkbenchReviewService.Status review = workbenchReviews.status(batchId);
        if (!"CONFIRMED".equals(review.schedule()))
            throw new IllegalStateException("请先确认当前整机排产");
        MrpCalcRun run = runMapper.selectById(batch.getActiveRunId());
        if (run == null || !Set.of("SCHEDULE_READY", "COMPLETED", "COMPLETED_WITH_ISSUES").contains(run.getStatus()))
            throw new IllegalStateException("整机计算结果不可用于物料分析");
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("batchId", batchId);
        payload.put("runId", batch.getActiveRunId());
        payload.put("materialRecalculation", "MATERIAL_RECALC_REQUIRED".equals(batch.getStatus()));
        payload.put("scheduleFingerprint", review.scheduleFingerprint());
        payload.put("bomSnapshotId", batch.getBomSnapshotId());
        payload.put("inventorySnapshotId", batch.getInventorySnapshotId());
        payload.put("rootDemands", loadRootDemandsFromForecast(batch));
        CalcTask task = new CalcTask();
        task.setTaskType("MRP_MATERIAL_ANALYSIS"); task.setBusinessScope("mrp");
        task.setRequestKey("mrp-material-" + batchId + "-" + System.currentTimeMillis());
        task.setStatus(CalcTask.STATUS_PENDING); task.setPriority(5);
        task.setProgressCurrent(0); task.setProgressTotal(100);
        task.setAttemptCount(0); task.setMaxAttempts(3); task.setPhase("QUEUED");
        task.setSuccessRoots(0); task.setFailedRoots(0); task.setCreatedBy(operatorId);
        task.setCreatedAt(Instant.now()); task.setVersion(0);
        task.setInputChecksum(batch.getInputChecksum());
        try { task.setInputPayload(objectMapper.writeValueAsString(payload)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("无法提交物料分析任务", error); }
        batchMapper.updateStatusForce(batchId, "CALCULATING");
        calcTaskMapper.insert(task);
        return task;
    }

    @Transactional
    public MrpCalcBatch completeDistribution(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        if (!"WAITING_DAILY_DISTRIBUTION".equals(batch.getStatus())) {
            throw new IllegalStateException("Batch not in WAITING_DAILY_DISTRIBUTION status: " + batch.getStatus());
        }

        Long calendarVersionId = batch.getProductionCalendarVersionId();
        Long materialPolicyVersionId = batch.getMaterialPolicyVersionId();
        Long runId = batch.getActiveRunId();

        if (calendarVersionId == null) {
            throw new IllegalStateException("Batch has no production calendar version");
        }
        if (runId == null) {
            throw new IllegalStateException("Batch has no active run");
        }

        // The legacy direct endpoint may already have atomically allocated this run.
        // Reuse those results and apply the normal completion gates without appending facts.
        if (!dailyPlanMapper.hasDistributionResult(runId)) {
            dailyPlanningService.distribute(batchId, runId, calendarVersionId, materialPolicyVersionId);
        }

        List<MrpDecisionIssue> openIssues = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN");
        List<MrpUnallocatedQuantity> pendingUnallocated =
                unallocatedMapper.selectByRunIdAndStatus(runId, "PENDING");
        DailyPlanningService.ConservationCheckResult conservation =
                dailyPlanningService.getSourceMonthConservation(batchId);
        if (!openIssues.isEmpty() || !pendingUnallocated.isEmpty() || conservation.mismatchCount > 0) {
            batchMapper.updateStatusForce(batchId, "WAITING_DECISION");
        } else {
            batchMapper.updateStatusForce(batchId, "READY");
        }
        batch = batchMapper.selectById(batchId);

        log.info("Distribution completed for batch {}, status now {}", batchId, batch.getStatus());
        return batch;
    }

    @Transactional
    public MrpCalcBatch publish(Long batchId) { return publish(batchId, null); }

    @Transactional
    public MrpCalcBatch publish(Long batchId, String capacityFingerprint) {
        return publish(batchId, capacityFingerprint, com.mrp.common.security.CurrentUser.getUserId());
    }

    @Transactional
    public MrpCalcBatch publish(Long batchId, String capacityFingerprint, Long operatorId) {
        return publish(batchId, capacityFingerprint, operatorId,
                com.mrp.mrp.engine.CalculationProgressListener.NONE);
    }

    @Transactional
    public MrpCalcBatch publish(Long batchId, String capacityFingerprint, Long operatorId,
            com.mrp.mrp.engine.CalculationProgressListener progress) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        if (!"READY".equals(batch.getStatus())) {
            throw new IllegalStateException("Batch not in READY status: " + batch.getStatus());
        }
        workbenchReviews.requireConfirmed(batch);
        bomSnapshotService.edgesForBatch(batch);

        List<MrpDecisionIssue> openIssues = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN");
        if (!openIssues.isEmpty()) {
            throw new IllegalStateException("Cannot publish: " + openIssues.size() + " unresolved issues");
        }

        Long activeRunId = batch.getActiveRunId();
        if (activeRunId == null) {
            throw new IllegalStateException("Cannot publish: batch has no active run");
        }

        List<MrpUnallocatedQuantity> pendingUnallocated =
                unallocatedMapper.selectByRunIdAndStatus(activeRunId, "PENDING");
        if (!pendingUnallocated.isEmpty()) {
            throw new IllegalStateException("Cannot publish: " + pendingUnallocated.size() + " pending unallocated quantities");
        }

        releaseValidation.requirePublishable(batch);
        progress.report(6, "读取排产与人工调整");
        List<MrpDailyPlanDetail> effectivePlans = dailyPlanningService.getDailyPlans(batchId);
        progress.report(12, "校验守恒与日产能");
        DailyPlanningService.ConservationCheckResult conservation =
                dailyPlanningService.getSourceMonthConservation(batchId, activeRunId, effectivePlans);
        if (conservation.totalDemands == 0 || conservation.mismatchCount > 0) {
            throw new IllegalStateException("Cannot publish: source-month conservation check failed");
        }

        String resultChecksum = effectiveChecksum(effectivePlans);
        CapacityReview review = capacityReview(batch, effectivePlans, resultChecksum);
        if (review.loads().stream().anyMatch(d -> !d.configured() && d.load().signum() > 0))
            throw new IllegalStateException("无法发布：实际排产日期的生产日历未配置，请维护日历后重新生成排产");
        boolean warnings = review.loads().stream().anyMatch(d -> !"NORMAL".equals(d.status()));
        if (capacityFingerprint != null && !Objects.equals(review.fingerprint(), capacityFingerprint))
            throw new com.mrp.common.exception.ConcurrentChangeException("排产或日产能预警已变化，请重新确认后发布");
        if (warnings && capacityFingerprint == null)
            throw new IllegalStateException("请查看并确认当前日产能预警后发布；计划变化后需重新确认");
        if (warnings) audit.log(operatorId, null, batch.getFactoryCode(),
                "CAPACITY_ACKNOWLEDGE", "mrp_calc_batch", batchId.toString(), null, review, null, null);

        // Create published version batch (independent copy)
        MrpCalcBatch published = new MrpCalcBatch();
        published.setFactoryCode(batch.getFactoryCode());
        published.setStatus("PUBLISHED");
        published.setForecastVersionId(batch.getForecastVersionId());
        published.setInventorySnapshotId(batch.getInventorySnapshotId());
        published.setBomSnapshotId(batch.getBomSnapshotId());
        published.setBomSourceChecksum(batch.getBomSourceChecksum());
        published.setCategoryRoleVersionId(batch.getCategoryRoleVersionId());
        published.setMaterialPolicyVersionId(batch.getMaterialPolicyVersionId());
        published.setProductionCalendarVersionId(batch.getProductionCalendarVersionId());
        published.setFactoryWarehouseMappingVersionId(batch.getFactoryWarehouseMappingVersionId());
        published.setStockPolicyVersionId(batch.getStockPolicyVersionId());
        published.setBusinessWeekVersionId(batch.getBusinessWeekVersionId());
        published.setInventorySnapshotGroupId(batch.getInventorySnapshotGroupId());
        published.setDecisionChecksum(batch.getDecisionChecksum());
        published.setRuleVersion(batch.getRuleVersion());
        published.setInputChecksum(batch.getInputChecksum());
        published.setResultChecksum(resultChecksum);
        published.setVersionNo(batch.getVersionNo());
        published.setPublishedAt(LocalDateTime.now());
        published.setActiveRequestKey(batch.getActiveRequestKey());
        published.setRevisionOfBatchId(batchId);
        published.setRevisionNo(batch.getRevisionNo() + 1);
        published.setIssueCount(batch.getIssueCount());
        published.setUnresolvedCount(0);
        published.setVersion(0);
        published.setCreatedBy(batch.getCreatedBy());
        published.setCreatedAt(LocalDateTime.now());
        published.setUpdatedAt(LocalDateTime.now());
        batchMapper.insert(published);
        batchMapper.copyFactoryScopes(batchId, published.getId());
        batchMapper.copyBusinessWeekScopes(batchId, published.getId());

        // Create independent run for published version
        MrpCalcRun publishedRun = new MrpCalcRun();
        publishedRun.setBatchId(published.getId());
        publishedRun.setStatus("PUBLISHED");
        publishedRun.setStartedAt(LocalDateTime.now());
        publishedRun.setFinishedAt(LocalDateTime.now());
        runMapper.insert(publishedRun);
        published.setActiveRunId(publishedRun.getId());
        batchMapper.update(published);
        if (ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())) {
            workbenchReviews.copyPublishedConfirmations(batchId, published.getId(), publishedRun.getId());
        }

        // Close the working batch after publication. It must never be publishable or editable again.
        batch.setStatus("SUPERSEDED");
        batch.setSupersededByBatchId(published.getId());
        batchMapper.update(batch);

        // Copy the complete calculation facts. A published batch must remain independently
        // traceable even when the source batch is later revised or recalculated.
        progress.report(20, "复制发布版本明细");
        Long sourceRunId = batch.getActiveRunId();
        if (sourceRunId != null) {
            policyNotices.copyRun(sourceRunId, published.getId(), publishedRun.getId());
            stockRisks.copyRun(sourceRunId, published.getId(), publishedRun.getId());
            List<MrpRequirementDetail> sourceRequirements = requirementDetailMapper.selectByRunId(sourceRunId);
            if (!sourceRequirements.isEmpty()) {
                copyInChunks(sourceRequirements,
                        item -> copyRequirement(item, published.getId(), publishedRun.getId()), requirementDetailMapper::insertBatch,
                        completed -> progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(20, 45, completed, sourceRequirements.size()),
                                "复制物料需求", completed, sourceRequirements.size()));
            }

            List<MrpTerminalDemand> sourceDemands = terminalDemandMapper.selectByRunId(sourceRunId);
            if (!sourceDemands.isEmpty()) {
                copyInChunks(sourceDemands,
                        item -> copyTerminalDemand(item, published.getId(), publishedRun.getId()), terminalDemandMapper::insertBatch,
                        completed -> progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(45, 55, completed, sourceDemands.size()),
                                "复制末级需求", completed, sourceDemands.size()));
            }

            List<MrpDailyPlanDetail> sourcePlans = effectivePlans;
            if (!sourcePlans.isEmpty()) {
                copyInChunks(sourcePlans, plan -> {
                    MrpDailyPlanDetail copy = new MrpDailyPlanDetail();
                    copy.setRunId(publishedRun.getId());
                    copy.setBatchId(published.getId());
                    copy.setFactoryCode(plan.getFactoryCode());
                    copy.setRootMaterialCode(plan.getRootMaterialCode());
                    copy.setTerminalMaterialCode(plan.getTerminalMaterialCode());
                    copy.setTerminalType(plan.getTerminalType());
                    copy.setSupplyMode(plan.getSupplyMode());
                    copy.setPathGroupKey(plan.getPathGroupKey());
                    copy.setSourceMonth(plan.getSourceMonth());
                    copy.setSourceDate(plan.getSourceDate());
                    copy.setScheduleDate(plan.getScheduleDate());
                    // Freeze the effective value as the published system fact. Overrides stay
                    // on the working batch and cannot mutate the published version afterwards.
                    copy.setSystemQuantity(plan.getEffectiveQuantity());
                    copy.setIsProductionDay(plan.getIsProductionDay());
                    copy.setRowKey(plan.getRowKey());
                    copy.setRevision(0);
                    return copy;
                }, dailyPlanMapper::insertBatch,
                        completed -> progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(55, 94, completed, sourcePlans.size()),
                                "复制日排产", completed, sourcePlans.size()));
                log.info("Copied {} daily plans from batch {} run {} to published batch {} run {}",
                        sourcePlans.size(), batchId, sourceRunId, published.getId(), publishedRun.getId());
            }
        }
        progress.report(95, "复制已锁定与已执行记录");
        copyExecutionFacts(batchId, published.getId());
        progress.report(99, "确认发布版本");

        log.info("Batch {} published as revision {} (new batch {})", batchId, published.getRevisionNo(), published.getId());
        return published;
    }

    /** Keep every chunk inside the outer publish transaction; never commit a partial version. */
    private static <T> void copyInChunks(List<T> rows, java.util.function.Function<T, T> copy,
                                        java.util.function.Function<List<T>, Integer> writer,
                                        java.util.function.IntConsumer completed) {
        for (int start = 0; start < rows.size(); start += 1000) {
            int end = Math.min(start + 1000, rows.size());
            writer.apply(rows.subList(start, end).stream().map(copy).toList());
            completed.accept(end);
        }
    }

    private MrpRequirementDetail copyRequirement(MrpRequirementDetail source, Long batchId, Long runId) {
        MrpRequirementDetail copy = new MrpRequirementDetail();
        copy.setRunId(runId);
        copy.setBatchId(batchId);
        copy.setFactoryCode(source.getFactoryCode());
        copy.setRootMaterialCode(source.getRootMaterialCode());
        copy.setSourceMonth(source.getSourceMonth());
        copy.setParentMaterialCode(source.getParentMaterialCode());
        copy.setCurrentMaterialCode(source.getCurrentMaterialCode());
        copy.setLevelNo(source.getLevelNo());
        copy.setPathText(source.getPathText());
        copy.setPathHash(source.getPathHash());
        copy.setCategoryRole(source.getCategoryRole());
        copy.setSupplyMode(source.getSupplyMode());
        copy.setExplosionPolicy(source.getExplosionPolicy());
        copy.setEffectiveExplosionPolicy(source.getEffectiveExplosionPolicy());
        copy.setPolicySource(source.getPolicySource());
        copy.setGrossQty(source.getGrossQty());
        copy.setInventoryBeforeQty(source.getInventoryBeforeQty());
        copy.setInventoryUsedQty(source.getInventoryUsedQty());
        copy.setInventoryAfterQty(source.getInventoryAfterQty());
        copy.setNetQty(source.getNetQty());
        copy.setTerminalFlag(source.getTerminalFlag());
        copy.setTerminalReason(source.getTerminalReason());
        return copy;
    }

    private MrpTerminalDemand copyTerminalDemand(MrpTerminalDemand source, Long batchId, Long runId) {
        MrpTerminalDemand copy = new MrpTerminalDemand();
        copy.setRunId(runId);
        copy.setBatchId(batchId);
        copy.setFactoryCode(source.getFactoryCode());
        copy.setRootMaterialCode(source.getRootMaterialCode());
        copy.setTerminalMaterialCode(source.getTerminalMaterialCode());
        copy.setTerminalType(source.getTerminalType());
        copy.setSupplyMode(source.getSupplyMode());
        copy.setSourceMonth(source.getSourceMonth());
        copy.setQuantity(source.getQuantity());
        return copy;
    }

    public MrpCalcBatch getBatch(Long batchId) {
        return batchMapper.selectById(batchId);
    }

    public List<MrpCalcBatch> getBatchesByStatus(String status) {
        if (status == null) {
            return batchMapper.selectAll();
        }
        return batchMapper.selectByStatus(status);
    }

    @Transactional
    public MrpCalcBatch cancel(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        Set<String> cancellable = Set.of("DRAFT", "WAITING_DECISION", "READY_TO_CALCULATE", "SCHEDULE_READY", "MATERIAL_REVIEW", "READY");
        if (!cancellable.contains(batch.getStatus())) {
            throw new IllegalStateException("当前状态不允许作废: " + batch.getStatus());
        }
        batchMapper.updateStatusForce(batchId, "CANCELLED");
        batch.setStatus("CANCELLED");
        return batch;
    }

    @Transactional
    public MrpCalcBatch revise(Long batchId) {
        return revise(batchId, com.mrp.common.security.CurrentUser.getUserId());
    }

    @Transactional
    public MrpCalcBatch revise(Long batchId, Long operatorId) {
        return revise(batchId, operatorId, com.mrp.mrp.engine.CalculationProgressListener.NONE);
    }

    @Transactional
    public MrpCalcBatch revise(Long batchId, Long operatorId,
            com.mrp.mrp.engine.CalculationProgressListener progress) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        if (!"PUBLISHED".equals(batch.getStatus())) {
            throw new IllegalStateException("Only PUBLISHED batches can be revised: " + batch.getStatus());
        }
        bomSnapshotService.edgesForBatch(batch);

        progress.report(10, "检查来源月份与业务周");
        List<LocalDate> sourceMonths = forecastMapper.selectDetailsByVersionId(batch.getForecastVersionId()).stream()
                .filter(detail -> batchFactories(batch).contains(detail.factoryCode()) && detail.planMonth() != null)
                .map(detail -> detail.planMonth().withDayOfMonth(1)).distinct().toList();
        Set<LocalDate> frozenMonths = new HashSet<>(batchMapper.selectBusinessWeekMonthsByBatchId(batchId));
        Set<LocalDate> maintainedMonths = new HashSet<>(configSnapshots.businessMonths());
        List<LocalDate> requiredMonths = configSnapshots.rollingBusinessMonths(sourceMonths);
        if (!maintainedMonths.containsAll(requiredMonths)) throw new IllegalStateException("请先维护来源月及上月的业务周期间后创建修订");
        List<LocalDate> missingMonths = requiredMonths.stream().filter(month -> !frozenMonths.contains(month)).toList();
        progress.report(30, "锁定新增业务周与生产日历");
        Map<LocalDate, Long> addedWeekVersions = configSnapshots.snapshotBusinessWeeks(missingMonths,
                operatorId);

        // Create revision batch (independent)
        MrpCalcBatch revision = new MrpCalcBatch();
        revision.setFactoryCode(batch.getFactoryCode());
        revision.setStatus("DRAFT");
        revision.setForecastVersionId(batch.getForecastVersionId());
        revision.setInventorySnapshotId(batch.getInventorySnapshotId());
        revision.setBomSnapshotId(batch.getBomSnapshotId());
        revision.setBomSourceChecksum(batch.getBomSourceChecksum());
        revision.setCategoryRoleVersionId(batch.getCategoryRoleVersionId());
        revision.setMaterialPolicyVersionId(batch.getMaterialPolicyVersionId());
        revision.setProductionCalendarVersionId(configSnapshots.snapshotCalendar(batch.getFactoryCode(), operatorId));
        revision.setFactoryWarehouseMappingVersionId(batch.getFactoryWarehouseMappingVersionId());
        revision.setStockPolicyVersionId(batch.getStockPolicyVersionId());
        revision.setBusinessWeekVersionId(batch.getBusinessWeekVersionId());
        revision.setInventorySnapshotGroupId(batch.getInventorySnapshotGroupId());
        revision.setDecisionChecksum(batch.getDecisionChecksum());
        revision.setRuleVersion("v2-daily-capacity".equals(batch.getRuleVersion())
                ? ProductionCapacityRules.WHOLE_MACHINE_V3 : batch.getRuleVersion());
        revision.setInputChecksum(hashText(Objects.toString(batch.getInputChecksum(), "") + "|calendar=" + revision.getProductionCalendarVersionId()
                + "|rule=" + revision.getRuleVersion() + "|addedWeeks=" + addedWeekVersions));
        revision.setVersionNo(batch.getVersionNo());
        revision.setRevisionOfBatchId(batchId);
        revision.setRevisionNo(batch.getRevisionNo() + 1);
        revision.setIssueCount(0);
        revision.setUnresolvedCount(0);
        revision.setVersion(0);
        revision.setCreatedBy(batch.getCreatedBy());
        revision.setCreatedAt(LocalDateTime.now());
        revision.setUpdatedAt(LocalDateTime.now());
        batchMapper.insert(revision);
        batchMapper.copyFactoryScopes(batchId, revision.getId());
        batchMapper.updateFactoryScopeCalendar(revision.getId(), revision.getProductionCalendarVersionId());
        batchMapper.copyBusinessWeekScopes(batchId, revision.getId());
        for (Map.Entry<LocalDate, Long> entry : addedWeekVersions.entrySet()) {
            batchMapper.insertBusinessWeekScope(revision.getId(), entry.getKey(), entry.getValue());
        }
        // A revision is an independent working batch. Rebuild its inventory pools from the
        // locked snapshot and stock-policy version so allocation pages and recalculation do
        // not depend on the published batch's mutable allocation state.
        var factories = batchFactories(revision);
        int finishedFactories = 0;
        for (String factory : factories) {
            Map<Long, Set<String>> warehousesBySnapshot = new LinkedHashMap<>();
            for (var scope : batchMapper.selectFactoryWarehousesByBatchId(revision.getId())) {
                if (!factory.equals(scope.getFactoryCode())) continue;
                Long source = inventoryService.snapshotIdForWarehouse(revision.getInventorySnapshotGroupId(),
                        scope.getWarehouseCode(), revision.getInventorySnapshotId());
                warehousesBySnapshot.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(scope.getWarehouseCode());
            }
            for (var source : warehousesBySnapshot.entrySet()) inventoryService.createPoolsFromSnapshot(
                    revision.getId(), source.getKey(), factory, revision.getStockPolicyVersionId(), source.getValue());
            finishedFactories++;
            progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(55, 90, finishedFactories, factories.size()),
                    "重建工厂库存池", finishedFactories, factories.size());
        }

        batch.setSupersededByBatchId(revision.getId());
        batchMapper.update(batch);

        // Execution facts are copied independently. Daily-plan rows are rebuilt only after a
        // real calculation run exists; DailyPlanningService merges these immutable facts into
        // that run and redistributes only the remaining unlocked quantity.
        progress.report(95, "复制已锁定与已执行记录");
        copyExecutionFacts(batchId, revision.getId());
        progress.report(99, "确认修订版本");

        log.info("Created revision batch {} from published batch {}", revision.getId(), batchId);
        return revision;
    }

    private void copyExecutionFacts(Long sourceBatchId, Long targetBatchId) {
        List<MrpDailyExecution> sourceExecutions = executionMapper.selectByBatchId(sourceBatchId);
        List<MrpDailyPlanDetail> sourcePlans = dailyPlanMapper.selectByBatchId(sourceBatchId);
        if (sourcePlans == null) sourcePlans = Collections.emptyList();
        Map<String, BigDecimal> sourcePlanQuantities = sourcePlans.stream()
                .collect(Collectors.toMap(
                        plan -> plan.getRowKey() + "|" + plan.getScheduleDate(),
                        MrpDailyPlanDetail::getSystemQuantity,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (MrpDailyExecution source : sourceExecutions) {
            if (!"LOCKED".equals(source.getExecutionStatus())
                    && !"EXECUTED".equals(source.getExecutionStatus())) {
                continue;
            }
            MrpDailyExecution copy = new MrpDailyExecution();
            copy.setBatchId(targetBatchId);
            copy.setRowKey(source.getRowKey());
            copy.setScheduleDate(source.getScheduleDate());
            copy.setExecutionStatus(source.getExecutionStatus());
            copy.setActualQuantity(source.getActualQuantity());
            copy.setPlannedQuantity(source.getPlannedQuantity() != null
                    ? source.getPlannedQuantity()
                    : sourcePlanQuantities.get(source.getRowKey() + "|" + source.getScheduleDate()));
            copy.setConfirmedBy(source.getConfirmedBy());
            copy.setConfirmedAt(source.getConfirmedAt());
            copy.setRemark(source.getRemark());
            copy.setRevision(0);
            executionMapper.insert(copy);
        }
    }

    @Transactional
    public MrpCalcBatch transitionStatus(Long batchId, String expectedStatus, String newStatus) {
        MrpCalcBatch batch = batchMapper.selectForUpdate(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        if (!expectedStatus.equals(batch.getStatus())) {
            throw new IllegalStateException("Batch status is " + batch.getStatus() + ", expected " + expectedStatus);
        }
        if ("PUBLISHED".equals(newStatus)) {
            throw new IllegalStateException("请通过发布排产操作完成异常校验，不能直接切换为已发布状态");
        }
        batch.setStatus(newStatus);
        batchMapper.update(batch);
        return batch;
    }

    public BatchSummary getBatchSummary(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }

        var counts = batchMapper.selectSummaryCounts(batchId, batch.getActiveRunId());
        return new BatchSummary(
                batch.getId(), batch.getStatus(), batch.getFactoryCode(),
                counts.totalIssues(), counts.openIssues(),
                counts.dailyPlanCount(), counts.unallocatedCount(), counts.pendingUnallocated(),
                counts.poolCount(), counts.totalAvailable(), counts.totalAllocated(),
                batch.getResultChecksum(), batch.getPublishedAt());
    }

    public record CapacityReview(List<DailyCapacityService.DayLoad> loads, String fingerprint) {}
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public CapacityReview capacityReview(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null) throw new IllegalArgumentException("计划不存在");
        var projection = automaticCapacityReview.read(batch);
        if (projection != null) {
            var loads = projection.start() == null ? List.<DailyCapacityService.DayLoad>of()
                    : capacityService.evaluate(batch, projection.machineLoads(), projection.start(), projection.end());
            return capacityFingerprint(batch, loads, projection.checksum());
        }
        List<MrpDailyPlanDetail> plans = dailyPlanningService.getDailyPlans(batchId);
        return capacityReview(batch, plans, effectiveChecksum(plans));
    }

    private CapacityReview capacityReview(MrpCalcBatch batch, List<MrpDailyPlanDetail> plans, String checksum) {
        LocalDate start = plans.stream().map(MrpDailyPlanDetail::getScheduleDate).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate end = plans.stream().map(MrpDailyPlanDetail::getScheduleDate).max(LocalDate::compareTo).orElse(start);
        List<DailyCapacityService.DayLoad> loads = plans.isEmpty() ? List.of() : capacityService.evaluate(batch, plans, start, end);
        return capacityFingerprint(batch, loads, checksum);
    }

    private CapacityReview capacityFingerprint(MrpCalcBatch batch, List<DailyCapacityService.DayLoad> loads, String checksum) {
        Long batchId = batch.getId();
        try { return new CapacityReview(loads, hashText(batchId + "|" + batch.getActiveRunId() + "|" + batch.getRuleVersion() + "|" + batch.getProductionCalendarVersionId() + "|" + checksum + "|" + objectMapper.writeValueAsString(loads))); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private static String hashText(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** Stable complete identities and effective values, independent of database row order. */
    static String effectiveChecksum(List<MrpDailyPlanDetail> plans) {
        List<String> facts = new ArrayList<>();
        for (MrpDailyPlanDetail p : plans) facts.add(checksumFact(p));
        return checksumFacts(facts);
    }

    static String checksumFact(MrpDailyPlanDetail p) {
        StringBuilder fact = new StringBuilder();
        for (Object value : Arrays.asList(p.getFactoryCode(), p.getRootMaterialCode(), p.getTerminalMaterialCode(),
                p.getTerminalType(), p.getSupplyMode(), p.getPathGroupKey(), p.getSourceMonth(), p.getSourceDate(),
                p.getScheduleDate(), p.getIsProductionDay(), p.getEffectiveQuantity().stripTrailingZeros().toPlainString())) {
            String text = Objects.toString(value, "");
            fact.append(text.length()).append(':').append(text);
        }
        return fact.toString();
    }

    static String checksumFacts(List<String> facts) {
        Collections.sort(facts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Preserve the exact joined UTF-8 input without allocating a batch-sized string/byte array.
            for (int i = 0; i < facts.size(); i++) {
                if (i > 0) digest.update((byte) '\n');
                digest.update(facts.get(i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class BatchSummary {
        public final Long batchId;
        public final String status;
        public final String factoryCode;
        public final int totalIssues;
        public final int openIssues;
        public final int dailyPlanCount;
        public final int unallocatedCount;
        public final int pendingUnallocated;
        public final int poolCount;
        public final BigDecimal totalAvailable;
        public final BigDecimal totalAllocated;
        public final String resultChecksum;
        public final LocalDateTime publishedAt;

        public BatchSummary(Long batchId, String status, String factoryCode,
                           int totalIssues, int openIssues,
                           int dailyPlanCount, int unallocatedCount, int pendingUnallocated,
                           int poolCount, BigDecimal totalAvailable, BigDecimal totalAllocated,
                           String resultChecksum, LocalDateTime publishedAt) {
            this.batchId = batchId;
            this.status = status;
            this.factoryCode = factoryCode;
            this.totalIssues = totalIssues;
            this.openIssues = openIssues;
            this.dailyPlanCount = dailyPlanCount;
            this.unallocatedCount = unallocatedCount;
            this.pendingUnallocated = pendingUnallocated;
            this.poolCount = poolCount;
            this.totalAvailable = totalAvailable;
            this.totalAllocated = totalAllocated;
            this.resultChecksum = resultChecksum;
            this.publishedAt = publishedAt;
        }
    }
    private Long snapshotForFactory(MrpCalcBatch batch, String factory) {
        if (batch.getInventorySnapshotGroupId() == null) return batch.getInventorySnapshotId();
        String warehouse = batchMapper.selectFactoryWarehousesByBatchId(batch.getId()).stream()
                .filter(scope -> factory.equals(scope.getFactoryCode())).map(scope -> scope.getWarehouseCode())
                .findFirst().orElseThrow(() -> new IllegalStateException("冻结工厂范围缺少仓库: " + factory));
        return inventoryService.snapshotIdForWarehouse(batch.getInventorySnapshotGroupId(), warehouse, batch.getInventorySnapshotId());
    }

    private List<Long> snapshotsForFactory(MrpCalcBatch batch, String factory) {
        if (batch.getInventorySnapshotGroupId() == null) return List.of(batch.getInventorySnapshotId());
        List<Long> snapshots = batchMapper.selectFactoryWarehousesByBatchId(batch.getId()).stream()
                .filter(scope -> factory.equals(scope.getFactoryCode()))
                .map(scope -> inventoryService.snapshotIdForWarehouse(batch.getInventorySnapshotGroupId(),
                        scope.getWarehouseCode(), batch.getInventorySnapshotId())).distinct().toList();
        if (snapshots.isEmpty()) throw new IllegalStateException("冻结工厂范围缺少仓库: " + factory);
        return snapshots;
    }

    private Map<String, BigDecimal> inventoryMapFromPools(Map<String, List<MrpInventoryPool>> pools, String factory) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (var entry : pools.entrySet()) {
            if (!entry.getKey().startsWith(factory + "|")) continue;
            String material = entry.getKey().substring(factory.length() + 1);
            for (MrpInventoryPool pool : entry.getValue()) result.merge(material + "|GOOD",
                    pool.getAvailableQty().subtract(pool.getReservedQty() == null ? BigDecimal.ZERO : pool.getReservedQty()).max(BigDecimal.ZERO), BigDecimal::add);
        }
        return result;
    }

    private List<String> batchFactories(MrpCalcBatch batch) {
        List<String> frozen = batchMapper.selectFactoryCodesByBatchId(batch.getId());
        return frozen.isEmpty() ? ProductionCapacityRules.factories(batch) : frozen;
    }

}
