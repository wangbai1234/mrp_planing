package com.mrp.mrp.service;

import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpInventoryPool;
import com.mrp.mrp.domain.MrpRequirementDetail;
import com.mrp.mrp.domain.MrpStockRisk;
import com.mrp.mrp.domain.StockPolicyDetail;
import com.mrp.mrp.repository.MrpStockRiskMapper;
import com.mrp.audit.service.AuditService;
import com.mrp.common.security.CurrentUser;
import com.mrp.common.exception.ConcurrentChangeException;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.repository.Phase2ScopeConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 库存风险只读提示：安全库存红色、预警库存黄色、未维护策略单独列出。
 * 不写入决策问题，因此不参与计算和发布门禁。
 */
@Service
public class StockRiskService {

    private static final int WRITE_BATCH_SIZE = 1000;
    private record FactoryMaterial(String factory, String material) {}
    private record WarehouseMaterial(String warehouse, String material) {}

    static final String SAFETY_BREACH = "SAFETY_STOCK_BREACH";
    static final String WARNING_BREACH = "WARNING_STOCK_BREACH";
    static final String POLICY_MISSING = "STOCK_POLICY_MISSING";

    private final MrpStockRiskMapper riskMapper;
    private final Phase2ScopeConfigMapper scopeConfigMapper;
    private final InventoryNormalizationService inventoryService;
    private final MrpCalcBatchMapper batchMapper;
    private final AuditService audit;

    public StockRiskService(MrpStockRiskMapper riskMapper, Phase2ScopeConfigMapper scopeConfigMapper,
                            InventoryNormalizationService inventoryService, MrpCalcBatchMapper batchMapper,
                            AuditService audit) {
        this.riskMapper = riskMapper;
        this.scopeConfigMapper = scopeConfigMapper;
        this.inventoryService = inventoryService;
        this.batchMapper = batchMapper;
        this.audit = audit;
    }

    @Transactional
    public void record(MrpCalcBatch batch, Long runId, List<MrpRequirementDetail> details) {
        riskMapper.deleteByRunId(runId);
        List<MrpStockRisk> risks = evaluate(batch, runId, details);
        for (int from = 0; from < risks.size(); from += WRITE_BATCH_SIZE) {
            riskMapper.insertBatch(risks.subList(from, Math.min(from + WRITE_BATCH_SIZE, risks.size())));
        }
    }

    public List<MrpStockRisk> list(Long batchId, Long runId) {
        return riskMapper.selectByBatchId(batchId, runId);
    }

    public PageResult<MrpStockRisk> page(Long batchId, Long runId, int page, int pageSize) {
        int total = riskMapper.countByBatchId(batchId, runId);
        return new PageResult<>(riskMapper.selectPageByBatchId(batchId, runId, (long) (page - 1) * pageSize, pageSize), total, page, pageSize);
    }

    @Transactional
    public int applyZeroThresholdForRun(Long batchId, Long runId) {
        var batch = batchMapper.selectForUpdate(batchId);
        if (batch == null || runId == null || !runId.equals(batch.getActiveRunId()))
            throw new ConcurrentChangeException("当前排产结果已变化，请刷新后重试");
        Long operatorId = CurrentUser.getUserId();
        if (operatorId == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "请先登录");
        int changed = riskMapper.resolvePendingByRunId(batchId, runId, operatorId, "ZERO_THRESHOLDS_FOR_THIS_RUN");
        if (changed > 0) audit.log(operatorId, null, batch.getFactoryCode(), "APPLY_ZERO_STOCK_THRESHOLDS",
                "mrp_stock_risk", batchId + ":" + runId, null,
                Map.of("resolvedCount", changed, "safetyStockQty", BigDecimal.ZERO,
                        "warningStockQty", BigDecimal.ZERO, "scope", "CURRENT_RUN_ONLY"), null, null);
        return changed;
    }

