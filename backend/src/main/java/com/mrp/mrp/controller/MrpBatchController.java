package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.capacity.domain.CapacityVersion;
import com.mrp.capacity.repository.CapacityMapper;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.inventory.repository.InventoryItemMapper;
import com.mrp.mrp.domain.CategoryRoleVersion;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.service.BatchOrchestratorService;
import com.mrp.mrp.service.ConfigSnapshotService;
import com.mrp.mrp.service.BomSnapshotService;
import com.mrp.mrp.service.MrpConfigurationService;
import com.mrp.mrp.service.InventoryNormalizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mrp/batches")
public class MrpBatchController {

    private final MrpCalcBatchMapper batchMapper;
    private final BatchOrchestratorService orchestrator;
    private final ForecastMapper forecastMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryItemMapper inventoryItemMapper;
    private final BomSnapshotService bomSnapshotService;
    private final MrpConfigurationService configurationService;
    private final ConfigSnapshotService configSnapshotService;
    private final CapacityMapper capacityMapper;
    private final InventoryNormalizationService inventoryNormalizationService;
    private final com.mrp.mrp.service.PlanningFactoryAccessService factoryAccess;
    private final com.mrp.mrp.service.FactoryMasterService factoryMaster;
    private final com.mrp.mrp.repository.ConfigMasterMapper configMaster;

    public MrpBatchController(MrpCalcBatchMapper batchMapper, BatchOrchestratorService orchestrator,
                              ForecastMapper forecastMapper, InventoryMapper inventoryMapper,
                              InventoryItemMapper inventoryItemMapper,
                              BomSnapshotService bomSnapshotService, MrpConfigurationService configurationService,
                              ConfigSnapshotService configSnapshotService, CapacityMapper capacityMapper,
                              InventoryNormalizationService inventoryNormalizationService,
                              com.mrp.mrp.service.PlanningFactoryAccessService factoryAccess,
                              com.mrp.mrp.service.FactoryMasterService factoryMaster,
                              com.mrp.mrp.repository.ConfigMasterMapper configMaster) {
        this.factoryAccess = factoryAccess;
        this.factoryMaster = factoryMaster;
        this.configMaster = configMaster;
        this.batchMapper = batchMapper;
        this.orchestrator = orchestrator;
        this.forecastMapper = forecastMapper;
        this.inventoryMapper = inventoryMapper;
        this.inventoryItemMapper = inventoryItemMapper;
        this.bomSnapshotService = bomSnapshotService;
        this.configurationService = configurationService;
        this.configSnapshotService = configSnapshotService;
        this.capacityMapper = capacityMapper;
        this.inventoryNormalizationService = inventoryNormalizationService;
    }

    @GetMapping("/current-bom-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentBomStatus() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("localCount", bomSnapshotService.countCurrentBomEdges())));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<MrpCalcBatch>> createBatch(
            @RequestParam String factoryCode,
            @RequestParam(required = false) Long forecastVersionId,
            @RequestParam(required = false) Long inventorySnapshotId,
            @RequestParam(required = false) Long bomSnapshotId,
            @RequestParam(required = false) Long categoryRoleVersionId,
            @RequestParam(required = false) Long materialPolicyVersionId,
            @RequestParam(required = false) Long productionCalendarVersionId,
            @RequestParam(required = false) List<Long> inventorySnapshotIds) {
        factoryCode = factoryCode == null ? null : factoryCode.trim();
        if (factoryCode == null || factoryCode.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("MISSING_FACTORY", "工厂不能为空"));
        }
        MrpCalcBatch current = batchMapper.selectCurrent();
        if (current != null && !List.of("PUBLISHED", "SUPERSEDED", "CANCELLED", "FAILED", "STALE").contains(current.getStatus())) {
            return ResponseEntity.status(409).body(ApiResponse.error(
                    "ACTIVE_BATCH_EXISTS", "当前已有未完成排产批次，请由有权人员先完成或取消"));
        }

