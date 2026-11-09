package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.mrp.service.BomSnapshotService;
import com.mrp.mrp.service.MrpCalculationService;
import com.mrp.mrp.service.PlanningFactoryAccessService;
import com.mrp.common.response.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mrp")
public class MrpCalculationController {

    private final BomSnapshotService bomSnapshotService;
    private final MrpCalculationService calculationService;
    private final PlanningFactoryAccessService factoryAccess;

    public MrpCalculationController(BomSnapshotService bomSnapshotService, MrpCalculationService calculationService,
                                    PlanningFactoryAccessService factoryAccess) {
        this.bomSnapshotService = bomSnapshotService;
        this.calculationService = calculationService;
        this.factoryAccess = factoryAccess;
    }

    @GetMapping("/bom-snapshots")
    public ResponseEntity<ApiResponse<List<BomSnapshot>>> getBomSnapshots() {
        List<BomSnapshot> snapshots = bomSnapshotService.getAllSnapshots();
        return ResponseEntity.ok(ApiResponse.ok(snapshots));
    }

    @GetMapping("/bom-snapshots/current")
    public ResponseEntity<ApiResponse<BomSnapshot>> getCurrentBomSnapshot() {
        BomSnapshot snapshot = bomSnapshotService.getCurrentSnapshot();
        if (snapshot == null) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("CURRENT_BOM_SNAPSHOT_NOT_FOUND", "尚未生成可用的 BOM 快照"));
        }
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }

    @GetMapping("/bom-snapshots/{id}")
    public ResponseEntity<ApiResponse<BomSnapshot>> getBomSnapshot(@PathVariable Long id) {
        BomSnapshot snapshot = bomSnapshotService.getSnapshot(id);
        if (snapshot == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }

    @GetMapping("/bom-snapshots/{id}/details")
    public ResponseEntity<ApiResponse<List<BomSnapshotDetail>>> getBomSnapshotDetails(@PathVariable Long id) {
        List<BomSnapshotDetail> details = bomSnapshotService.getSnapshotDetails(id);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }

    @GetMapping("/bom-snapshots/{id}/children/{parentCode}")
    public ResponseEntity<ApiResponse<List<BomSnapshotDetail>>> getChildren(
            @PathVariable Long id, @PathVariable String parentCode) {
        List<BomSnapshotDetail> children = bomSnapshotService.getChildren(id, parentCode);
        return ResponseEntity.ok(ApiResponse.ok(children));
    }

    @GetMapping("/batches/{batchId}/requirements")
    public ResponseEntity<ApiResponse<List<MrpRequirementDetail>>> getRequirements(@PathVariable Long batchId) {
        factoryAccess.requireBatch(batchId);
        List<MrpRequirementDetail> details = calculationService.getRequirementDetails(batchId);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }

    @GetMapping("/batches/{batchId}/requirements/page")
    public ResponseEntity<ApiResponse<PageResult<MrpRequirementDetail>>> getRequirementsPage(@PathVariable Long batchId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(calculationService.getRequirementDetailsPage(batchId, page, pageSize)));
    }

    @GetMapping("/batches/{batchId}/terminal-demands")
    public ResponseEntity<ApiResponse<List<MrpTerminalDemand>>> getTerminalDemands(@PathVariable Long batchId) {
        factoryAccess.requireBatch(batchId);
        List<MrpTerminalDemand> demands = calculationService.getTerminalDemands(batchId);
        return ResponseEntity.ok(ApiResponse.ok(demands));
    }

    @GetMapping("/batches/{batchId}/terminal-demands/page")
    public ResponseEntity<ApiResponse<PageResult<MrpTerminalDemand>>> getTerminalDemandsPage(@PathVariable Long batchId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(calculationService.getTerminalDemandsPage(batchId, keyword, page, pageSize)));
    }

    @GetMapping("/batches/{batchId}/shortage/page")
    public ResponseEntity<ApiResponse<PageResult<MrpTerminalDemand>>> getShortagePage(@PathVariable Long batchId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String factoryCode, @RequestParam(required=false) String rootMaterialCode,
            @RequestParam(required=false) String terminalMaterialCode, @RequestParam(required=false) LocalDate sourceMonth,
            @RequestParam(required=false) String keyword, @RequestParam(defaultValue="false") boolean detail) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("分页参数不合法");
        factoryAccess.requireBatch(batchId);
        return ResponseEntity.ok(ApiResponse.ok(calculationService.getShortagePage(batchId, factoryCode,
                rootMaterialCode, terminalMaterialCode, sourceMonth, keyword, detail, page, pageSize)));
    }

    @GetMapping("/batches/{batchId}/shortage/pools")
    public ResponseEntity<ApiResponse<PageResult<MrpInventoryPool>>> getShortagePools(@PathVariable Long batchId,
            @RequestParam String factoryCode, @RequestParam String terminalMaterialCode,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || factoryCode.isBlank() || terminalMaterialCode.isBlank())
            throw new IllegalArgumentException("缺料库存池查询参数不合法");
        factoryAccess.requireBatchFactory(batchId, factoryCode);
        return ResponseEntity.ok(ApiResponse.ok(calculationService.getShortagePools(batchId, factoryCode,
                terminalMaterialCode, page, pageSize)));
    }

    @GetMapping("/batches/{batchId}/terminal-demands/aggregated")
    public ResponseEntity<ApiResponse<List<MrpTerminalDemand>>> getTerminalDemandsAggregated(@PathVariable Long batchId) {
        List<MrpTerminalDemand> demands = calculationService.getTerminalDemandsAggregated(batchId);
        return ResponseEntity.ok(ApiResponse.ok(demands));
    }

    public static class RootDemandRequest {
        public String materialCode;
        public BigDecimal quantity;
        public LocalDate sourceMonth;
    }
}
