package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.CalculationProgressListener;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDailyPlanDetailMapper;
import com.mrp.mrp.repository.MrpTerminalDemandMapper;
import com.mrp.mrp.repository.MrpRequirementDetailMapper;
import com.mrp.mrp.repository.MrpUnallocatedQuantityMapper;
import com.mrp.mrp.repository.MrpDecisionIssueMapper;
import com.mrp.mrp.repository.MrpInventoryAllocationMapper;
import com.mrp.mrp.repository.MrpInventoryPoolMapper;
import com.mrp.mrp.repository.MrpStockRiskMapper;
import com.mrp.mrp.repository.MrpMaterialPolicyNoticeMapper;
import com.mrp.mrp.repository.MrpInventoryBalanceMapper;
import com.mrp.mrp.repository.MrpDailyPlanOverrideMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically appends material facts to the confirmed machine run. */
@Service
public class MaterialAnalysisStageService {
    private final MrpCalcBatchMapper batches;
    private final MrpCalculationService calculations;
    private final DailyPlanningService daily;
    private final MrpWorkbenchReviewService reviews;
    private final MrpDailyPlanDetailMapper dailyFacts;
    private final MrpTerminalDemandMapper demands;
    private final MrpRequirementDetailMapper requirements;
    private final MrpUnallocatedQuantityMapper unallocated;
    private final MrpDecisionIssueMapper issues;
    private final MrpInventoryAllocationMapper allocations;
    private final MrpInventoryPoolMapper pools;
    private final MrpStockRiskMapper risks;
    private final MrpMaterialPolicyNoticeMapper notices;
    private final MrpInventoryBalanceMapper balances;
    private final MrpDailyPlanOverrideMapper overrides;
    private final InventoryNormalizationService inventory;
    private final CategoryRoleVersionService categoryRoles;
    private final MaterialPolicyVersionService materialPolicies;
    private final BomSnapshotService bom;
    private final ForecastMapper forecast;

    public MaterialAnalysisStageService(MrpCalcBatchMapper batches, MrpCalculationService calculations,
            DailyPlanningService daily, MrpWorkbenchReviewService reviews,
            MrpDailyPlanDetailMapper dailyFacts, MrpTerminalDemandMapper demands,
            MrpRequirementDetailMapper requirements, MrpUnallocatedQuantityMapper unallocated,
            MrpDecisionIssueMapper issues, MrpInventoryAllocationMapper allocations,
            MrpInventoryPoolMapper pools, MrpStockRiskMapper risks, MrpMaterialPolicyNoticeMapper notices,
            MrpInventoryBalanceMapper balances, MrpDailyPlanOverrideMapper overrides,
            InventoryNormalizationService inventory,
            CategoryRoleVersionService categoryRoles, MaterialPolicyVersionService materialPolicies,
            BomSnapshotService bom, ForecastMapper forecast) {
        this.batches=batches; this.calculations=calculations; this.daily=daily;
        this.reviews=reviews;
        this.dailyFacts=dailyFacts; this.demands=demands; this.requirements=requirements;
        this.unallocated=unallocated; this.issues=issues; this.allocations=allocations; this.pools=pools;
        this.risks=risks; this.notices=notices; this.balances=balances; this.overrides=overrides; this.inventory=inventory;
        this.categoryRoles=categoryRoles; this.materialPolicies=materialPolicies; this.bom=bom; this.forecast=forecast;
    }

