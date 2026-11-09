package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.mrp.repository.*;
import com.mrp.common.response.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MrpCalculationService {

    private static final Logger log = LoggerFactory.getLogger(MrpCalculationService.class);
    private static final int WRITE_BATCH_SIZE = 1000;

    private final MrpCalcBatchMapper batchMapper;
    private final MrpCalcRunMapper runMapper;
    private final BomSnapshotService bomSnapshotService;
    private final InventoryNormalizationService inventoryService;
    private final MrpRequirementDetailMapper requirementDetailMapper;
    private final MrpTerminalDemandMapper terminalDemandMapper;
    private final MrpDecisionIssueMapper issueMapper;
    private final MrpInventoryBalanceMapper balanceMapper;
    private final MrpInventoryAllocationMapper allocationMapper;
    private final CategoryRoleVersionService categoryRoleService;
    private final MaterialPolicyVersionService materialPolicyService;
    private final MaterialPolicyNoticeService policyNotices;
    private final StockRiskService stockRiskService;
    private final com.mrp.forecast.repository.ForecastMapper forecastMapper;

    public MrpCalculationService(
            MrpCalcBatchMapper batchMapper,
            MrpCalcRunMapper runMapper,
            BomSnapshotService bomSnapshotService,
            InventoryNormalizationService inventoryService,
            MrpRequirementDetailMapper requirementDetailMapper,
            MrpTerminalDemandMapper terminalDemandMapper,
            MrpDecisionIssueMapper issueMapper,
            MrpInventoryBalanceMapper balanceMapper,
            MrpInventoryAllocationMapper allocationMapper,
            CategoryRoleVersionService categoryRoleService,
            MaterialPolicyVersionService materialPolicyService,
            StockRiskService stockRiskService, MaterialPolicyNoticeService policyNotices,
            com.mrp.forecast.repository.ForecastMapper forecastMapper) {
        this.forecastMapper = forecastMapper;
        this.policyNotices = policyNotices;
        this.batchMapper = batchMapper;
        this.runMapper = runMapper;
        this.bomSnapshotService = bomSnapshotService;
        this.inventoryService = inventoryService;
        this.requirementDetailMapper = requirementDetailMapper;
        this.terminalDemandMapper = terminalDemandMapper;
        this.issueMapper = issueMapper;
        this.balanceMapper = balanceMapper;
        this.allocationMapper = allocationMapper;
        this.categoryRoleService = categoryRoleService;
        this.materialPolicyService = materialPolicyService;
        this.stockRiskService = stockRiskService;
    }

    @Transactional
    public MrpCalcRun executeCalculation(Long batchId, Long snapshotId, Long inventorySnapshotId,
                                          List<NetRequirementEngine.RootDemand> rootDemands) {
        return executeCalculation(batchId, snapshotId, inventorySnapshotId, rootDemands,
                com.mrp.mrp.engine.CalculationProgressListener.NONE);
    }

    @Transactional
    public MrpCalcRun executeCalculation(Long batchId, Long snapshotId, Long inventorySnapshotId,
            List<NetRequirementEngine.RootDemand> rootDemands,
            com.mrp.mrp.engine.CalculationProgressListener progress) {
        return executeCalculation(batchId, snapshotId, inventorySnapshotId, rootDemands, progress, false, null);
    }

    @Transactional
    public MrpCalcRun executeCalculation(Long batchId, Long snapshotId, Long inventorySnapshotId,
            List<NetRequirementEngine.RootDemand> rootDemands,
            com.mrp.mrp.engine.CalculationProgressListener progress, boolean materialStage, Long existingRunId) {
        progress.report(5, "加载锁定输入");
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalStateException("Batch not found: " + batchId);
        }
        if (!"CALCULATING".equals(batch.getStatus())) {
            throw new IllegalStateException("Batch not in CALCULATING status: " + batch.getStatus());
        }

        boolean split = ProductionCapacityRules.VERSION.equals(batch.getRuleVersion());
        MrpCalcRun run;
        if (materialStage) {
            if (!split || existingRunId == null || !existingRunId.equals(batch.getActiveRunId()))
                throw new IllegalStateException("物料分析必须基于当前已确认的整机 run");
            run = runMapper.selectById(existingRunId);
            if (run == null || !Set.of("SCHEDULE_READY", "COMPLETED", "COMPLETED_WITH_ISSUES").contains(run.getStatus()))
                throw new IllegalStateException("整机 run 尚未准备好物料分析");
        } else {
            run = new MrpCalcRun();
            run.setBatchId(batchId);
            run.setStatus("RUNNING");
            run.setStartedAt(LocalDateTime.now());
            runMapper.insert(run);
        }

        try {
            if (batch.getBomSnapshotId() != null && !batch.getBomSnapshotId().equals(snapshotId)) {
                throw new IllegalStateException("计算任务BOM输入与历史批次不一致");
            }
            List<BomSnapshotDetail> edges = bomSnapshotService.edgesForBatch(batch);

            List<MrpRequirementDetail> allDetails = new ArrayList<>();
            List<MrpTerminalDemand> allDemands = new ArrayList<>();
            List<MrpDecisionIssue> allIssues = new ArrayList<>();
            List<com.mrp.forecast.domain.ForecastDetail> forecast = "ALL".equals(batch.getFactoryCode())
                    ? forecastMapper.selectDetailsByVersionId(batch.getForecastVersionId()) : List.of();
            Map<String, MaterialPolicyDetail> materialPolicyMap = materialPolicyService.getDetailMap(batch.getMaterialPolicyVersionId());
            List<MrpInventoryAllocation> allocations = allocationMapper.selectByBatchId(batchId);
            Map<Long, MrpInventoryPool> allocationPools = new LinkedHashMap<>();
            for (MrpInventoryPool pool : inventoryService.getPoolsByBatch(batchId)) allocationPools.put(pool.getId(), pool);
            Map<Long,BigDecimal> machineAllocated = materialStage
                    ? MaterialAnalysisStageService.machineAllocatedByPool(allocations, allocationPools) : Map.of();
            List<String> frozenFactories = batchMapper.selectFactoryCodesByBatchId(batchId);
            List<String> factories = frozenFactories.isEmpty() ? ProductionCapacityRules.factories(batch) : frozenFactories;
            int factoryIndex = 0;
            for (String factory : factories) {
                List<NetRequirementEngine.RootDemand> roots = "ALL".equals(batch.getFactoryCode())
                        ? BatchOrchestratorService.rootDemandsForFactory(forecast, factory) : rootDemands;
                List<MrpInventoryBalance> allBalances = new ArrayList<>();
                for (Long source : snapshotsForFactory(batch, factory)) allBalances.addAll(balanceMapper.selectAllBySnapshotId(source));
                Map<String, BigDecimal> inventoryMap = new LinkedHashMap<>();
                for (MrpInventoryPool pool : inventoryService.getPoolsByBatch(batchId)) {
                    if (!factory.equals(pool.getFactoryCode())) continue;
                    inventoryMap.merge(pool.getMaterialCode() + "|GOOD", pool.getAvailableQty()
                            .subtract(pool.getReservedQty() == null ? BigDecimal.ZERO : pool.getReservedQty())
                            .subtract(machineAllocated.getOrDefault(pool.getId(),BigDecimal.ZERO)).max(BigDecimal.ZERO), BigDecimal::add);
                }
                Set<String> materialsWithInventory = allBalances.stream().filter(balance -> factory.equals(balance.getFactoryCode())
                                && !Boolean.FALSE.equals(balance.getIncludedFlag())
                                && !"DEFECTIVE".equals(balance.getInventoryQuality()))
                        .map(MrpInventoryBalance::getMaterialCode).collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> calculationMaterials = new LinkedHashSet<>();
                for (var root : roots) calculationMaterials.add(root.materialCode);
                for (var edge : edges) { calculationMaterials.add(edge.getParentCode()); calculationMaterials.add(edge.getChildCode()); }
                Map<String, CategoryRoleDetail> roles = categoryRoleService.getMaterialRoleMap(batch.getCategoryRoleVersionId(), calculationMaterials);
                Set<String> existingMaterials = categoryRoleService.getExistingMaterialCodes(calculationMaterials);
                List<MrpInventoryAllocation> factoryAllocations = allocations.stream()
                        .filter(allocation -> {
                            MrpInventoryPool pool = allocationPools.get(allocation.getPoolId());
                            return pool != null && factory.equals(pool.getFactoryCode())
                                    && (!materialStage || MaterialAnalysisStageService.isMaterialAllocation(allocation,pool));
                        }).toList();
                NetRequirementEngine engine = new NetRequirementEngine(edges, inventoryMap, roles, materialPolicyMap,
                        factoryAllocations, materialsWithInventory, factory, null, run.getId(), batchId, existingMaterials,
                        split && "ALL".equals(batch.getFactoryCode()));
                int startPercent = 10 + 35 * factoryIndex / factories.size();
                int endPercent = 10 + 35 * (factoryIndex + 1) / factories.size();
                progress.report(startPercent, "BOM展开与库存扣减 · " + factory, 0, roots.size());
                Map<String,Integer> priorityByRoot = new LinkedHashMap<>();
                for (var root : roots) priorityByRoot.put(root.materialCode + "|" + root.sourceMonth, root.prioritySeq);
                List<NetRequirementEngine.RootDemand> netMachineRoots = materialStage
                        ? terminalDemandMapper.selectByRunId(run.getId()).stream()
                            .filter(d -> ProductionCapacityRules.MACHINE_OUTPUT.equals(d.getTerminalType())
                                    && factory.equals(d.getFactoryCode()))
                            .map(d -> new NetRequirementEngine.RootDemand(d.getRootMaterialCode(), d.getQuantity(), d.getSourceMonth(),
                                    priorityByRoot.getOrDefault(d.getRootMaterialCode() + "|" + d.getSourceMonth(), 0)))
                            .sorted(Comparator.comparing((NetRequirementEngine.RootDemand root) -> root.sourceMonth)
                                    .thenComparingInt(root -> root.prioritySeq).thenComparing(root -> root.materialCode))
                            .toList() : List.of();
                var factoryResult = split && !materialStage ? engine.explodeRoots(roots)
                        : split ? engine.explodeNetRoots(netMachineRoots)
                        : engine.explode(roots, (completed, total) -> progress.report(
                            com.mrp.mrp.engine.CalculationProgressListener.within(startPercent, endPercent, completed, total),
                            "BOM展开与库存扣减 · " + factory, completed, total));
                factoryIndex++;
                if (ProductionCapacityRules.wholeMachine(batch) && !materialStage) factoryResult.terminalDemands.addAll(ProductionCapacityRules.machineDemands(factoryResult.details));
                allDetails.addAll(factoryResult.details); allDemands.addAll(factoryResult.terminalDemands); allIssues.addAll(factoryResult.issues);
            }
            // One terminal may be reached through several valid BOM paths. Persist
            // the sum for each unique terminal demand key.
            List<MrpTerminalDemand> mergedDemands = mergeTerminalDemands(allDemands);
            NetRequirementEngine.ExplosionResult result = new NetRequirementEngine.ExplosionResult(
                    allDetails, mergedDemands, Map.of(), Map.of(), allIssues);

            // Bound each SQL statement while retaining the candidate run's transaction.
            progress.report(45, "保存物料需求", 0, result.details.size());
            for (int start = 0; start < result.details.size(); start += WRITE_BATCH_SIZE) {
                requirementDetailMapper.insertBatch(result.details.subList(start,
                        Math.min(start + WRITE_BATCH_SIZE, result.details.size())));
                int completed = Math.min(start + WRITE_BATCH_SIZE, result.details.size());
                progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(45, 58, completed, result.details.size()),
                        "保存物料需求", completed, result.details.size());
            }
            progress.report(58, "保存末级需求", 0, result.terminalDemands.size());
            for (int start = 0; start < result.terminalDemands.size(); start += WRITE_BATCH_SIZE) {
                terminalDemandMapper.insertBatch(result.terminalDemands.subList(start,
                        Math.min(start + WRITE_BATCH_SIZE, result.terminalDemands.size())));
                int completed = Math.min(start + WRITE_BATCH_SIZE, result.terminalDemands.size());
                progress.report(com.mrp.mrp.engine.CalculationProgressListener.within(58, 62, completed, result.terminalDemands.size()),
                        "保存末级需求", completed, result.terminalDemands.size());
            }
            // 预检阶段已按同一 issueKey 落库并完成人工确认。计算重跑不得把已解决的问题重新打开。
            if (!result.issues.isEmpty()) {
                Set<String> existingKeys = issueMapper.selectByBatchId(batchId).stream()
                        .map(MrpDecisionIssue::getIssueKey)
                        .collect(Collectors.toSet());
                List<MrpDecisionIssue> freshIssues = new ArrayList<>();
                for (MrpDecisionIssue issue : result.issues) {
                    if (existingKeys.contains(issue.getIssueKey())) continue;
                    issue.setBatchId(batchId);
                    issue.setStatus("OPEN");
                    issue.setRevision(0);
                    issue.setCreatedAt(LocalDateTime.now());
                    freshIssues.add(issue);
                    existingKeys.add(issue.getIssueKey());
                }
                for (int start = 0; start < freshIssues.size(); start += WRITE_BATCH_SIZE) {
                    issueMapper.insertBatch(freshIssues.subList(start,
                            Math.min(start + WRITE_BATCH_SIZE, freshIssues.size())));
                }
            }

            progress.report(62, "记录库存与物料策略提示");
            if (!split || materialStage) {
                // Material analysis shares a stock pool with the confirmed machine
                // stage. Risk must include both deductions, including equal codes.
                stockRiskService.record(batch, run.getId(), materialStage
                        ? requirementDetailMapper.selectByRunId(run.getId()) : result.details);
                policyNotices.record(batch, run.getId(), result.details, materialPolicyMap);
            }

            long machineCount = result.terminalDemands.stream().filter(d -> ProductionCapacityRules.MACHINE_OUTPUT.equals(d.getTerminalType())).count();
            long retainedMachines = materialStage ? terminalDemandMapper.selectByRunId(run.getId()).stream()
                    .filter(d -> ProductionCapacityRules.MACHINE_OUTPUT.equals(d.getTerminalType())).count() : machineCount;
            run.setRowCountsJson("{\"details\":" + result.details.size() + ",\"terminals\":" + (result.terminalDemands.size() - machineCount) + ",\"machineOutputs\":" + retainedMachines + ",\"issues\":" + result.issues.size() + "}");
            run.setFinishedAt(LocalDateTime.now());

            batch = batchMapper.selectById(batchId);
            // New batches retain null snapshot id and their source checksum.
            batch.setInventorySnapshotId(inventorySnapshotId);

            // 只统计本次新产生、仍未处理的阻断问题；预检已确认的问题不算。
            boolean hasBlocking = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN").stream()
                    .anyMatch(i -> "BLOCKING".equals(i.getSeverity()) || "ERROR".equals(i.getSeverity()));

            if (split && !materialStage && !hasBlocking) {
                run.setStatus("SCHEDULE_READY");
                runMapper.update(run);
                batchMapper.updateStatusForce(batchId, "WAITING_DAILY_DISTRIBUTION");
            } else if (hasBlocking) {
                run.setStatus("COMPLETED_WITH_ISSUES");
                runMapper.update(run);

                List<MrpDecisionIssue> openIssues = issueMapper.selectByBatchIdAndStatus(batchId, "OPEN");
                batch.setIssueCount(result.issues.size());
                batch.setUnresolvedCount(openIssues.size());
                batchMapper.update(batch);
                batchMapper.updateStatusForce(batchId, materialStage ? "CALCULATING" : "WAITING_DECISION");

                log.warn("Calculation completed with {} blocking issues for batch {}, status set to WAITING_DECISION",
                        result.issues.size(), batchId);
            } else {
                run.setStatus("COMPLETED");
                runMapper.update(run);
                batchMapper.updateStatusForce(batchId, materialStage ? "CALCULATING" : "WAITING_DAILY_DISTRIBUTION");

                log.info("Calculation completed for batch {}: {} details, {} terminal demands, no blocking issues",
                        batchId, result.details.size(), result.terminalDemands.size());
            }

        } catch (Exception e) {
            if (materialStage) throw e;
            run.setStatus("FAILED");
            run.setErrorCode("CALC_ERROR");
            run.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown error");
            runMapper.update(run);
            batchMapper.updateStatusForce(batchId, "FAILED");
            throw e;
        }

        return run;
    }

    public List<MrpRequirementDetail> getRequirementDetails(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        return requirementDetailMapper.selectByRunId(runId);
    }

    public PageResult<MrpRequirementDetail> getRequirementDetailsPage(Long batchId, int page, int pageSize) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch == null ? null : batch.getActiveRunId();
        if (runId == null) return new PageResult<>(List.of(), 0, page, pageSize);
        int total = requirementDetailMapper.countByRunId(runId);
        return new PageResult<>(requirementDetailMapper.selectPageByRunId(runId, (long) (page - 1) * pageSize, pageSize), total, page, pageSize);
    }

    public List<MrpTerminalDemand> getTerminalDemands(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        return terminalDemandMapper.selectByRunId(runId).stream()
                .filter(demand -> !ProductionCapacityRules.MACHINE_OUTPUT.equals(demand.getTerminalType())).toList();
    }

    public PageResult<MrpTerminalDemand> getTerminalDemandsPage(Long batchId, String keyword, int page, int pageSize) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch == null ? null : batch.getActiveRunId();
        if (runId == null) return new PageResult<>(List.of(), 0, page, pageSize);
        String filter = keyword == null || keyword.isBlank() ? null : keyword.trim();
        int total = terminalDemandMapper.countByRunId(runId, filter);
        return new PageResult<>(terminalDemandMapper.selectPageByRunId(runId, filter,
                (long) (page - 1) * pageSize, pageSize), total, page, pageSize);
    }

    @Transactional(readOnly=true)
    public PageResult<MrpTerminalDemand> getShortagePage(Long batchId, String factory, String root,
            String terminal, LocalDate sourceMonth, String keyword, boolean detail, int page, int pageSize) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch == null ? null : batch.getActiveRunId();
        if (runId == null) return new PageResult<>(List.of(), 0, page, pageSize);
        String filter = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (!detail && (root == null || root.isBlank())) {
            int total = Math.toIntExact(terminalDemandMapper.countShortageSummary(runId,factory,terminal,sourceMonth,filter));
            Long poolBatchId = poolSourceBatchId(batch);
            Long issueBatchId = "PUBLISHED".equals(batch.getStatus()) ? poolBatchId : batchId;
            return new PageResult<>(terminalDemandMapper.selectShortageSummary(runId,poolBatchId,batchId,issueBatchId,factory,terminal,sourceMonth,filter,
                    (long)(page-1)*pageSize,pageSize),total,page,pageSize);
        }
        int total = Math.toIntExact(terminalDemandMapper.countShortagePage(runId, factory, root, terminal, sourceMonth, filter));
        return new PageResult<>(terminalDemandMapper.selectShortagePage(runId, factory, root, terminal, sourceMonth, filter,
                (long) (page - 1) * pageSize, pageSize), total, page, pageSize);
    }

    @Transactional(readOnly=true)
    public PageResult<MrpInventoryPool> getShortagePools(Long batchId, String factory, String material,
            int page, int pageSize) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        if (batch == null || batch.getActiveRunId() == null)
            return new PageResult<>(List.of(), 0, page, pageSize);
        Long poolBatchId = poolSourceBatchId(batch);
        int total = Math.toIntExact(terminalDemandMapper.countShortagePools(poolBatchId, factory, material));
        return new PageResult<>(terminalDemandMapper.selectShortagePools(poolBatchId, factory, material,
                (long)(page-1)*pageSize, pageSize), total, page, pageSize);
    }

    private Long poolSourceBatchId(MrpCalcBatch batch) {
        Long candidate = batch.getRevisionOfBatchId();
        if (!"PUBLISHED".equals(batch.getStatus()) || candidate == null) return batch.getId();
        MrpCalcBatch source = batchMapper.selectById(candidate);
        return source != null && batch.getId().equals(source.getSupersededByBatchId())
                ? candidate : batch.getId();
    }

    public List<MrpTerminalDemand> getTerminalDemandsAggregated(Long batchId) {
        MrpCalcBatch batch = batchMapper.selectById(batchId);
        Long runId = batch != null ? batch.getActiveRunId() : null;
        if (runId == null) {
            return Collections.emptyList();
        }
        return terminalDemandMapper.selectAggregatedByRunId(runId);
    }
    static List<MrpTerminalDemand> mergeTerminalDemands(List<MrpTerminalDemand> demands) {
        record DemandKey(Long runId, String factory, String root, String terminal,
                         String type, String supply, LocalDate month) {}
        Map<DemandKey, MrpTerminalDemand> merged = new LinkedHashMap<>();
        for (MrpTerminalDemand demand : demands) {
            DemandKey key = new DemandKey(demand.getRunId(), demand.getFactoryCode(),
                    demand.getRootMaterialCode(), demand.getTerminalMaterialCode(),
                    demand.getTerminalType(), demand.getSupplyMode(), demand.getSourceMonth());
            MrpTerminalDemand existing = merged.get(key);
            if (existing == null) {
                MrpTerminalDemand copy = new MrpTerminalDemand();
                copy.setRunId(demand.getRunId());
                copy.setBatchId(demand.getBatchId());
                copy.setFactoryCode(demand.getFactoryCode());
                copy.setRootMaterialCode(demand.getRootMaterialCode());
                copy.setTerminalMaterialCode(demand.getTerminalMaterialCode());
                copy.setTerminalType(demand.getTerminalType());
                copy.setSupplyMode(demand.getSupplyMode());
                copy.setSourceMonth(demand.getSourceMonth());
                copy.setQuantity(demand.getQuantity());
                merged.put(key, copy);
            } else {
                existing.setQuantity(existing.getQuantity().add(demand.getQuantity()));
            }
        }
        return new ArrayList<>(merged.values());
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
                .map(scope -> inventoryService.snapshotIdForWarehouse(batch.getInventorySnapshotGroupId(), scope.getWarehouseCode(),
                        batch.getInventorySnapshotId())).distinct().toList();
        if (snapshots.isEmpty()) throw new IllegalStateException("冻结工厂范围缺少仓库: " + factory);
        return snapshots;
    }

}
