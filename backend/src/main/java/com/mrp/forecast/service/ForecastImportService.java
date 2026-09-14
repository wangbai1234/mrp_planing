package com.mrp.forecast.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.ValidationException;
import com.mrp.common.response.PageResult;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.importexport.excel.ForecastExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.service.FileStorageService;
import com.mrp.importexport.service.FileStorageService.StoredFile;
import com.mrp.task.domain.ImportTask;
import com.mrp.task.repository.ImportTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ForecastImportService {

    private static final Logger log = LoggerFactory.getLogger(ForecastImportService.class);

    private final FileStorageService fileStorage;
    private final ForecastExcelParser excelParser;
    private final ForecastMapper forecastMapper;
    private final ImportTaskMapper importTaskMapper;

    private final Map<Long, ParseResult<ForecastDetail>> stagingCache = new ConcurrentHashMap<>();

    public ForecastImportService(FileStorageService fileStorage, ForecastExcelParser excelParser,
                                 ForecastMapper forecastMapper, ImportTaskMapper importTaskMapper) {
        this.fileStorage = fileStorage;
        this.excelParser = excelParser;
        this.forecastMapper = forecastMapper;
        this.importTaskMapper = importTaskMapper;
    }

    public ImportTask upload(InputStream fileStream, String fileName, Long userId) throws IOException {
        StoredFile stored = fileStorage.store(fileStream, fileName);

        // Check idempotency
        ImportTask existing = importTaskMapper.selectByRequestKey("forecast:" + stored.checksum());
        if (existing != null) {
            // 检查 staging cache 是否还有数据
            ParseResult<ForecastDetail> cachedResult = stagingCache.get(existing.id());
            if (cachedResult != null) {
                log.info("Forecast import already exists for checksum={}, returning cached", stored.checksum());
                return existing;
            }
            // staging cache 已过期，重新解析
            log.info("Forecast import exists but staging expired, re-parsing for checksum={}", stored.checksum());
            ParseResult<ForecastDetail> result = excelParser.parse(stored.path(), null);
            stagingCache.put(existing.id(), result);
            return existing;
        }

        // Create import task
        ImportTask task = new ImportTask(
                null, ImportTask.TYPE_FORECAST, null,
                "forecast:" + stored.checksum(), ImportTask.STATUS_PENDING, 0,
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

        // Parse Excel to staging
        ParseResult<ForecastDetail> result = excelParser.parse(stored.path(), null);
        stagingCache.put(task.id(), result);

        if (result.hasErrors()) {
            log.warn("Forecast parse has {} errors out of {} rows", result.errorRows(), result.totalRows());
        }

        return task;
    }

    public ParseResult<ForecastDetail> getStagingResult(Long taskId) {
        ParseResult<ForecastDetail> result = stagingCache.get(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        return result;
    }

    @Transactional
    public ForecastVersion confirm(Long taskId, Long userId) {
        ParseResult<ForecastDetail> result = stagingCache.get(taskId);
        if (result == null) {
            throw new BusinessException("MRP_STAGING_EXPIRED", "Staging data expired, please re-upload");
        }
        if (result.hasErrors()) {
            throw new ValidationException("Cannot confirm with " + result.errorRows() + " errors");
        }
        if (result.rows().isEmpty()) {
            throw new ValidationException("No data to import");
        }

        // Get next version number
        ForecastVersion latest = forecastMapper.selectLatestVersion();
        int nextVersionNo = (latest != null) ? latest.versionNo() + 1 : 1;

        // Create version
        ForecastVersion newVersion = new ForecastVersion(
                null, nextVersionNo, null, null,
                ForecastVersion.STATUS_IMPORTED, userId, null
        );
        forecastMapper.insertVersion(newVersion);
        Long versionId = forecastMapper.selectLastInsertVersionId();
        ForecastVersion version = new ForecastVersion(
                versionId, newVersion.versionNo(), newVersion.fileName(), newVersion.fileChecksum(),
                newVersion.status(), newVersion.createdBy(), newVersion.createdAt()
        );

        // Insert details
        final Long finalVersionId = versionId;
        List<ForecastDetail> details = result.rows().stream()
                .map(d -> new ForecastDetail(
                        null, finalVersionId, d.factoryCode(), d.materialId(), d.materialName(),
                        d.businessLine(), d.formType(), d.project(), d.platform(), d.mold(),
                        d.status(), d.planMonth(), d.forecastQty()
                ))
                .toList();

        // Batch insert (500 per batch)
        for (int i = 0; i < details.size(); i += 500) {
            List<ForecastDetail> batch = details.subList(i, Math.min(i + 500, details.size()));
            forecastMapper.insertDetails(batch);
        }

        // Update task
        ImportTask task = importTaskMapper.selectById(taskId);
        importTaskMapper.updateResult(taskId, ImportTask.STATUS_SUCCEEDED, version.id(), task.version());

        stagingCache.remove(taskId);
        log.info("Forecast import confirmed: version={}, details={}", version.id(), details.size());

        return version;
    }

    public List<ForecastVersion> listVersions() {
        return forecastMapper.selectAllVersions();
    }

    public PageResult<ForecastVersion> listVersionsPage(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ForecastVersion> items = forecastMapper.selectVersionsPage(keyword, offset, pageSize);
        long total = forecastMapper.countVersionsPage(keyword);
        return new PageResult<>(items, (int) total, page, pageSize);
    }

    public List<ForecastDetail> getVersionDetails(Long versionId) {
        return forecastMapper.selectDetailsByVersionId(versionId);
    }

    public PageResult<ForecastDetail> getVersionDetailsPage(Long versionId, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ForecastDetail> items = forecastMapper.selectDetailsPage(versionId, keyword, offset, pageSize);
        long total = forecastMapper.countDetailsPage(versionId, keyword);
        return new PageResult<>(items, (int) total, page, pageSize);
    }

    public PageResult<Map<String, Object>> getVersionMaterialsPage(Long versionId, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> materials = forecastMapper.selectDistinctMaterials(versionId, keyword, offset, pageSize);
        long total = forecastMapper.countDistinctMaterials(versionId, keyword);
        
        // 为每个物料查询其月份数据
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> material : materials) {
            String materialId = (String) material.get("material_id");
            List<ForecastDetail> details = forecastMapper.selectDetailsByVersionIdAndMaterial(versionId, materialId);
            
            Map<String, Object> item = new HashMap<>();
            item.put("materialId", materialId);
            item.put("materialName", material.get("material_name"));
            item.put("factoryCode", material.get("factory_code"));
            item.put("formType", material.get("form_type"));
            item.put("project", material.get("project"));
            item.put("platform", material.get("platform"));
            item.put("mold", material.get("mold"));
            item.put("status", material.get("status"));
            
            // 构建月份数据
            Map<String, Object> months = new HashMap<>();
            BigDecimal totalQty = BigDecimal.ZERO;
            for (ForecastDetail detail : details) {
                months.put(detail.planMonth().toString(), detail.forecastQty());
                totalQty = totalQty.add(detail.forecastQty());
            }
            item.put("months", months);
            item.put("total", totalQty);
            
            items.add(item);
        }
        
        return new PageResult<>(items, (int) total, page, pageSize);
    }
}