    @Transactional
    public MrpCalcRun execute(Long batchId, Long runId, String scheduleFingerprint,
            Long bomSnapshotId, Long inventorySnapshotId,
            List<NetRequirementEngine.RootDemand> roots, CalculationProgressListener progress) {
        MrpCalcBatch batch=batches.selectForUpdate(batchId);
        if (batch==null || !"CALCULATING".equals(batch.getStatus())
                || !reviews.matchesSchedule(batchId,runId,scheduleFingerprint))
            throw new IllegalStateException("整机排产已变化，请重新提交物料分析");
        // One transaction replaces only material facts. A failed recalculation restores the old facts.
        if (overrides.selectByBatchId(batchId).stream()
                .anyMatch(row->!ProductionCapacityRules.MACHINE_OUTPUT.equals(row.getTerminalType())))
            throw new IllegalStateException("物料日计划存在人工调整，请先处理并确认调整口径后再重算");
        if (unallocated.countResolvedMaterialByRunId(runId)>0)
            throw new IllegalStateException("旧物料待分配已人工处理，请先确认重算后如何迁移该决策");
        if (notices.countConfirmedByRunId(runId)>0)
            throw new IllegalStateException("旧物料默认下钻已人工确认，请先确认重算后如何迁移该决策");
        if (risks.countResolvedByRunId(runId)>0)
            throw new IllegalStateException("旧物料库存风险已人工处理，请先确认重算后如何迁移该决策");
        if (issues.countResolvedMaterialByBatchId(batchId)>0)
            throw new IllegalStateException("旧物料异常已人工处理，请先确认重算后如何迁移该决策");
        unallocated.deleteMaterialByRunId(runId);
        dailyFacts.deleteMaterialByRunId(runId);
        demands.deleteMaterialByRunId(runId);
        // Split runs write stock risks only in the material stage, including
        // component codes that happen to equal a finished-machine code.
        risks.deleteByRunId(runId);
        requirements.deleteMaterialByRunId(runId);
        issues.deleteOpenMaterialByBatchId(batchId);
        notices.deleteByRunId(runId);
        rebuildMaterialAllocations(batch);
        MrpCalcRun run=calculations.executeCalculation(batchId,bomSnapshotId,inventorySnapshotId,
                roots,progress,true,runId);
        if (batch.getProductionCalendarVersionId()==null)
            throw new IllegalStateException("缺少冻结的生产日历");
        daily.distributeMaterial(batchId,runId,batch.getProductionCalendarVersionId(),
                batch.getMaterialPolicyVersionId(),progress);
        DailyPlanningService.ConservationCheckResult check=daily.getSourceMonthConservation(batchId,runId);
        if (check.mismatchCount>0)
            throw new IllegalStateException("物料日事实数量不守恒: " + check.mismatchCount);
        batches.activateRunAndStatus(batchId,runId,"MATERIAL_REVIEW");
        return run;
    }

