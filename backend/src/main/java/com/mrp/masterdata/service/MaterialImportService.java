package com.mrp.masterdata.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.ValidationException;
import com.mrp.importexport.excel.MaterialExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.service.FileStorageService;
import com.mrp.importexport.service.FileStorageService.StoredFile;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MaterialImportService {

    private static final Logger log = LoggerFactory.getLogger(MaterialImportService.class);

    private static final int BATCH_SIZE = 1000;

    private final FileStorageService fileStorage;
    private final MaterialExcelParser excelParser;
    private final MaterialMapper materialMapper;

    // Staging cache: checksum -> parsed result
    private final Map<String, StagingData> stagingCache = new ConcurrentHashMap<>();

    public MaterialImportService(FileStorageService fileStorage, MaterialExcelParser excelParser,
                                 MaterialMapper materialMapper) {
        this.fileStorage = fileStorage;
        this.excelParser = excelParser;
        this.materialMapper = materialMapper;
    }

    /**
     * Upload and parse Excel file
     * @return Staging result with checksum for confirmation
     */
    public StagingResult upload(InputStream fileStream, String fileName) throws IOException {
        // Store file temporarily
        StoredFile stored = fileStorage.store(fileStream, fileName);
        String checksum = stored.checksum();

        // Check if already staged
        if (stagingCache.containsKey(checksum)) {
            log.info("Material import already staged for checksum={}", checksum);
            StagingData existing = stagingCache.get(checksum);
            return new StagingResult(checksum, existing.result());
        }

        // Check file size
        long fileSize = Files.size(stored.path());
        if (fileSize > MaterialExcelParser.MAX_FILE_SIZE) {
            throw new ValidationException("文件大小超过" + (MaterialExcelParser.MAX_FILE_SIZE / 1024 / 1024) + "MB限制");
        }

        // Parse Excel
        ParseResult<Material> result = excelParser.parse(stored.path());

        // Store in staging
        stagingCache.put(checksum, new StagingData(stored.path(), result));

        if (result.hasErrors()) {
            log.warn("Material parse has {} errors out of {} rows", result.errorRows(), result.totalRows());
        }

        return new StagingResult(checksum, result);
    }

    /**
     * Confirm import with batch processing
     * Each batch is an independent transaction
     */
    public ImportResult confirm(String checksum, boolean skipDuplicates) {
        StagingData staging = stagingCache.get(checksum);
        if (staging == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }

        ParseResult<Material> result = staging.result();
        if (result.rows().isEmpty()) {
            throw new ValidationException("No data to import");
        }

        List<Material> materials = result.rows();
        int total = materials.size();
        int imported = 0;
        int skipped = 0;
        int failed = 0;

        log.info("Starting material import: total={}, checksum={}", total, checksum);

        // Process in batches
        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<Material> batch = materials.subList(i, end);
            int batchNum = i / BATCH_SIZE + 1;

            try {
                int result_count = processBatch(batch, skipDuplicates);
                imported += result_count;
                skipped += (batch.size() - result_count);
                log.debug("Batch {} completed: {}/{} imported", batchNum, result_count, batch.size());
            } catch (Exception e) {
                failed += batch.size();
                log.error("Batch {} failed: {}", batchNum, e.getMessage(), e);
            }
        }

        // Clean up staging
        stagingCache.remove(checksum);
        cleanUpFile(staging.filePath());

        log.info("Material import completed: imported={}, skipped={}, failed={}, checksum={}", 
                imported, skipped, failed, checksum);

        return new ImportResult(imported, skipped, failed, total);
    }

    /**
     * Process a single batch with its own transaction
     */
    @Transactional
    protected int processBatch(List<Material> batch, boolean skipDuplicates) {
        int count = 0;

        for (Material m : batch) {
            try {
                // Check duplicate
                if (materialMapper.countByCode(m.materialCode()) > 0) {
                    if (skipDuplicates) {
                        continue; // Skip silently
                    } else {
                        throw new ValidationException("Material code already exists: " + m.materialCode());
                    }
                }

                materialMapper.insert(m);
                count++;
            } catch (ValidationException e) {
                if (!skipDuplicates) {
                    throw e;
                }
                // If skipDuplicates, just skip this row
            }
        }

        return count;
    }

    /**
     * Get staging result for preview
     */
    public ParseResult<Material> getStagingResult(String checksum) {
        StagingData staging = stagingCache.get(checksum);
        if (staging == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        return staging.result();
    }

    private void cleanUpFile(Path filePath) {
        try {
            if (filePath != null && Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", filePath, e);
        }
    }

    // Internal data classes

    private record StagingData(Path filePath, ParseResult<Material> result) {}

    public record StagingResult(String checksum, ParseResult<Material> result) {}

    public record ImportResult(int imported, int skipped, int failed, int total) {}
}
