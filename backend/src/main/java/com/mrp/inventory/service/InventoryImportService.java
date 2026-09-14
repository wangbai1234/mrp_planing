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
import com.mrp.task.repository.ImportTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
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

    private final Map<Long, ParseResult<InventoryDetail>> stagingCache = new ConcurrentHashMap<>();
    private final Map<Long, ParseResult<InventoryItem>> itemStagingCache = new ConcurrentHashMap<>();

    public InventoryImportService(FileStorageService fileStorage, InventoryExcelParser excelParser,
                                  InventoryItemExcelParser itemExcelParser,
                                  InventoryMapper inventoryMapper, InventoryItemMapper inventoryItemMapper,
                                  MaterialMapper materialMapper, ImportTaskMapper importTaskMapper) {
        this.fileStorage = fileStorage;
        this.excelParser = excelParser;
        this.itemExcelParser = itemExcelParser;
        this.inventoryMapper = inventoryMapper;
        this.inventoryItemMapper = inventoryItemMapper;
        this.materialMapper = materialMapper;
        this.importTaskMapper = importTaskMapper;
    }

    public ImportTask upload(InputStream fileStream, String fileName, LocalDate snapshotDate, Long userId) throws IOException {
        StoredFile stored = fileStorage.store(fileStream, fileName);

        ImportTask existing = importTaskMapper.selectByRequestKey("inventory:" + stored.checksum());
        if (existing != null) {
            return existing;
        }

        ImportTask task = new ImportTask(
                null, ImportTask.TYPE_INVENTORY, null,
                "inventory:" + stored.checksum(), ImportTask.STATUS_PENDING, 0,
                null, null, null, 0, 0, 0, 3, null,
                null, stored.checksum(), null, null, null,
                userId, null, null, null, 0
        );
        importTaskMapper.insert(task);
        Long generatedId = importTaskMapper.selectLastInsertId();
        task = new ImportTask(
                generatedId, task.taskType(), task.businessScope(),
                task.requestKey(), task.status(), task.priority(),
                task.workerId(), task.leaseUntil(), task.heartbeatAt(),
                task.progressCurrent(), task.progressTotal(), task.attemptCount(),
                task.maxAttempts(), task.nextRunAt(), task.inputPayload(),
                task.inputChecksum(), task.resultResourceId(), task.errorCode(),
                task.errorMessage(), task.createdBy(), task.createdAt(),
                task.startedAt(), task.finishedAt(), task.version()
        );

        // Parse with temporary snapshotId (will be set on confirm)
        ParseResult<InventoryDetail> result = excelParser.parse(stored.path(), null);
        stagingCache.put(task.id(), result);

        return task;
    }

    public ParseResult<InventoryDetail> getStagingResult(Long taskId) {
        ParseResult<InventoryDetail> result = stagingCache.get(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        return result;
    }

    @Transactional
    public InventorySnapshot confirm(Long taskId, LocalDate snapshotDate, Long userId) {
        ParseResult<InventoryDetail> result = stagingCache.get(taskId);
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

        ImportTask task = importTaskMapper.selectById(taskId);
        importTaskMapper.updateResult(taskId, ImportTask.STATUS_SUCCEEDED, snapshot.getId(), task.version());
        stagingCache.remove(taskId);

        log.info("Inventory import confirmed: snapshot={}, details={}", snapshot.getId(), details.size());
        return snapshot;
    }

    // ========== New inventory item import flow ==========

    public ImportTask uploadItem(InputStream fileStream, String fileName, Long userId) throws IOException {
        StoredFile stored = fileStorage.store(fileStream, fileName);

        ImportTask existing = importTaskMapper.selectByRequestKey("inventory_item:" + stored.checksum());
        if (existing != null) {
            return existing;
        }

        ImportTask task = new ImportTask(
                null, ImportTask.TYPE_INVENTORY, null,
                "inventory_item:" + stored.checksum(), ImportTask.STATUS_PENDING, 0,
                null, null, null, 0, 0, 0, 3, null,
                null, stored.checksum(), null, null, null,
                userId, null, null, null, 0
        );
        importTaskMapper.insert(task);
        Long generatedId = importTaskMapper.selectLastInsertId();
        task = new ImportTask(
                generatedId, task.taskType(), task.businessScope(),
                task.requestKey(), task.status(), task.priority(),
                task.workerId(), task.leaseUntil(), task.heartbeatAt(),
                task.progressCurrent(), task.progressTotal(), task.attemptCount(),
                task.maxAttempts(), task.nextRunAt(), task.inputPayload(),
                task.inputChecksum(), task.resultResourceId(), task.errorCode(),
                task.errorMessage(), task.createdBy(), task.createdAt(),
                task.startedAt(), task.finishedAt(), task.version()
        );

        ParseResult<InventoryItem> result = itemExcelParser.parse(stored.path());

        // Enrich with material info from material master
        List<InventoryItem> enrichedRows = new ArrayList<>();
        for (InventoryItem item : result.rows()) {
            Material material = materialMapper.selectByCode(item.materialId());
            if (material != null) {
                enrichedRows.add(item.withMaterialInfo(material.projectModel(), material.materialName()));
            } else {
                enrichedRows.add(item);
            }
        }

        ParseResult<InventoryItem> enrichedResult = new ParseResult<>(
                enrichedRows, result.errors(), result.recognizedMonths(),
                result.totalRows(), result.successRows(), result.errorRows(),
                result.rawRows(), result.headers()
        );
        itemStagingCache.put(task.id(), enrichedResult);

        return task;
    }

    public ParseResult<InventoryItem> getItemStagingResult(Long taskId) {
        ParseResult<InventoryItem> result = itemStagingCache.get(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        return result;
    }

    @Transactional
    public InventorySnapshot confirmItem(Long taskId, Long userId) {
        ParseResult<InventoryItem> result = itemStagingCache.get(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        if (result.hasErrors()) {
            throw new ValidationException("Cannot confirm with " + result.errorRows() + " errors");
        }

        InventorySnapshot snapshot = new InventorySnapshot(
                null, LocalDate.now(), null, null,
                InventorySnapshot.STATUS_IMPORTED, userId, null
        );
        inventoryMapper.insertSnapshot(snapshot);

        List<InventoryItem> items = result.rows().stream()
                .map(item -> item.withSnapshotId(snapshot.getId()))
                .toList();

        for (int i = 0; i < items.size(); i += 500) {
            inventoryItemMapper.insertItems(items.subList(i, Math.min(i + 500, items.size())));
        }

        ImportTask task = importTaskMapper.selectById(taskId);
        importTaskMapper.updateResult(taskId, ImportTask.STATUS_SUCCEEDED, snapshot.getId(), task.version());
        itemStagingCache.remove(taskId);

        log.info("Inventory item import confirmed: snapshot={}, items={}", snapshot.getId(), items.size());
        return snapshot;
    }

    public List<InventorySnapshot> listSnapshots() {
        return inventoryMapper.selectAllSnapshots();
    }

    public List<InventoryItem> getSnapshotItems(Long snapshotId) {
        return inventoryItemMapper.selectBySnapshotId(snapshotId);
    }

    public List<InventoryItem> getSnapshotItemsPage(Long snapshotId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return inventoryItemMapper.selectPageBySnapshotId(snapshotId, offset, pageSize);
    }

    public int countSnapshotItems(Long snapshotId) {
        return inventoryItemMapper.countBySnapshotId(snapshotId);
    }
}