    private void rebuildMaterialAllocations(MrpCalcBatch batch) {
        Long batchId=batch.getId();
        allocations.deleteAutoMaterialByBatchId(batchId);
        pools.rebuildAllocatedQuantitiesByBatchId(batchId);
        List<MrpInventoryPool> allPools=inventory.getPoolsByBatch(batchId);
        Map<Long,MrpInventoryPool> poolById=new LinkedHashMap<>();
        for (MrpInventoryPool pool:allPools) poolById.put(pool.getId(),pool);
        List<MrpInventoryAllocation> manual=new ArrayList<>();
        List<MrpInventoryAllocation> retained=allocations.selectByBatchId(batchId);
        Map<Long,BigDecimal> machineAllocated=machineAllocatedByPool(retained,poolById);
        for (MrpInventoryAllocation row:retained) {
            MrpInventoryPool pool=poolById.get(row.getPoolId());
            if (pool==null) throw new IllegalStateException("冻结库存分配缺少库存池");
            if ("MANUAL_OVERRIDE".equals(row.getAllocationType()) && isMaterialAllocation(row,pool))
                manual.add(row);
        }
        Map<String,List<MrpInventoryPool>> ordered=BatchOrchestratorService.orderedGoodPools(
                batches.selectFactoryWarehousesByBatchId(batchId),allPools);
        Map<Long,BigDecimal> remaining=new LinkedHashMap<>();
        for (List<MrpInventoryPool> candidates:ordered.values()) for (MrpInventoryPool pool:candidates)
            remaining.put(pool.getId(),pool.getAvailableQty().subtract(pool.getReservedQty()==null?BigDecimal.ZERO:pool.getReservedQty())
                    .subtract(pool.getAllocatedQty()==null?BigDecimal.ZERO:pool.getAllocatedQty()).max(BigDecimal.ZERO));
        Map<String,MrpInventoryAllocation> grouped=new LinkedHashMap<>();
        Map<String,MaterialPolicyDetail> policy=materialPolicies.getDetailMap(batch.getMaterialPolicyVersionId());
        List<com.mrp.mrp.domain.BomSnapshotDetail> edges=bom.edgesForBatch(batch);
        List<String> frozenFactories=batches.selectFactoryCodesByBatchId(batchId);
        List<MrpTerminalDemand> machineDemands=demands.selectByRunId(batch.getActiveRunId());
        var forecastRows=batch.getForecastVersionId()==null ? Collections.<com.mrp.forecast.domain.ForecastDetail>emptyList()
                : forecast.selectDetailsByVersionId(batch.getForecastVersionId());
        for (String factory:frozenFactories.isEmpty()?ProductionCapacityRules.factories(batch):frozenFactories) {
            Map<String,Integer> priorityByRoot=new LinkedHashMap<>();
            for (var source:BatchOrchestratorService.rootDemandsForFactory(forecastRows,factory))
                priorityByRoot.put(source.materialCode+"|"+source.sourceMonth,source.prioritySeq);
            var roots=machineDemands.stream()
                    .filter(d->ProductionCapacityRules.MACHINE_OUTPUT.equals(d.getTerminalType()) && factory.equals(d.getFactoryCode()))
                    .map(d->new NetRequirementEngine.RootDemand(d.getRootMaterialCode(),d.getQuantity(),d.getSourceMonth(),
                            priorityByRoot.getOrDefault(d.getRootMaterialCode()+"|"+d.getSourceMonth(),0)))
                    .sorted(Comparator.comparing((NetRequirementEngine.RootDemand root)->root.sourceMonth)
                            .thenComparingInt(root->root.prioritySeq).thenComparing(root->root.materialCode)).toList();
            if (roots.isEmpty()) continue;
            Set<String> codes=new LinkedHashSet<>();
            for (var root:roots) codes.add(root.materialCode);
            for (var edge:edges) { codes.add(edge.getParentCode()); codes.add(edge.getChildCode()); }
            Map<String,CategoryRoleDetail> roles=categoryRoles.getMaterialRoleMap(batch.getCategoryRoleVersionId(),codes);
            Set<String> existing=categoryRoles.getExistingMaterialCodes(codes);
            Set<String> withInventory=new LinkedHashSet<>();
            Set<Long> snapshotIds=new LinkedHashSet<>();
            for (MrpInventoryPool pool:poolById.values())
                if (factory.equals(pool.getFactoryCode()))
                    snapshotIds.add(pool.getSnapshotId());
            for (Long snapshotId:snapshotIds) for (MrpInventoryBalance balance:balances.selectAllBySnapshotId(snapshotId))
                if (factory.equals(balance.getFactoryCode())
                        && !Boolean.FALSE.equals(balance.getIncludedFlag())
                        && !"DEFECTIVE".equals(balance.getInventoryQuality()))
                    withInventory.add(balance.getMaterialCode());
            Map<String,BigDecimal> available=new LinkedHashMap<>();
            for (List<MrpInventoryPool> candidates:ordered.values()) for (MrpInventoryPool pool:candidates)
                if (factory.equals(pool.getFactoryCode())) available.merge(pool.getMaterialCode()+"|GOOD",
                        pool.getAvailableQty().subtract(pool.getReservedQty()==null?BigDecimal.ZERO:pool.getReservedQty())
                                .subtract(machineAllocated.getOrDefault(pool.getId(),BigDecimal.ZERO)).max(BigDecimal.ZERO),BigDecimal::add);
            var fixed=manual.stream().filter(a->factory.equals(poolById.get(a.getPoolId()).getFactoryCode())).toList();
            Set<String> fixedKeys=new LinkedHashSet<>();
            for (MrpInventoryAllocation item:fixed) fixedKeys.add(item.getDemandKey());
            var preview=new NetRequirementEngine(edges,available,roles,policy,fixed,withInventory,factory,null,
                    batch.getActiveRunId(),batchId,existing).explodeNetRoots(roots);
            for (MrpRequirementDetail detail:preview.details) {
                String key=detail.getRootMaterialCode()+"|"+detail.getCurrentMaterialCode()+"|"+detail.getSourceMonth();
                BigDecimal needed=detail.getInventoryUsedQty();
                if (needed==null || needed.signum()<=0) continue;
                // Manual override is the entire budget for this demand key.
                if (fixedKeys.contains(key)) continue;
                for (MrpInventoryPool pool:ordered.getOrDefault(factory+"|"+detail.getCurrentMaterialCode(),List.of())) {
                    BigDecimal taken=needed.min(remaining.get(pool.getId()));
                    if (taken.signum()<=0) continue;
                    remaining.put(pool.getId(),remaining.get(pool.getId()).subtract(taken));
                    String identity=pool.getId()+"|"+key;
                    MrpInventoryAllocation row=grouped.computeIfAbsent(identity,ignored->{
                        MrpInventoryAllocation value=new MrpInventoryAllocation(); value.setPoolId(pool.getId());
                        value.setDemandKey(key); value.setRootMaterialCode(detail.getRootMaterialCode());
                        value.setSourceMonth(detail.getSourceMonth()); value.setPathHash(detail.getPathHash());
                        value.setPrioritySeq(priorityByRoot.getOrDefault(
                                detail.getRootMaterialCode()+"|"+detail.getSourceMonth(),0));
                        value.setAllocationType("AUTO_PRIORITY"); value.setManualConfirmed(false); value.setRevision(0);
                        return value;
                    });
                    row.setGrossQty((row.getGrossQty()==null?BigDecimal.ZERO:row.getGrossQty()).add(detail.getGrossQty()));
                    row.setAllocatedQty((row.getAllocatedQty()==null?BigDecimal.ZERO:row.getAllocatedQty()).add(taken));
                    needed=needed.subtract(taken);
                    if (needed.signum()==0) break;
                }
                if (needed.signum()>0) throw new IllegalStateException("物料库存池不足以覆盖预计扣减："+factory+"/"+detail.getCurrentMaterialCode());
            }
        }
        if (!grouped.isEmpty()) allocations.insertBatch(new ArrayList<>(grouped.values()));
        pools.rebuildAllocatedQuantitiesByBatchId(batchId);
    }