        if (!"ALL".equals(factoryCode) && !factoryMaster.enabled(factoryCode)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("FACTORY_NOT_CONFIGURED", "工厂不在业务范围内"));
        }

        List<String> factories = "ALL".equals(factoryCode) ? factoryMaster.enabledCodes() : List.of(factoryCode);
        if (factories.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.error("FACTORY_NOT_CONFIGURED", "尚无启用工厂"));
        for (String factory : factories) factoryAccess.requireFactory(factory);
        if (forecastVersionId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("MISSING_VERSION", "经营计划版本不能为空"));
        }
        if (inventorySnapshotId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("MISSING_VERSION", "库存快照不能为空"));
        }
        ForecastVersion forecastVersion = forecastMapper.selectVersionById(forecastVersionId);
        Set<Long> selectedIds = new LinkedHashSet<>();
        selectedIds.add(inventorySnapshotId);
        if (inventorySnapshotIds != null) selectedIds.addAll(inventorySnapshotIds);
        if (selectedIds.size() != 1) {
            return ResponseEntity.badRequest().body(ApiResponse.error("SINGLE_INVENTORY_VERSION_REQUIRED",
                    "请选择一个统一导入的库存版本，不支持组合多个库存版本"));
        }
        Map<Long, InventorySnapshot> selectedSnapshots = new LinkedHashMap<>();
        for (Long selectedId : selectedIds) {
            InventorySnapshot selected = selectedId == null ? null : inventoryMapper.selectSnapshotById(selectedId);
            if (selected == null || !InventorySnapshot.STATUS_IMPORTED.equals(selected.getStatus())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_INVENTORY_SNAPSHOT", "库存快照不存在或不可用: " + selectedId));
            }
            selectedSnapshots.put(selectedId, selected);
        }
        Set<String> enabledWarehouseCodes = new LinkedHashSet<>();
        for (String enabledFactory : factoryMaster.enabledCodes()) {
            configMaster.selectFactoryWarehouseMappingsByFactory(enabledFactory).stream()
                    .filter(mapping -> Boolean.TRUE.equals(mapping.getEnabled()))
                    .map(FactoryWarehouseMapping::getWarehouseCode)
                    .forEach(enabledWarehouseCodes::add);
        }
        for (var selected : selectedSnapshots.entrySet()) {
            Set<String> importedWarehouseCodes = new LinkedHashSet<>();
            List<String> itemWarehouseCodes = inventoryItemMapper.selectWarehouseCodesBySnapshotId(selected.getKey());
            if (itemWarehouseCodes != null) importedWarehouseCodes.addAll(itemWarehouseCodes);
            if (selected.getValue().getWarehouseCode() != null)
                importedWarehouseCodes.add(selected.getValue().getWarehouseCode());
            importedWarehouseCodes.removeAll(enabledWarehouseCodes);
            if (!importedWarehouseCodes.isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("INVENTORY_WAREHOUSE_MISMATCH",
                        "所选库存快照包含未启用或未映射的仓库: " + String.join("、", importedWarehouseCodes)));
        }
        if (bomSnapshotId != null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("BOM_SNAPSHOT_UNSUPPORTED", "新排产使用BOM管理中当前同步数据，不再选择BOM快照"));
        }
        if (forecastVersion == null || !ForecastVersion.STATUS_IMPORTED.equals(forecastVersion.status())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_FORECAST_VERSION", "经营计划版本不存在或不可用"));
        }
        BomSnapshotService.CurrentBom currentBom = bomSnapshotService.loadCurrentBom();
        String bomSourceChecksum = currentBom.checksum();

        if (categoryRoleVersionId == null) {
            categoryRoleVersionId = latestPublishedCategoryRoleId();
        }
        CategoryRoleVersion categoryRoleVersion = configurationService.getCategoryRoleVersion(categoryRoleVersionId);

        if (categoryRoleVersion == null || !"PUBLISHED".equals(categoryRoleVersion.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_CATEGORY_ROLE_VERSION", "物料分类角色版本未发布"));
        }

        Map<String, List<FactoryWarehouseMapping>> factoryMappings = new LinkedHashMap<>();
        Map<String, Long> warehouseSnapshots = new LinkedHashMap<>();
        Set<Long> coveredSnapshots = new LinkedHashSet<>();
        for (String factory : factories) {
            List<FactoryWarehouseMapping> mappings = configMaster.selectFactoryWarehouseMappingsByFactory(factory).stream()
                    .filter(mapping -> Boolean.TRUE.equals(mapping.getEnabled())).toList();
            if (mappings.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("FACTORY_WAREHOUSE_NOT_MAPPED", "请维护并启用工厂仓库映射: " + factory));
            }
            for (FactoryWarehouseMapping mapping : mappings) {
                if (!Boolean.TRUE.equals(mapping.getQualityConfigured()))
                    return ResponseEntity.badRequest().body(ApiResponse.error("WAREHOUSE_QUALITY_NOT_CONFIGURED", "请先确认仓库良品属性: " + mapping.getWarehouseCode()));
                if (warehouseSnapshots.containsKey(mapping.getWarehouseCode()))
                    return ResponseEntity.badRequest().body(ApiResponse.error("FACTORY_WAREHOUSE_NOT_MAPPED", "仓库不能归属多个工厂"));
                List<Long> matching = selectedSnapshots.entrySet().stream().filter(entry ->
                        mapping.getWarehouseCode().equals(entry.getValue().getWarehouseCode())
                        || inventoryItemMapper.countBySnapshotIdAndWarehouseCode(entry.getKey(), mapping.getWarehouseCode()) > 0)
                        .map(Map.Entry::getKey).toList();
                if (matching.size() != 1) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("INVENTORY_WAREHOUSE_MISMATCH",
                            "请为 " + factory + " 的仓库 " + mapping.getWarehouseCode() + " 选择且仅选择一个库存快照"));
                }
                warehouseSnapshots.put(mapping.getWarehouseCode(), matching.getFirst());
                coveredSnapshots.add(matching.getFirst());
            }
            factoryMappings.put(factory, mappings);
            if (!configSnapshotService.hasCalendar(factory)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("MISSING_CALENDAR", "请先维护 " + factory + " 的生产日历"));
            }
        }
        if (!coveredSnapshots.equals(selectedIds)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVENTORY_WAREHOUSE_MISMATCH", "所选库存快照包含工厂映射范围以外的仓库"));
        }
        if (!configSnapshotService.hasStockPolicy()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("MISSING_STOCK_POLICY", "请先维护库存控制策略"));
        }

        List<ForecastDetail> forecastDetails = forecastMapper.selectDetailsByVersionId(forecastVersionId);
        Set<LocalDate> businessMonths = new LinkedHashSet<>();
        for (ForecastDetail detail : forecastDetails) {
            if (factories.contains(detail.factoryCode()) && detail.planMonth() != null) {
                businessMonths.add(detail.planMonth().withDayOfMonth(1));
            }
        }
        if (businessMonths.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "NO_FACTORY_FORECAST", "经营计划中没有所选工厂的数据: " + factoryCode));
        }
        List<LocalDate> maintainedMonths = configSnapshotService.businessMonths();
        List<LocalDate> requiredMonths = configSnapshotService.rollingBusinessMonths(List.copyOf(businessMonths));
        for (LocalDate businessMonth : requiredMonths) {
            if (!maintainedMonths.contains(businessMonth)) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        "MISSING_BUSINESS_WEEK", "请先维护 " + businessMonth.toString().substring(0, 7) + " 的业务周期间"));
            }
        }

        Long userId = CurrentUser.getUserId();
        ConfigSnapshotService.Snapshot snapshot = configSnapshotService.snapshot(factoryCode,
                requiredMonths, userId);
        Long mappingVersionId = snapshot.mappingVersionId();
        Long stockPolicyVersionId = snapshot.stockPolicyVersionId();
        materialPolicyVersionId = snapshot.materialPolicyVersionId();
        productionCalendarVersionId = snapshot.calendarVersionId();
        Map<LocalDate, Long> businessWeekVersions = snapshot.businessWeekVersionIds();
        MrpCalcBatch batch = new MrpCalcBatch();
        batch.setFactoryCode(factoryCode);
        batch.setStatus("DRAFT");
        batch.setForecastVersionId(forecastVersionId);
        batch.setInventorySnapshotId(inventorySnapshotId);
        if (coveredSnapshots.size() > 1) batch.setInventorySnapshotGroupId(
                inventoryNormalizationService.createSnapshotGroup(warehouseSnapshots, userId));
        batch.setBomSnapshotId(bomSnapshotId);
        batch.setBomSourceChecksum(bomSourceChecksum);
        batch.setCategoryRoleVersionId(categoryRoleVersionId);
        batch.setMaterialPolicyVersionId(materialPolicyVersionId);
        batch.setProductionCalendarVersionId(productionCalendarVersionId);
        batch.setFactoryWarehouseMappingVersionId(mappingVersionId);
        batch.setStockPolicyVersionId(stockPolicyVersionId);
        batch.setBusinessWeekVersionId(businessWeekVersions.values().iterator().next());
        batch.setInputChecksum(computeInputChecksum(factoryCode, forecastVersionId, inventorySnapshotId,
                bomSourceChecksum, categoryRoleVersionId, materialPolicyVersionId, productionCalendarVersionId,
                mappingVersionId, stockPolicyVersionId, warehouseSnapshots.toString(), businessWeekVersions));
        batch.setVersionNo(1);
        batch.setActiveRequestKey(UUID.randomUUID().toString());
        batch.setIssueCount(0);
        batch.setUnresolvedCount(0);
        batch.setVersion(0);
        batch.setRuleVersion(com.mrp.mrp.service.ProductionCapacityRules.VERSION);
        batch.setRevisionNo(0);
        batch.setCreatedBy(userId);
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.insert(batch);
        for (String factory : factories) for (FactoryWarehouseMapping mapping : factoryMappings.get(factory)) {
            String warehouse = mapping.getWarehouseCode();
            batchMapper.insertFactoryScope(batch.getId(), factory, warehouse, productionCalendarVersionId,
                    warehouseSnapshots.get(warehouse));
        }
        for (Map.Entry<LocalDate, Long> entry : businessWeekVersions.entrySet()) {
            batchMapper.insertBusinessWeekScope(batch.getId(), entry.getKey(), entry.getValue());
        }
        for (Long selected : coveredSnapshots) inventoryNormalizationService.normalizeSnapshot(selected, "ALL", null);
        for (String factory : factories) {
            Map<Long, Set<String>> warehousesBySnapshot = new LinkedHashMap<>();
            for (FactoryWarehouseMapping mapping : factoryMappings.get(factory)) {
                Long source = warehouseSnapshots.get(mapping.getWarehouseCode());
                warehousesBySnapshot.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(mapping.getWarehouseCode());
            }
            for (var source : warehousesBySnapshot.entrySet())
                inventoryNormalizationService.createPoolsFromSnapshot(batch.getId(), source.getKey(), factory,
                        stockPolicyVersionId, source.getValue());
        }
        return ResponseEntity.ok(ApiResponse.ok(batch));
    }

    private String computeInputChecksum(String factoryCode, Long forecastVersionId, Long inventorySnapshotId,
                                         String bomSourceChecksum, Long categoryRoleVersionId,
                                         Long materialPolicyVersionId, Long productionCalendarVersionId,
                                         Long mappingVersionId, Long stockPolicyVersionId, String warehouseCode,
                                         Map<LocalDate, Long> businessWeekVersions) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = factoryCode + "|" + forecastVersionId + "|" + inventorySnapshotId + "|"
                    + bomSourceChecksum + "|" + categoryRoleVersionId + "|" + materialPolicyVersionId + "|"
                    + productionCalendarVersionId + "|" + mappingVersionId + "|" + stockPolicyVersionId + "|"
                    + warehouseCode + "|" + businessWeekVersions;
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private Long latestPublishedCategoryRoleId() {
        return configurationService.getCategoryRoleVersions().stream()
                .filter(version -> "PUBLISHED".equals(version.getStatus()))
                .map(CategoryRoleVersion::getId).max(Long::compareTo).orElse(null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MrpCalcBatch>> getBatch(@PathVariable Long id) {
        MrpCalcBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("BATCH_NOT_FOUND", "Batch not found: " + id));
        }
        return ResponseEntity.ok(ApiResponse.ok(batch));
    }

    @GetMapping("/{id}/factories")
    public ResponseEntity<ApiResponse<List<String>>> getBatchFactories(@PathVariable Long id) {
        MrpCalcBatch batch = batchMapper.selectById(id);
        factoryAccess.require(batch);
        List<String> codes = batchMapper.selectFactoryCodesByBatchId(id);
        return ResponseEntity.ok(ApiResponse.ok(codes.isEmpty()
                ? com.mrp.mrp.service.ProductionCapacityRules.factories(batch) : codes));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<MrpCalcBatch>> getCurrentBatch() {
        var visible = factoryAccess.visible(null, null, true, 0L, 1);
        return ResponseEntity.ok(ApiResponse.ok(visible.isEmpty() ? null : visible.getFirst()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<MrpCalcBatch>> cancelBatch(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(orchestrator.cancel(id)));
    }
    
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResult<MrpCalcBatch>>> getBatchesPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        com.mrp.mrp.service.RollingPlanningQueryService.validatePage(page, pageSize);
        long offset = (long) (page - 1) * pageSize;
        List<MrpCalcBatch> batches = factoryAccess.visible(status, keyword, false, offset, pageSize);
        long total = factoryAccess.countVisible(status, keyword);
        PageResult<MrpCalcBatch> result = new PageResult<>(batches, total, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
    
    public static class PageResult<T> {
        public final List<T> items;
        public final long total;
        public final int page;
        public final int pageSize;
        public final int totalPages;

        public PageResult(List<T> items, long total, int page, int pageSize) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.pageSize = pageSize;
            this.totalPages = (int) Math.ceil((double) total / pageSize);
        }
    }
}