    List<MrpStockRisk> evaluate(MrpCalcBatch batch, Long runId, List<MrpRequirementDetail> details) {
        Map<FactoryMaterial, Set<String>> rootsByMaterial = new LinkedHashMap<>();
        Map<FactoryMaterial, BigDecimal> usedByMaterial = new LinkedHashMap<>();
        for (MrpRequirementDetail detail : details) {
            String material = detail.getCurrentMaterialCode();
            if (material == null) continue;
            FactoryMaterial factoryMaterial = new FactoryMaterial(detail.getFactoryCode(), material);
            rootsByMaterial.computeIfAbsent(factoryMaterial, key -> new LinkedHashSet<>()).add(detail.getRootMaterialCode());
            usedByMaterial.merge(factoryMaterial, detail.getInventoryUsedQty() == null
                    ? BigDecimal.ZERO : detail.getInventoryUsedQty(), BigDecimal::add);
        }


        Map<WarehouseMaterial, StockPolicyDetail> policies = new LinkedHashMap<>();
        if (batch.getStockPolicyVersionId() != null) {
            for (StockPolicyDetail policy : scopeConfigMapper.selectStockPolicyDetails(batch.getStockPolicyVersionId())) {
                policies.put(new WarehouseMaterial(policy.getWarehouseCode(), policy.getMaterialCode()), policy);
            }
        }

        List<MrpStockRisk> risks = new ArrayList<>();
        List<MrpInventoryPool> pools = inventoryService.getPoolsByBatch(batch.getId());
        Set<FactoryMaterial> materialsWithPools = new LinkedHashSet<>();
        for (MrpInventoryPool pool : pools) {
            FactoryMaterial factoryMaterial = new FactoryMaterial(pool.getFactoryCode(), pool.getMaterialCode());
            materialsWithPools.add(factoryMaterial);
            StockPolicyDetail policy = policies.get(new WarehouseMaterial(pool.getWarehouseCode(), pool.getMaterialCode()));
            if (policy == null) {
                if (rootsByMaterial.containsKey(factoryMaterial)) {
                    MrpStockRisk missing = base(batch, runId, POLICY_MISSING,
                            pool.getMaterialCode(), pool.getFactoryCode(), rootsByMaterial);
                    missing.setWarehouseCode(pool.getWarehouseCode());
                    risks.add(missing);
                }
                continue;
            }
            BigDecimal safety = zero(policy.getSafetyStockQty());
            BigDecimal warning = zero(policy.getWarningStockQty());
            if (safety.signum() == 0 && warning.signum() == 0) continue;

            BigDecimal available = zero(pool.getAvailableQty());
            // The split workflow allocates each machine/component deduction to an
            // inventory pool. Factory-wide usage repeated for each warehouse
            // would manufacture false risks.
            BigDecimal used = ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())
                    ? zero(pool.getAllocatedQty())
                    : usedByMaterial.getOrDefault(factoryMaterial, BigDecimal.ZERO);
            BigDecimal remaining = available.subtract(used);
            String riskType = null;
            if (remaining.compareTo(safety) <= 0) riskType = SAFETY_BREACH;
            else if (remaining.compareTo(warning) <= 0) riskType = WARNING_BREACH;
            if (riskType == null) continue;

            MrpStockRisk risk = base(batch, runId, riskType, pool.getMaterialCode(), pool.getFactoryCode(), rootsByMaterial);
            risk.setWarehouseCode(pool.getWarehouseCode());
            risk.setAvailableQty(available);
            risk.setUsedQty(used);
            risk.setRemainingQty(remaining);
            risk.setSafetyStockQty(safety);
            risk.setWarningStockQty(warning);
            risks.add(risk);
        }

        for (FactoryMaterial key : rootsByMaterial.keySet()) {
            if (!materialsWithPools.contains(key)) {
                risks.add(base(batch, runId, POLICY_MISSING, key.material(), key.factory(), rootsByMaterial));
            }
        }

        risks.sort(Comparator.comparing(MrpStockRisk::getRiskType).thenComparing(MrpStockRisk::getMaterialCode));
        return risks;
    }

    private MrpStockRisk base(MrpCalcBatch batch, Long runId, String riskType, String material, String factory,
                              Map<FactoryMaterial, Set<String>> rootsByMaterial) {
        MrpStockRisk risk = new MrpStockRisk();
        risk.setBatchId(batch.getId());
        risk.setRunId(runId);
        risk.setFactoryCode(factory);
        risk.setRiskType(riskType);
        risk.setMaterialCode(material);
        Set<String> roots = rootsByMaterial.getOrDefault(new FactoryMaterial(factory, material), Set.of());
        String joined = String.join(",", roots);
        risk.setRootMaterialCodes(joined.length() > 1000 ? joined.substring(0, 1000) : joined);
        return risk;
    }

    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