    static boolean isMaterialAllocation(MrpInventoryAllocation allocation, MrpInventoryPool pool) {
        // Demand keys can have the same material code at different BOM depths.
        // Root facts use the root itself as path_hash; descendants have a longer path.
        if (allocation.getPathHash()!=null)
            return !allocation.getPathHash().equals(allocation.getRootMaterialCode());
        return !allocation.getRootMaterialCode().equals(pool.getMaterialCode())
                || !allocation.getDemandKey().equals(allocation.getRootMaterialCode()+"|"+allocation.getRootMaterialCode()
                    +"|"+allocation.getSourceMonth());
    }

    static Map<Long,BigDecimal> machineAllocatedByPool(List<MrpInventoryAllocation> rows,
            Map<Long,MrpInventoryPool> poolById) {
        Map<Long,BigDecimal> machine=new LinkedHashMap<>();
        for (MrpInventoryAllocation row:rows) {
            MrpInventoryPool pool=poolById.get(row.getPoolId());
            if (pool==null) throw new IllegalStateException("冻结库存分配缺少库存池");
            if (!isMaterialAllocation(row,pool)) {
                BigDecimal qty=row.getAllocatedQty();
                if (qty==null || qty.signum()<0) throw new IllegalStateException("整机库存分配数量无效");
                machine.merge(pool.getId(),qty,BigDecimal::add);
            }
        }
        return machine;
    }

}
