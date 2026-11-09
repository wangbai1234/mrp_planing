package com.mrp.mrp.service;

import com.mrp.inventory.domain.InventoryItem;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.repository.InventoryItemMapper;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.mrp.domain.MrpInventoryBalance;
import com.mrp.mrp.domain.MrpInventoryPool;
import com.mrp.mrp.domain.MrpInventoryAllocation;
import com.mrp.mrp.domain.StockPolicyDetail;
import com.mrp.mrp.repository.MrpInventoryBalanceMapper;
import com.mrp.mrp.repository.MrpInventoryPoolMapper;
import com.mrp.mrp.repository.MrpInventoryAllocationMapper;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpWorkbenchReviewMapper;
import com.mrp.mrp.repository.Phase2ScopeConfigMapper;
import com.mrp.audit.service.AuditService;
import com.mrp.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class InventoryNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryNormalizationService.class);
    // Codes may contain separators; preserve field boundaries without serializing composite keys.
    private record WarehouseMaterialKey(String warehouse, String material) {}
    private record BalanceKey(String warehouse, String factory, String material, String quality) {}

    private final InventoryItemMapper inventoryItemMapper;
    private final InventoryMapper inventoryMapper;
    private final MrpInventoryBalanceMapper balanceMapper;
    private final MrpInventoryPoolMapper poolMapper;
    private final MrpInventoryAllocationMapper allocationMapper;
    private final Phase2ScopeConfigMapper scopeConfigMapper;
    private final com.mrp.mrp.repository.InventorySnapshotGroupMapper snapshotGroups;
    private final MrpCalcBatchMapper batchMapper;
    private final MrpWorkbenchReviewMapper reviews;
    private final AuditService audit;

    public InventoryNormalizationService(
            InventoryItemMapper inventoryItemMapper,
            InventoryMapper inventoryMapper,
            MrpInventoryBalanceMapper balanceMapper,
            MrpInventoryPoolMapper poolMapper,
            MrpInventoryAllocationMapper allocationMapper,
            Phase2ScopeConfigMapper scopeConfigMapper, com.mrp.mrp.repository.InventorySnapshotGroupMapper snapshotGroups,
            MrpCalcBatchMapper batchMapper, MrpWorkbenchReviewMapper reviews, AuditService audit) {
        this.snapshotGroups = snapshotGroups;
        this.inventoryItemMapper = inventoryItemMapper;
        this.inventoryMapper = inventoryMapper;
        this.balanceMapper = balanceMapper;
        this.poolMapper = poolMapper;
        this.allocationMapper = allocationMapper;
        this.scopeConfigMapper = scopeConfigMapper;
        this.batchMapper = batchMapper;
        this.reviews = reviews;
        this.audit = audit;
    }

    @Transactional
    public Long createSnapshotGroup(Map<String, Long> warehouseSnapshots, Long userId) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("groupName", "排产库存版本组"); group.put("snapshotDate", java.time.LocalDate.now());
        group.put("createdBy", userId);
        try {
            String stable = new java.util.TreeMap<>(warehouseSnapshots).toString();
            group.put("checksum", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(stable.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        snapshotGroups.insert(group);
        Long groupId = ((Number) group.get("id")).longValue();
        warehouseSnapshots.forEach((warehouse, snapshot) -> snapshotGroups.insertMember(groupId, warehouse, snapshot));
        return groupId;
    }

    public Long snapshotIdForWarehouse(Long groupId, String warehouseCode, Long fallback) {
        if (groupId == null) return fallback;
        Long snapshotId = snapshotGroups.selectSnapshotId(groupId, warehouseCode);
        if (snapshotId == null) throw new IllegalStateException("冻结库存版本组缺少仓库：" + warehouseCode);
        return snapshotId;
    }

    @Transactional
    public List<MrpInventoryBalance> normalizeSnapshot(Long snapshotId, String factoryCode, String inventoryOrg) {
        InventorySnapshot snapshot = inventoryMapper.selectSnapshotById(snapshotId);
        if (snapshot == null) throw new IllegalStateException("Inventory snapshot not found: " + snapshotId);
        List<InventoryItem> items = inventoryItemMapper.selectBySnapshotId(snapshotId);
        if (items.isEmpty()) {
            log.warn("No inventory items found for snapshot {}", snapshotId);
            return List.of();
        }

        Map<String, String> factoryByWarehouse = new LinkedHashMap<>();
        Map<BalanceKey, BigDecimal> allMaterialQuality = new LinkedHashMap<>();
        for (InventoryItem item : items) {
            String warehouseCode = item.warehouseCode();
            if (warehouseCode == null || warehouseCode.isBlank()) {
                throw new IllegalStateException("库存明细存在未维护仓库的记录，料号: " + item.materialId());
            }
            String mappedFactory = factoryByWarehouse.computeIfAbsent(warehouseCode,
                    scopeConfigMapper::selectPublishedFactoryByWarehouse);
            if (mappedFactory == null || mappedFactory.isBlank()) {
                throw new IllegalStateException("仓库 " + warehouseCode + " 尚未维护工厂映射");
            }
            if (item.inventoryQuality() == null) continue;
            BalanceKey key = new BalanceKey(warehouseCode, mappedFactory, item.materialId(), item.inventoryQuality());
            allMaterialQuality.merge(key,
                    item.shippingAvailableQty() == null ? BigDecimal.ZERO : item.shippingAvailableQty(),
                    BigDecimal::add);
        }
        if (factoryCode != null && !factoryCode.isBlank() && !"ALL".equals(factoryCode)
                && factoryByWarehouse.values().stream().noneMatch(factoryCode::equals)) {
            throw new IllegalStateException("库存快照中没有所选工厂对应仓库的数据: " + factoryCode);
        }

        List<MrpInventoryBalance> balances = new ArrayList<>();
        for (Map.Entry<BalanceKey, BigDecimal> entry : allMaterialQuality.entrySet()) {
            BalanceKey key = entry.getKey();
            String warehouseCode = key.warehouse();
            String mappedFactory = key.factory();
            String materialCode = key.material();
            String quality = key.quality();
            BigDecimal qty = entry.getValue().max(BigDecimal.ZERO);
            MrpInventoryBalance balance = new MrpInventoryBalance();
            balance.setInventorySnapshotId(snapshotId);
            balance.setFactoryCode(mappedFactory);
            balance.setInventoryOrg(warehouseCode);
            balance.setWarehouseScope(warehouseCode);
            balance.setMaterialCode(materialCode);
            balance.setInventoryQuality(quality);
            balance.setAvailableQuantity(qty);
            // GOOD zero quantity remains a real inventory record; DEFECTIVE is trace-only.
            balance.setIncludedFlag("GOOD".equals(quality));
            balance.setNormalizationRuleVersion("v4-row-warehouse-scope");
            balances.add(balance);
        }

        if (!balances.isEmpty()) {
            balanceMapper.deleteBySnapshotId(snapshotId);
            balanceMapper.insertBatch(balances);
            log.info("Normalized {} material balances from snapshot {} ({} GOOD, {} DEFECTIVE, {} zero-qty)",
                    balances.size(), snapshotId,
                    balances.stream().filter(b -> "GOOD".equals(b.getInventoryQuality()) && Boolean.TRUE.equals(b.getIncludedFlag())).count(),
                    balances.stream().filter(b -> "DEFECTIVE".equals(b.getInventoryQuality())).count(),
                    balances.stream().filter(b -> BigDecimal.ZERO.equals(b.getAvailableQuantity())).count());
        }

        return balances;
    }

    public List<MrpInventoryBalance> getNormalizedBalances(Long snapshotId) {
        return balanceMapper.selectBySnapshotId(snapshotId);
    }

    public Map<String, BigDecimal> buildCalculableInventoryMap(Long snapshotId, Long stockPolicyVersionId) {
        return buildCalculableInventoryMap(snapshotId, stockPolicyVersionId, null);
    }

    public Map<String, BigDecimal> buildCalculableInventoryMap(
            Long snapshotId, Long stockPolicyVersionId, String factoryCode) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        Map<WarehouseMaterialKey, StockPolicyDetail> policies = new LinkedHashMap<>();
        if (stockPolicyVersionId != null) for (StockPolicyDetail policy : scopeConfigMapper.selectStockPolicyDetails(stockPolicyVersionId))
            policies.put(new WarehouseMaterialKey(policy.getWarehouseCode(), policy.getMaterialCode()), policy);
        // Aggregate each warehouse/material before applying its safety floor exactly once.
        Map<WarehouseMaterialKey, BigDecimal> goodByWarehouse = new LinkedHashMap<>();
        for (MrpInventoryBalance balance : balanceMapper.selectGoodBySnapshotId(snapshotId)) {
            if (factoryCode != null && !"ALL".equals(factoryCode) && balance.getFactoryCode() != null
                    && !factoryCode.equals(balance.getFactoryCode())) continue;
            BigDecimal available = balance.getAvailableQuantity() == null ? BigDecimal.ZERO : balance.getAvailableQuantity();
            goodByWarehouse.merge(new WarehouseMaterialKey(balance.getWarehouseScope(), balance.getMaterialCode()), available, BigDecimal::add);
        }
        for (var entry : goodByWarehouse.entrySet()) {
            WarehouseMaterialKey key = entry.getKey();
            StockPolicyDetail policy = stockPolicyVersionId == null
                    ? scopeConfigMapper.selectPublishedStockPolicy(key.warehouse(), key.material()) : policies.get(key);
            BigDecimal safety = policy == null || policy.getSafetyStockQty() == null ? BigDecimal.ZERO : policy.getSafetyStockQty();
            result.merge(key.material() + "|GOOD", entry.getValue().subtract(safety).max(BigDecimal.ZERO), BigDecimal::add);
        }
        return result;
    }

    @Transactional
    public List<MrpInventoryPool> createPoolsFromSnapshot(Long batchId, Long snapshotId, String factoryCode) {
        return createPoolsFromSnapshot(batchId, snapshotId, factoryCode, null);
    }

    @Transactional
    public List<MrpInventoryPool> createPoolsFromSnapshot(Long batchId, Long snapshotId, String factoryCode,
                                                           Long stockPolicyVersionId) {
        return createPoolsFromSnapshot(batchId, snapshotId, factoryCode, stockPolicyVersionId, null);
    }

    @Transactional
    public List<MrpInventoryPool> createPoolsFromSnapshot(Long batchId, Long snapshotId, String factoryCode,
                                                           Long stockPolicyVersionId, java.util.Set<String> warehouseCodes) {
        List<MrpInventoryBalance> balances = balanceMapper.selectAggregatedBySnapshotId(snapshotId);
        if (balances.isEmpty()) {
            log.warn("No normalized balances found for snapshot {}", snapshotId);
            return List.of();
        }

        Map<WarehouseMaterialKey, StockPolicyDetail> lockedPolicies = new LinkedHashMap<>();
        if (stockPolicyVersionId != null) {
            for (StockPolicyDetail detail : scopeConfigMapper.selectStockPolicyDetails(stockPolicyVersionId)) {
                lockedPolicies.put(new WarehouseMaterialKey(detail.getWarehouseCode(), detail.getMaterialCode()), detail);
            }
        }

        List<MrpInventoryPool> pools = new ArrayList<>();
        for (MrpInventoryBalance balance : balances) {
            if (warehouseCodes != null && !warehouseCodes.contains(balance.getWarehouseScope())) continue;
            if (factoryCode != null && !"ALL".equals(factoryCode) && balance.getFactoryCode() != null
                    && !factoryCode.equals(balance.getFactoryCode())) continue;
            MrpInventoryPool pool = new MrpInventoryPool();
            pool.setBatchId(batchId);
            pool.setSnapshotId(snapshotId);
            pool.setFactoryCode(balance.getFactoryCode() != null ? balance.getFactoryCode() : factoryCode);
            pool.setWarehouseCode(balance.getWarehouseScope());
            pool.setInventoryOrg(balance.getInventoryOrg());
            pool.setMaterialCode(balance.getMaterialCode());
            pool.setAvailableQty(balance.getAvailableQuantity());
            StockPolicyDetail policy = stockPolicyVersionId == null
                    ? scopeConfigMapper.selectPublishedStockPolicy(balance.getWarehouseScope(), balance.getMaterialCode())
                    : lockedPolicies.get(new WarehouseMaterialKey(balance.getWarehouseScope(), balance.getMaterialCode()));
            // 安全库存是硬保底；预警库存仅提示，未维护物料默认两条线均为 0。
            BigDecimal safety = policy == null || policy.getSafetyStockQty() == null
                    ? BigDecimal.ZERO : policy.getSafetyStockQty();
            BigDecimal warning = policy == null || policy.getWarningStockQty() == null
                    ? BigDecimal.ZERO : policy.getWarningStockQty();
            pool.setReservedQty(safety);
            pool.setWarningQty(warning);
            pool.setAllocatedQty(BigDecimal.ZERO);
            pool.setVersion(0);
            poolMapper.insert(pool);
            pools.add(pool);
        }

        log.info("Created {} inventory pools for batch {}", pools.size(), batchId);
        return pools;
    }

    public List<MrpInventoryPool> getPoolsByBatch(Long batchId) {
        return poolMapper.selectByBatchId(batchId);
    }

    public MrpInventoryPool getPoolById(Long poolId) {
        return poolMapper.selectById(poolId);
    }

    @Transactional
    public MrpInventoryAllocation previewAllocation(Long poolId, String demandKey, String rootMaterialCode,
                                                     java.time.LocalDate sourceMonth, String pathHash,
                                                     BigDecimal grossQty) {
        MrpInventoryPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new IllegalStateException("Pool not found: " + poolId);
        }

        BigDecimal remaining = pool.getAvailableQty()
                .subtract(pool.getReservedQty() == null ? BigDecimal.ZERO : pool.getReservedQty())
                .subtract(pool.getAllocatedQty()).max(BigDecimal.ZERO);

        BigDecimal allocatable = grossQty.min(remaining);
        if (allocatable.compareTo(BigDecimal.ZERO) < 0) {
            allocatable = BigDecimal.ZERO;
        }

        MrpInventoryAllocation allocation = new MrpInventoryAllocation();
        allocation.setPoolId(poolId);
        allocation.setDemandKey(demandKey);
        allocation.setRootMaterialCode(rootMaterialCode);
        allocation.setSourceMonth(sourceMonth);
        allocation.setPathHash(pathHash);
        allocation.setGrossQty(grossQty);
        allocation.setAllocatedQty(allocatable);
        allocation.setAllocationType("AUTO_PRIORITY");
        allocation.setManualConfirmed(false);
        allocation.setRevision(0);

        return allocation;
    }

    @Transactional
    public void updateAllocation(Long allocationId, BigDecimal newAllocatedQty, Integer expectedRevision,
                                 String adjustmentReason) {
        MrpInventoryAllocation allocationRef = allocationMapper.selectById(allocationId);
        if (allocationRef == null) {
            throw new IllegalStateException("Allocation not found: " + allocationId);
        }
        MrpInventoryPool poolRef = poolMapper.selectById(allocationRef.getPoolId());
        if (poolRef == null) {
            throw new IllegalStateException("Pool not found: " + allocationRef.getPoolId());
        }
        com.mrp.mrp.domain.MrpCalcBatch batch = batchMapper.selectForUpdate(poolRef.getBatchId());
        if (batch == null || !(List.of("DRAFT", "WAITING_DECISION", "READY_TO_CALCULATE", "REANALYZING")
                .contains(batch.getStatus()) || ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())
                && List.of("MATERIAL_REVIEW", "READY", "MATERIAL_RECALC_REQUIRED").contains(batch.getStatus()))) {
            throw new IllegalStateException("当前排产状态不允许调整库存分配");
        }
        // The first reads identify the owner; after the batch lock, reload current rows.
        MrpInventoryAllocation allocation = allocationMapper.selectById(allocationId);
        MrpInventoryPool pool = poolMapper.selectById(poolRef.getId());
        if (allocation == null || pool == null || !pool.getId().equals(allocation.getPoolId())
                || !batch.getId().equals(pool.getBatchId())) {
            throw new com.mrp.common.exception.ConcurrentChangeException("库存分配已变化，请刷新后重试");
        }
        boolean materialReview = ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())
                && List.of("MATERIAL_REVIEW", "READY", "MATERIAL_RECALC_REQUIRED").contains(batch.getStatus());
        if (materialReview && !MaterialAnalysisStageService.isMaterialAllocation(allocation,pool))
            throw new IllegalStateException("整机库存分配只能由排产员在整机阶段调整");

        if (expectedRevision == null || !expectedRevision.equals(allocation.getRevision())) {
            throw new com.mrp.common.exception.ConcurrentChangeException("库存分配已被其他操作修改，请刷新后重试");
        }
        if (adjustmentReason == null || adjustmentReason.isBlank()) throw new IllegalArgumentException("人工调整必须填写原因");
        if (newAllocatedQty == null || newAllocatedQty.signum() < 0) throw new IllegalArgumentException("分配数量不能为负数");
        if (allocation.getGrossQty() != null && newAllocatedQty.compareTo(allocation.getGrossQty()) > 0)
            throw new IllegalArgumentException("分配数量不能超过该笔需求数量");
        BigDecimal diff = newAllocatedQty.subtract(allocation.getAllocatedQty());
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = pool.getAvailableQty()
                    .subtract(pool.getReservedQty() == null ? BigDecimal.ZERO : pool.getReservedQty())
                .subtract(pool.getAllocatedQty()).max(BigDecimal.ZERO);
            if (diff.compareTo(remaining) > 0) {
                throw new IllegalStateException("Increase exceeds available balance");
            }
        }

        BigDecimal previousQty = allocation.getAllocatedQty();
        String previousReason = allocation.getAdjustmentReason();
        allocation.setAllocatedQty(newAllocatedQty);
        allocation.setManualConfirmed(true);
        allocation.setAllocationType("MANUAL_OVERRIDE");
        allocation.setAdjustmentReason(adjustmentReason.trim());
        allocation.setRevision(allocation.getRevision() + 1);
        if (allocationMapper.update(allocation) == 0) {
            throw new com.mrp.common.exception.ConcurrentChangeException("库存分配已被其他操作修改，请刷新后重试");
        }

        pool.setAllocatedQty(pool.getAllocatedQty().add(diff));
        if (poolMapper.update(pool) == 0) {
            throw new com.mrp.common.exception.ConcurrentChangeException("库存池已被其他操作修改，请刷新后重试");
        }
        batchMapper.updateStatusForce(batch.getId(), materialReview ? "MATERIAL_RECALC_REQUIRED" : "REANALYZING");
        if (materialReview) reviews.insert(batch.getId(), "MATERIAL", batch.getActiveRunId(),
                "INVALIDATED_BY_ALLOCATION", null, CurrentUser.getUserId());
        audit.log(CurrentUser.getUserId(), null, batch.getFactoryCode(),
                "OVERRIDE_INVENTORY_ALLOCATION", "mrp_inventory_allocation", allocationId.toString(),
                Map.of("allocatedQty", previousQty, "adjustmentReason", previousReason == null ? "" : previousReason),
                Map.of("allocatedQty", newAllocatedQty, "adjustmentReason", adjustmentReason.trim()), null, null);
    }

    @Transactional
    public void resetAllocation(Long allocationId) {
        MrpInventoryAllocation allocationRef = allocationMapper.selectById(allocationId);
        if (allocationRef == null) {
            throw new IllegalStateException("Allocation not found: " + allocationId);
        }
        MrpInventoryPool poolRef = poolMapper.selectById(allocationRef.getPoolId());
        if (poolRef == null) throw new IllegalStateException("Pool not found: " + allocationRef.getPoolId());
        com.mrp.mrp.domain.MrpCalcBatch batch = batchMapper.selectForUpdate(poolRef.getBatchId());
        if (batch == null || !(List.of("DRAFT", "WAITING_DECISION", "READY_TO_CALCULATE", "REANALYZING")
                .contains(batch.getStatus()) || ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())
                && List.of("MATERIAL_REVIEW", "READY", "MATERIAL_RECALC_REQUIRED").contains(batch.getStatus()))) {
            throw new IllegalStateException("当前排产状态不允许恢复自动分配");
        }
        MrpInventoryAllocation allocation = allocationMapper.selectById(allocationId);
        MrpInventoryPool pool = poolMapper.selectById(poolRef.getId());
        if (allocation == null || pool == null || !pool.getId().equals(allocation.getPoolId())
                || !batch.getId().equals(pool.getBatchId())) {
            throw new com.mrp.common.exception.ConcurrentChangeException("库存分配已变化，请刷新后重试");
        }
        boolean materialReview = ProductionCapacityRules.VERSION.equals(batch.getRuleVersion())
                && List.of("MATERIAL_REVIEW", "READY", "MATERIAL_RECALC_REQUIRED").contains(batch.getStatus());
        if (materialReview && !MaterialAnalysisStageService.isMaterialAllocation(allocation,pool))
            throw new IllegalStateException("整机库存分配只能由排产员在整机阶段调整");
        if (!Boolean.TRUE.equals(allocation.getManualConfirmed())
                && !"MANUAL_OVERRIDE".equals(allocation.getAllocationType())) return;

        BigDecimal previousQty = allocation.getAllocatedQty();
        allocation.setAllocatedQty(BigDecimal.ZERO);
        allocation.setAllocationType("AUTO_PRIORITY");
        allocation.setAdjustmentReason(null);
        allocation.setManualConfirmed(false);
        allocation.setRevision(allocation.getRevision() + 1);
        if (allocationMapper.update(allocation) == 0) {
            throw new com.mrp.common.exception.ConcurrentChangeException("库存分配已被其他操作修改，请刷新后重试");
        }
        pool.setAllocatedQty(pool.getAllocatedQty().subtract(previousQty));
        if (poolMapper.update(pool) == 0) {
            throw new com.mrp.common.exception.ConcurrentChangeException("库存池已被其他操作修改，请刷新后重试");
        }
        batchMapper.updateStatusForce(batch.getId(), materialReview ? "MATERIAL_RECALC_REQUIRED" : "REANALYZING");
        if (materialReview) reviews.insert(batch.getId(), "MATERIAL", batch.getActiveRunId(),
                "INVALIDATED_BY_ALLOCATION", null, CurrentUser.getUserId());
        audit.log(CurrentUser.getUserId(), null, batch.getFactoryCode(), "RESET_INVENTORY_ALLOCATION",
                "mrp_inventory_allocation", allocationId.toString(),
                Map.of("allocatedQty", previousQty, "manualOverride", true),
                Map.of("allocatedQty", BigDecimal.ZERO, "manualOverride", false), null, null);
    }

    public List<MrpInventoryAllocation> getAllocationsByPool(Long poolId) {
        return allocationMapper.selectByPoolId(poolId);
    }

    public List<MrpInventoryAllocation> getAllocationsByBatch(Long batchId) {
        return allocationMapper.selectByBatchId(batchId);
    }
}
