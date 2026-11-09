package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.mrp.domain.MrpInventoryAllocation;
import com.mrp.mrp.domain.MrpInventoryBalance;
import com.mrp.mrp.domain.MrpInventoryPool;
import com.mrp.mrp.domain.MrpStockRisk;
import com.mrp.mrp.repository.MrpInventoryPoolMapper;
import com.mrp.mrp.repository.MrpInventoryAllocationMapper;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.service.InventoryNormalizationService;
import com.mrp.mrp.service.StockRiskService;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.service.InventorySnapshotAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/mrp/inventory")
public class MrpInventoryController {

    private final InventoryNormalizationService normalizationService;
    private final InventorySnapshotAccessService snapshotAccess;
    private final StockRiskService stockRiskService;
    private final com.mrp.mrp.service.PlanningFactoryAccessService factoryAccess;
    private final MrpInventoryPoolMapper poolMapper;
    private final MrpInventoryAllocationMapper allocationMapper;

    public MrpInventoryController(InventoryNormalizationService normalizationService,
                                   InventorySnapshotAccessService snapshotAccess,
                                   StockRiskService stockRiskService, com.mrp.mrp.service.PlanningFactoryAccessService factoryAccess,
                                   MrpInventoryPoolMapper poolMapper, MrpInventoryAllocationMapper allocationMapper) {
        this.factoryAccess = factoryAccess;
        this.normalizationService = normalizationService;
        this.snapshotAccess = snapshotAccess;
        this.stockRiskService = stockRiskService;
        this.poolMapper = poolMapper;
        this.allocationMapper = allocationMapper;
    }

    @GetMapping("/snapshots")
    public ResponseEntity<ApiResponse<List<InventorySnapshot>>> getInventorySnapshots(
            @RequestParam(required = false) String warehouseCode) {
        List<InventorySnapshot> snapshots = snapshotAccess.list(warehouseCode == null ? null : warehouseCode.trim());
        return ResponseEntity.ok(ApiResponse.ok(snapshots));
    }

    @PostMapping("/normalize/{snapshotId}")
    public ResponseEntity<ApiResponse<List<MrpInventoryBalance>>> normalize(
            @PathVariable Long snapshotId,
            @RequestParam String factoryCode,
            @RequestParam String inventoryOrg) {
        List<MrpInventoryBalance> balances = snapshotAccess.normalize(snapshotId, factoryCode, inventoryOrg);
        return ResponseEntity.ok(ApiResponse.ok(balances));
    }

    @GetMapping("/balances/{snapshotId}")
    public ResponseEntity<ApiResponse<List<MrpInventoryBalance>>> getBalances(@PathVariable Long snapshotId) {
        List<MrpInventoryBalance> balances = snapshotAccess.balances(snapshotId);
        return ResponseEntity.ok(ApiResponse.ok(balances));
    }

    @GetMapping("/stock-risks/{batchId}")
    public ResponseEntity<ApiResponse<List<MrpStockRisk>>> getStockRisks(@PathVariable Long batchId,
                                                                          @RequestParam(required = false) Long runId) {
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(stockRiskService.list(batchId, runId)));
    }

    @GetMapping("/stock-risks/{batchId}/page")
    public ResponseEntity<ApiResponse<PageResult<MrpStockRisk>>> getStockRisksPage(@PathVariable Long batchId,
            @RequestParam(required = false) Long runId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(stockRiskService.page(batchId, runId, page, pageSize)));
    }

    @PostMapping("/stock-risks/{batchId}/apply-zero-thresholds")
    public ResponseEntity<ApiResponse<Integer>> applyZeroThresholds(@PathVariable Long batchId,
            @RequestParam Long runId) {
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(stockRiskService.applyZeroThresholdForRun(batchId, runId)));
    }

    @GetMapping("/pools/{batchId}")
    public ResponseEntity<ApiResponse<List<MrpInventoryPool>>> getPools(@PathVariable Long batchId) {
        factoryAccess.requireBatch(batchId);
        List<MrpInventoryPool> pools = normalizationService.getPoolsByBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(pools));
    }

    @GetMapping("/pools/{batchId}/page")
    public ResponseEntity<ApiResponse<PageResult<MrpInventoryPool>>> getPoolsPage(@PathVariable Long batchId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        factoryAccess.requireBatch(batchId);
        int total = poolMapper.countByBatchId(batchId);
        return ResponseEntity.ok(ApiResponse.ok(new PageResult<>(poolMapper.selectPageByBatchId(batchId,
                (long) (page - 1) * pageSize, pageSize), total, page, pageSize)));
    }

    @GetMapping("/allocations/pool/{poolId}/count")
    public ResponseEntity<ApiResponse<Integer>> getAllocationCount(@PathVariable Long poolId) {
        factoryAccess.requireResource("pool", poolId);
        return ResponseEntity.ok(ApiResponse.ok(allocationMapper.countByPoolId(poolId)));
    }

    @GetMapping("/pools/detail/{poolId}")
    public ResponseEntity<ApiResponse<MrpInventoryPool>> getPool(@PathVariable Long poolId) {
        factoryAccess.requireResource("pool", poolId);
        MrpInventoryPool pool = normalizationService.getPoolById(poolId);
        if (pool == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(pool));
    }

    @PutMapping("/allocations/{allocationId}")
    public ResponseEntity<ApiResponse<Void>> updateAllocation(
            @PathVariable Long allocationId,
            @RequestParam BigDecimal allocatedQty,
            @RequestParam Integer expectedRevision,
            @RequestParam String reason) {
        factoryAccess.requireResource("allocation", allocationId);
        normalizationService.updateAllocation(allocationId, allocatedQty, expectedRevision, reason);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/allocations/{allocationId}/reset")
    public ResponseEntity<ApiResponse<Void>> resetAllocation(@PathVariable Long allocationId) {
        factoryAccess.requireResource("allocation", allocationId);
        normalizationService.resetAllocation(allocationId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/allocations/pool/{poolId}")
    public ResponseEntity<ApiResponse<List<MrpInventoryAllocation>>> getAllocationsByPool(@PathVariable Long poolId) {
        factoryAccess.requireResource("pool", poolId);
        List<MrpInventoryAllocation> allocations = normalizationService.getAllocationsByPool(poolId);
        return ResponseEntity.ok(ApiResponse.ok(allocations));
    }

    @GetMapping("/allocations/batch/{batchId}")
    public ResponseEntity<ApiResponse<List<MrpInventoryAllocation>>> getAllocationsByBatch(@PathVariable Long batchId) {
        factoryAccess.requireBatch(batchId);
        List<MrpInventoryAllocation> allocations = normalizationService.getAllocationsByBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(allocations));
    }
}
