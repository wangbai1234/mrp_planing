package com.mrp.inventory.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.ValidationException;
import com.mrp.importexport.excel.InventoryExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.service.FileStorageService;
import com.mrp.importexport.service.FileStorageService.StoredFile;
import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.task.domain.ImportTask;
import com.mrp.task.repository.ImportTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryImportService {

    private static final Logger log = LoggerFactory.getLogger(InventoryImportService.class);

    private final FileStorageService fileStorage;
    private final InventoryExcelParser excelParser;
    private final InventoryMapper inventoryMapper;
    private final ImportTaskMapper importTaskMapper;

    private final Map<Long, ParseResult<InventoryDetail>> stagingCache = new ConcurrentHashMap<>();

    public InventoryImportService(FileStorageService fileStorage, InventoryExcelParser excelParser,
                                  InventoryMapper inventoryMapper, ImportTaskMapper importTaskMapper) {
        this.fileStorage = fileStorage;
        this.excelParser = excelParser;
        this.inventoryMapper = inventoryMapper;
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
                        null, snapshot.id(), d.factoryCode(), d.materialId(),
                        d.warehouseCode(), d.warehouseName(), d.warehouseType(),
                        d.quantity(), d.isInCalculation()
                ))
                .toList();

        for (int i = 0; i < details.size(); i += 500) {
            inventoryMapper.insertDetails(details.subList(i, Math.min(i + 500, details.size())));
        }

        ImportTask task = importTaskMapper.selectById(taskId);
        importTaskMapper.updateResult(taskId, ImportTask.STATUS_SUCCEEDED, snapshot.id(), task.version());
        stagingCache.remove(taskId);

        log.info("Inventory import confirmed: snapshot={}, details={}", snapshot.id(), details.size());
        return snapshot;
    }
}
