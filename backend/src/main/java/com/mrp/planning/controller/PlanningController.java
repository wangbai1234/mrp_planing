package com.mrp.planning.controller;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.importexport.service.ExportService;
import com.mrp.planning.domain.*;
import com.mrp.planning.service.AsyncCalculationService;
import com.mrp.planning.service.PlanningDomainService;
import com.mrp.planning.service.PlanningService;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.domain.ExportTask;
import com.mrp.task.repository.CalcTaskMapper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PlanningController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlanningController.class);

    private final PlanningService planningService;
    private final PlanningDomainService domainService;
    private final ExportService exportService;
    private final CalcTaskMapper calcTaskMapper;
    private final AsyncCalculationService asyncCalculationService;

    public PlanningController(PlanningService planningService, PlanningDomainService domainService,
                              ExportService exportService, CalcTaskMapper calcTaskMapper,
                              AsyncCalculationService asyncCalculationService) {
        this.planningService = planningService;
        this.domainService = domainService;
        this.exportService = exportService;
        this.calcTaskMapper = calcTaskMapper;
        this.asyncCalculationService = asyncCalculationService;
    }

    // === 版本列表 ===

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanVersion>>> listVersions(
            @RequestParam(required = false) String factoryCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<PlanVersion> versions;
        if (factoryCode != null && !factoryCode.isBlank()) {
            versions = domainService.listVersions(factoryCode);
        } else {
            versions = domainService.listVersions(null);
        }
        int from = Math.min((page - 1) * pageSize, versions.size());
        int to = Math.min(from + pageSize, versions.size());
        return ResponseEntity.ok(ApiResponse.ok(versions.subList(from, to)));
    }

    // === 当前排产 ===

    @GetMapping("/plans/current")
    public ResponseEntity<ApiResponse<List<PlanVersion>>> getCurrentPlan(
            @RequestParam(required = false) String factoryCode) {
        List<PlanVersion> versions;
        if (factoryCode != null && !factoryCode.isBlank()) {
            PlanVersion version = planningService.getLatestPlan(factoryCode);
            versions = version != null ? List.of(version) : List.of();
        } else {
            versions = planningService.getAllLatestPlans();
        }
        return ResponseEntity.ok(ApiResponse.ok(versions));
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
        log.info("收到重算请求: {}", request);
        
        String factoryCode = request.get("factoryCode") != null ? request.get("factoryCode").toString() : null;
        LocalDate currentWeekStart = LocalDate.parse(request.get("currentWeekStart").toString());
        
        // 如果没有提供 forecastVersionId，自动获取最新版本
        Long forecastVersionId;
        if (request.get("forecastVersionId") != null) {
            forecastVersionId = Long.valueOf(request.get("forecastVersionId").toString());
        } else {
            forecastVersionId = planningService.getLatestForecastVersionId();
            if (forecastVersionId == null) {
                throw new BusinessException("NO_FORECAST", "请先导入经营计划");
            }
        }
        
        Long inventorySnapshotId = request.get("inventorySnapshotId") != null
                ? Long.valueOf(request.get("inventorySnapshotId").toString()) : null;
        Long shipmentBatchId = request.get("shipmentBatchId") != null
                ? Long.valueOf(request.get("shipmentBatchId").toString()) : null;
        Long capacityVersionId = request.get("capacityVersionId") != null
                ? Long.valueOf(request.get("capacityVersionId").toString()) : null;

        // 读取拆分维度
        @SuppressWarnings("unchecked")
        List<String> splitCategories = request.get("splitCategories") != null
                ? (List<String>) request.get("splitCategories") : null;
        boolean useBomExplosion = splitCategories != null && !splitCategories.isEmpty();

        // 构建 requestKey（含拆分维度以区分不同拆分的重算）
        String splitKey = useBomExplosion ? ":" + String.join(",", splitCategories) : "";

        // 当 factoryCode 为空时，为所有工厂生成排产版本
        if (factoryCode == null || factoryCode.isBlank()) {
            log.info("factoryCode 为空，为所有工厂生成排产版本");
            String requestKey = "recalc:all:" + forecastVersionId + ":" + inventorySnapshotId + ":" + currentWeekStart + splitKey;
            CalcTask existing = calcTaskMapper.selectByRequestKey(requestKey);
            if (existing != null && CalcTask.STATUS_SUCCEEDED.equals(existing.status())) {
                return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                        "taskId", existing.id(), "status", existing.status(),
                        "planVersionId", existing.resultResourceId())));
            }
            // 删除失败的任务，允许重新提交
            if (existing != null && CalcTask.STATUS_FAILED.equals(existing.status())) {
                calcTaskMapper.deleteById(existing.id());
            }

            CalcTask task = new CalcTask(null, "RECALCULATION", "ALL", requestKey,
                    CalcTask.STATUS_PENDING, 0, null, null, null, 0, 0, 0, 3, null,
                    null, null, null, null, null, CurrentUser.getUserId(), null, null, null, 0);
            calcTaskMapper.insert(task);

            Long userId = CurrentUser.getUserId();
            if (useBomExplosion) {
                asyncCalculationService.executeRecalculationAllFactoriesWithBomExplosion(
                        task.id(), forecastVersionId, inventorySnapshotId, shipmentBatchId,
                        capacityVersionId, currentWeekStart, splitCategories, userId);
            } else {
                asyncCalculationService.executeRecalculationAllFactories(
                        task.id(), forecastVersionId, inventorySnapshotId, shipmentBatchId,
                        capacityVersionId, currentWeekStart, userId);
            }

            return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                    "taskId", task.id(), "status", CalcTask.STATUS_PENDING)));
        }

        String requestKey = "recalc:" + forecastVersionId + ":" + factoryCode + ":" + inventorySnapshotId + ":" + currentWeekStart + splitKey;
        log.info("请求键: {}, forecastVersionId: {}", requestKey, forecastVersionId);
        
        CalcTask existing = calcTaskMapper.selectByRequestKey(requestKey);
        if (existing != null && CalcTask.STATUS_SUCCEEDED.equals(existing.status())) {
            return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                    "taskId", existing.id(), "status", existing.status(),
                    "planVersionId", existing.resultResourceId())));
        }
        // 删除失败的任务，允许重新提交
        if (existing != null && CalcTask.STATUS_FAILED.equals(existing.status())) {
            calcTaskMapper.deleteById(existing.id());
        }

        CalcTask task = new CalcTask(null, "RECALCULATION", factoryCode, requestKey,
                CalcTask.STATUS_PENDING, 0, null, null, null, 0, 0, 0, 3, null,
                null, null, null, null, null, CurrentUser.getUserId(), null, null, null, 0);
        calcTaskMapper.insert(task);

        Long userId = CurrentUser.getUserId();
        if (useBomExplosion) {
            asyncCalculationService.executeRecalculationWithBomExplosion(
                    task.id(), forecastVersionId, inventorySnapshotId, shipmentBatchId,
                    capacityVersionId, factoryCode, currentWeekStart, splitCategories, userId);
        } else {
            asyncCalculationService.executeRecalculation(
                    task.id(), forecastVersionId, inventorySnapshotId, shipmentBatchId,
                    capacityVersionId, factoryCode, currentWeekStart, userId);
        }

        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                "taskId", task.id(), "status", CalcTask.STATUS_PENDING)));
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

        PlanOverride ov = domainService.saveOverride(id, materialId, weekStartDate, manualQuantity, reason, CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(ov));
    }

    @DeleteMapping("/plans/{id}/overrides/{materialId}")
    public ResponseEntity<ApiResponse<Void>> restoreAutoValue(
            @PathVariable Long id, @PathVariable String materialId,
            @RequestParam String weekStartDate) {
        domainService.restoreAutoValue(id, materialId, LocalDate.parse(weekStartDate), CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // === 发布 ===

    @PostMapping("/plans/{id}/publish")
    public ResponseEntity<ApiResponse<PlanVersion>> publish(@PathVariable Long id) {
        PlanVersion version = domainService.publish(id, CurrentUser.getUserId());
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
        String factoryCode = request.get("factoryCode") != null ? request.get("factoryCode").toString() : null;

        ExportTask task = exportService.createExportTask(id, factoryCode, fields, includePriority, includeActual, CurrentUser.getUserId());
        return ResponseEntity.accepted().body(ApiResponse.ok(task));
    }

    @GetMapping("/export-tasks/{id}")
    public ResponseEntity<ApiResponse<ExportTask>> getExportTask(@PathVariable Long id) {
        ExportTask task = exportService.getExportTask(id);
        if (task == null) {
            throw new BusinessException("MRP_NOT_FOUND", "Export task not found: " + id);
        }
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @GetMapping("/export-tasks/{id}/download")
    public ResponseEntity<Resource> downloadExport(@PathVariable Long id) {
        ExportTask task = exportService.getExportTask(id);
        if (task == null || task.getFilePath() == null) {
            throw new BusinessException("MRP_NOT_FOUND", "Export file not found");
        }
        
        Path filePath = Paths.get(task.getFilePath());
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new BusinessException("MRP_NOT_FOUND", "Export file not found on disk");
        }
        
        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
