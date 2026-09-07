package com.mrp.planning.controller;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.response.ApiResponse;
import com.mrp.importexport.service.ExportService;
import com.mrp.planning.domain.*;
import com.mrp.planning.service.PlanningDomainService;
import com.mrp.planning.service.PlanningService;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.domain.ExportTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PlanningController {

    private final PlanningService planningService;
    private final PlanningDomainService domainService;
    private final ExportService exportService;
    private final CalcTaskMapper calcTaskMapper;

    public PlanningController(PlanningService planningService, PlanningDomainService domainService,
                              ExportService exportService, CalcTaskMapper calcTaskMapper) {
        this.planningService = planningService;
        this.domainService = domainService;
        this.exportService = exportService;
        this.calcTaskMapper = calcTaskMapper;
    }

    // === 版本列表 ===

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanVersion>>> listVersions(
            @RequestParam String factoryCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<PlanVersion> versions = domainService.listVersions(factoryCode);
        int from = Math.min((page - 1) * pageSize, versions.size());
        int to = Math.min(from + pageSize, versions.size());
        return ResponseEntity.ok(ApiResponse.ok(versions.subList(from, to)));
    }

    // === 当前排产 ===

    @GetMapping("/plans/current")
    public ResponseEntity<ApiResponse<PlanVersion>> getCurrentPlan(
            @RequestParam String factoryCode) {
        PlanVersion version = planningService.getLatestPlan(factoryCode);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }

    // === 12 周网格（带 override） ===

    @GetMapping("/plans/{id}/grid")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlanGrid(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(domainService.getPlanGridWithOverrides(id)));
    }

    // === 重算 ===

    @PostMapping("/recalculations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recalculate(
            @RequestBody Map<String, Object> request) {
        Long forecastVersionId = Long.valueOf(request.get("forecastVersionId").toString());
        Long inventorySnapshotId = request.get("inventorySnapshotId") != null
                ? Long.valueOf(request.get("inventorySnapshotId").toString()) : null;
        Long shipmentBatchId = request.get("shipmentBatchId") != null
                ? Long.valueOf(request.get("shipmentBatchId").toString()) : null;
        Long capacityVersionId = request.get("capacityVersionId") != null
                ? Long.valueOf(request.get("capacityVersionId").toString()) : null;
        String factoryCode = request.get("factoryCode").toString();
        LocalDate currentWeekStart = LocalDate.parse(request.get("currentWeekStart").toString());

        String requestKey = "recalc:" + forecastVersionId + ":" + inventorySnapshotId + ":" + currentWeekStart;
        CalcTask existing = calcTaskMapper.selectByRequestKey(requestKey);
        if (existing != null && CalcTask.STATUS_SUCCEEDED.equals(existing.status())) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "taskId", existing.id(), "status", existing.status(),
                    "planVersionId", existing.resultResourceId())));
        }

        CalcTask task = new CalcTask(null, "RECALCULATION", factoryCode, requestKey,
                CalcTask.STATUS_PENDING, 0, null, null, null, 0, 0, 0, 3, null,
                null, null, null, null, null, 1L, null, null, null, 0);
        calcTaskMapper.insert(task);

        try {
            PlanVersion version = planningService.recalculate(
                    forecastVersionId, inventorySnapshotId, shipmentBatchId,
                    capacityVersionId, factoryCode, currentWeekStart, 1L);
            calcTaskMapper.updateResult(task.id(), CalcTask.STATUS_SUCCEEDED, version.id(), 0);
            return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                    "taskId", task.id(), "status", CalcTask.STATUS_SUCCEEDED,
                    "planVersionId", version.id())));
        } catch (Exception e) {
            calcTaskMapper.updateFailure(task.id(), CalcTask.STATUS_FAILED, "MRP_CALC_ERROR", e.getMessage(), 0);
            throw new BusinessException("MRP_CALC_ERROR", "Recalculation failed: " + e.getMessage());
        }
    }

    @GetMapping("/recalculations/{id}")
    public ResponseEntity<ApiResponse<CalcTask>> getRecalculation(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(calcTaskMapper.selectById(id)));
    }

    // === 人工调整 ===

    @PostMapping("/plans/{id}/overrides")
    public ResponseEntity<ApiResponse<PlanOverride>> saveOverride(
            @PathVariable Long id, @RequestBody Map<String, Object> request) {
        String materialId = request.get("materialId").toString();
        LocalDate weekStartDate = LocalDate.parse(request.get("weekStartDate").toString());
        Long manualQuantity = Long.valueOf(request.get("manualQuantity").toString());
        String reason = request.get("reason") != null ? request.get("reason").toString() : null;

        PlanOverride ov = domainService.saveOverride(id, materialId, weekStartDate, manualQuantity, reason, 1L);
        return ResponseEntity.ok(ApiResponse.ok(ov));
    }

    @DeleteMapping("/plans/{id}/overrides/{materialId}")
    public ResponseEntity<ApiResponse<Void>> restoreAutoValue(
            @PathVariable Long id, @PathVariable String materialId,
            @RequestParam String weekStartDate) {
        domainService.restoreAutoValue(id, materialId, LocalDate.parse(weekStartDate), 1L);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // === 发布 ===

    @PostMapping("/plans/{id}/publish")
    public ResponseEntity<ApiResponse<PlanVersion>> publish(@PathVariable Long id) {
        PlanVersion version = domainService.publish(id, 1L);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }

    // === 版本对比 ===

    @GetMapping("/plans/compare")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compare(
            @RequestParam Long baseId, @RequestParam Long currentId) {
        return ResponseEntity.ok(ApiResponse.ok(domainService.compareVersions(baseId, currentId)));
    }

    // === 导出 ===

    @PostMapping("/plans/{id}/exports")
    public ResponseEntity<ApiResponse<ExportTask>> createExport(
            @PathVariable Long id, @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> fields = (List<String>) request.get("fields");
        boolean includePriority = Boolean.TRUE.equals(request.get("includePriority"));
        boolean includeActual = Boolean.TRUE.equals(request.get("includeActual"));

        ExportTask task = exportService.createExportTask(id, fields, includePriority, includeActual, 1L);
        return ResponseEntity.accepted().body(ApiResponse.ok(task));
    }

    @GetMapping("/export-tasks/{id}")
    public ResponseEntity<ApiResponse<ExportTask>> getExportTask(@PathVariable Long id) {
        // TODO: implement ExportTaskMapper.selectById
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
