package com.mrp.inventory.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.ValidationException;
import com.mrp.importexport.excel.InventoryExcelParser;
import com.mrp.importexport.excel.InventoryItemExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.service.FileStorageService;
import com.mrp.importexport.service.FileStorageService.StoredFile;
import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.domain.InventoryItem;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.repository.InventoryItemMapper;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.task.domain.ImportTask;
import com.mrp.task.service.ImportTaskAccess;
import com.mrp.task.service.ImportTaskCreation;
import com.mrp.task.repository.ImportTaskMapper;
import com.mrp.mrp.repository.ConfigMasterMapper;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.importexport.excel.ParseError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryImportService {

    private static final Logger log = LoggerFactory.getLogger(InventoryImportService.class);

    private final FileStorageService fileStorage;
    private final InventoryExcelParser excelParser;
    private final InventoryItemExcelParser itemExcelParser;
    private final InventoryMapper inventoryMapper;
    private final InventoryItemMapper inventoryItemMapper;
    private final MaterialMapper materialMapper;
    private final ImportTaskMapper importTaskMapper;
    private final InventorySnapshotAccessService snapshotAccess;
    private final ConfigMasterMapper warehouseMapper;

    private final Map<Long, ParseResult<InventoryDetail>> stagingCache = new ConcurrentHashMap<>();
    private final Map<Long, ParseResult<InventoryItem>> itemStagingCache = new ConcurrentHashMap<>();

    public InventoryImportService(FileStorageService fileStorage, InventoryExcelParser excelParser,
                                  InventoryItemExcelParser itemExcelParser,
                                  InventoryMapper inventoryMapper, InventoryItemMapper inventoryItemMapper,
                                  MaterialMapper materialMapper, ImportTaskMapper importTaskMapper,
                                  InventorySnapshotAccessService snapshotAccess, ConfigMasterMapper warehouseMapper) {
        this.snapshotAccess = snapshotAccess;
        this.warehouseMapper = warehouseMapper;
        this.fileStorage = fileStorage;
        this.excelParser = excelParser;
        this.itemExcelParser = itemExcelParser;
        this.inventoryMapper = inventoryMapper;
        this.inventoryItemMapper = inventoryItemMapper;
        this.materialMapper = materialMapper;
        this.importTaskMapper = importTaskMapper;
    }

    public ImportTask upload(InputStream fileStream, String fileName, LocalDate snapshotDate, Long userId) throws IOException {
        ImportTaskAccess.requireActor(userId);
        StoredFile stored = fileStorage.store(fileStream, fileName);

        String requestKey = ImportTaskAccess.requestKey("inventory:", stored.checksum(), userId);
        ImportTask existing = importTaskMapper.selectByRequestKey(requestKey);
        if (existing != null) {
            // Re-parse file to re-populate staging cache (it may have been cleared after confirm or JVM restart)
            ParseResult<InventoryDetail> result = excelParser.parse(stored.path(), null);
            stagingCache.put(existing.id(), result);
            return existing;
        }

        ImportTask task = new ImportTask(
                null, ImportTask.TYPE_INVENTORY, null,
                requestKey, ImportTask.STATUS_PENDING, 0,
                null, null, null, 0, 0, 0, 3, null,
                null, stored.checksum(), null, null, null,
                userId, null, null, null, 0
        );
        task = ImportTaskCreation.createOrGet(importTaskMapper, task);

        // Parse with temporary snapshotId (will be set on confirm)
        ParseResult<InventoryDetail> result = excelParser.parse(stored.path(), null);
        stagingCache.put(task.id(), result);

        return task;
    }

    public ParseResult<InventoryDetail> getStagingResult(Long taskId) {
        ImportTaskAccess.requireOwner(importTaskMapper, taskId, ImportTask.TYPE_INVENTORY, "inventory:");
        ParseResult<InventoryDetail> result = stagingCache.get(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        return result;
    }

    @Transactional
    public InventorySnapshot confirm(Long taskId, LocalDate snapshotDate, Long userId) {
        ImportTaskAccess.requireActor(userId);
        ImportTask task = ImportTaskAccess.lockForConfirmation(importTaskMapper, taskId, ImportTask.TYPE_INVENTORY, "inventory:");
        if (ImportTask.STATUS_SUCCEEDED.equals(task.status())) return confirmedSnapshot(task);
        ParseResult<InventoryDetail> result = getStagingResult(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        if (result.hasErrors()) {
            throw new ValidationException("Cannot confirm with " + result.errorRows() + " errors");
        }

        InventorySnapshot snapshot = new InventorySnapshot(
                null, snapshotDate, null, null,
                InventorySnapshot.STATUS_IMPORTED, userId, null
        );
        inventoryMapper.insertSnapshot(snapshot);

        List<InventoryDetail> details = result.rows().stream()
                .map(d -> new InventoryDetail(
                        null, snapshot.getId(), d.factoryCode(), d.materialId(),
                        d.warehouseCode(), d.warehouseName(), d.warehouseType(),
                        d.quantity(), d.isInCalculation()
                ))
                .toList();

        for (int i = 0; i < details.size(); i += 500) {
            inventoryMapper.insertDetails(details.subList(i, Math.min(i + 500, details.size())));
        }

        ImportTaskAccess.completeAfterCommit(importTaskMapper, task, snapshot.getId(), stagingCache, result);

        log.info("Inventory import confirmed: snapshot={}, details={}", snapshot.getId(), details.size());
        return snapshot;
    }

    // ========== New inventory item import flow ==========

    public ImportTask uploadItem(InputStream fileStream, String fileName, Long userId) throws IOException {
        ImportTaskAccess.requireActor(userId);
        StoredFile stored = fileStorage.store(fileStream, fileName);

        String requestKey = ImportTaskAccess.requestKey("inventory_item:", stored.checksum(), userId);
        ImportTask existing = importTaskMapper.selectByRequestKey(requestKey);
        if (existing != null) {
            // Re-parse file to re-populate staging cache (it may have been cleared after confirm or JVM restart)
            ParseResult<InventoryItem> result = validateWarehouses(itemExcelParser.parse(stored.path()));
            itemStagingCache.put(existing.id(), result);
            return existing;
        }

        ImportTask task = new ImportTask(
                null, ImportTask.TYPE_INVENTORY, null,
                requestKey, ImportTask.STATUS_PENDING, 0,
                null, null, null, 0, 0, 0, 3, null,
                null, stored.checksum(), null, null, null,
                userId, null, null, null, 0
        );
        task = ImportTaskCreation.createOrGet(importTaskMapper, task);

        ParseResult<InventoryItem> result = validateWarehouses(itemExcelParser.parse(stored.path()));
        itemStagingCache.put(task.id(), result);
        return task;
    }

    public ParseResult<InventoryItem> getItemStagingResult(Long taskId) {
        ImportTaskAccess.requireOwner(importTaskMapper, taskId, ImportTask.TYPE_INVENTORY, "inventory_item:");
        ParseResult<InventoryItem> result = itemStagingCache.get(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        return result;
    }

    @Transactional
    public InventorySnapshot confirmItem(Long taskId, Long userId) {
        ImportTaskAccess.requireActor(userId);
        ImportTask task = ImportTaskAccess.lockForConfirmation(importTaskMapper, taskId, ImportTask.TYPE_INVENTORY, "inventory_item:");
        if (ImportTask.STATUS_SUCCEEDED.equals(task.status())) return confirmedSnapshot(task);
        ParseResult<InventoryItem> result = getItemStagingResult(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        if (result.hasErrors()) {
            throw new ValidationException("Cannot confirm with " + result.errorRows() + " errors");
        }
        ParseResult<InventoryItem> stagedResult = result;
        result = validateWarehouses(result);
        if (result.hasErrors()) throw new ValidationException("仓库配置已变化，请重新上传库存文件");
        // The preview established the warehouse-derived quality for every row.
        // A later warehouse quality change must not silently turn GOOD into DEFECTIVE
        // (or vice versa) when the user confirms that preview.
        if (stagedResult.rows().size() != result.rows().size())
            throw new ValidationException("仓库配置已变化，请重新上传库存文件");
        for (int index = 0; index < result.rows().size(); index++) {
            InventoryItem staged = stagedResult.rows().get(index);
            InventoryItem current = result.rows().get(index);
            if (!java.util.Objects.equals(staged.warehouseCode(), current.warehouseCode())
                    || !java.util.Objects.equals(staged.inventoryQuality(), current.inventoryQuality()))
                throw new ValidationException("仓库品质配置已变化，请重新上传库存文件");
        }
        if (result.rows().isEmpty()) {
            throw new ValidationException("No inventory data to import");
        }
        itemStagingCache.replace(taskId, stagedResult, result);

        InventorySnapshot snapshot = new InventorySnapshot(
                null, LocalDate.now(), null, null, null,
                InventorySnapshot.STATUS_IMPORTED, userId, null
        );
        inventoryMapper.insertSnapshot(snapshot);

        List<InventoryItem> items = result.rows().stream()
                .map(item -> item.withSnapshotId(snapshot.getId()))
                .toList();

        for (int i = 0; i < items.size(); i += 500) {
            inventoryItemMapper.insertItems(items.subList(i, Math.min(i + 500, items.size())));
        }

        ImportTaskAccess.completeAfterCommit(importTaskMapper, task, snapshot.getId(), itemStagingCache, result);
        log.info("Inventory item import confirmed: snapshot={}, items={}", snapshot.getId(), items.size());
        return snapshot;
    }

    private InventorySnapshot confirmedSnapshot(ImportTask task) {
        InventorySnapshot snapshot = task.resultResourceId() == null ? null
                : inventoryMapper.selectSnapshotById(task.resultResourceId());
        if (snapshot == null || !java.util.Objects.equals(snapshot.getCreatedBy(), task.createdBy())) {
            throw ImportTaskAccess.conflict();
        }
        return snapshot;
    }

    private ParseResult<InventoryItem> validateWarehouses(ParseResult<InventoryItem> parsed) {
        List<ParseError> errors = new ArrayList<>(parsed.errors());
        List<InventoryItem> rows = new ArrayList<>();
        List<String> materialCodes = parsed.rows().stream().map(InventoryItem::materialId).distinct().toList();
        Map<String, Material> materials = new HashMap<>();
        for (int offset = 0; offset < materialCodes.size(); offset += 500) {
            materialMapper.selectByCodes(materialCodes.subList(offset, Math.min(offset + 500, materialCodes.size())))
                    .forEach(material -> materials.put(material.materialCode(), material));
        }
        boolean legacy = parsed.headers().isEmpty() || (parsed.headers().size() > 4 && parsed.headers().get(4).contains("品质"));
        int index = 0;
        int rawIndex = 0;
        for (InventoryItem item : parsed.rows()) {
            FactoryWarehouseMapping warehouse = warehouseMapper.selectFactoryWarehouseMappingByWarehouse(item.warehouseCode());
            int line = index++ + 3;
            while (rawIndex < parsed.rawRows().size()) {
                List<String> raw = parsed.rawRows().get(rawIndex);
                int candidate = rawIndex++ + 3;
                if (raw.size() > 3 && item.warehouseCode().equals(raw.get(0))
                        && item.materialId().equals(raw.get(3))) { line = candidate; break; }
            }
            if (warehouse == null || !Boolean.TRUE.equals(warehouse.getEnabled())
                    || !Boolean.TRUE.equals(warehouse.getQualityConfigured())) {
                errors.add(ParseError.of(line, "warehouse_code", item.warehouseCode(), "WAREHOUSE_NOT_CONFIGURED",
                        "仓库不存在、未启用或未确认良品属性"));
                continue;
            }
            String quality = Boolean.TRUE.equals(warehouse.getGoodWarehouse()) ? "GOOD" : "DEFECTIVE";
            if (legacy && item.inventoryQuality() != null && !quality.equals(item.inventoryQuality())) {
                errors.add(ParseError.of(line, "inventory_quality", item.inventoryQuality(), "QUALITY_MISMATCH",
                        "库存品质与仓库良品属性不一致"));
                continue;
            }
            Material material = materials.get(item.materialId());
            if (material == null) {
                errors.add(ParseError.of(line, "material_id", item.materialId(), "MATERIAL_NOT_FOUND",
                        "未找到该料号，请先维护再导入"));
                continue;
            }
            rows.add(item.withWarehouseQuality(quality, warehouse.getWarehouseName())
                    .withMaterialInfo(material.projectModel(), material.materialName()));
        }
        int errorRows = (int) errors.stream().map(ParseError::rowNumber).distinct().count();
        return new ParseResult<>(rows, errors, parsed.recognizedMonths(), parsed.totalRows(), rows.size(),
                errorRows, parsed.rawRows(), parsed.headers());
    }

    public List<InventorySnapshot> listSnapshots() {
        return snapshotAccess.list(null);
    }

    public com.mrp.common.response.PageResult<InventorySnapshot> listSnapshotsPage(
            String keyword, int page, int pageSize) {
        return snapshotAccess.page(keyword, page, pageSize);
    }

    public List<InventoryItem> getSnapshotItems(Long snapshotId) {
        return snapshotAccess.items(snapshotId);
    }

    public List<InventoryItem> getSnapshotItemsPage(Long snapshotId, int page, int pageSize) {
        return snapshotAccess.itemsPage(snapshotId, page, pageSize).items();
    }

    public com.mrp.common.response.PageResult<InventoryItem> getSnapshotPage(Long snapshotId, int page, int pageSize) {
        return snapshotAccess.itemsPage(snapshotId, page, pageSize);
    }

    public com.mrp.common.response.PageResult<InventoryItem> getSnapshotPage(Long snapshotId, String keyword, int page, int pageSize) {
        return snapshotAccess.itemsPage(snapshotId, keyword, page, pageSize);
    }

    public int countSnapshotItems(Long snapshotId) {
        return snapshotAccess.countItems(snapshotId);
    }
}
