package com.mrp.importexport.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.ValidationException;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.service.PlanningFactoryAccessService;
import com.mrp.planning.domain.PlanDetail;
import com.mrp.planning.domain.PlanVersion;
import com.mrp.planning.repository.PlanMapper;
import com.mrp.task.domain.ExportTask;
import com.mrp.task.repository.ExportTaskMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final PlanMapper planMapper;
    private final ExportTaskMapper exportTaskMapper;
    private final Path exportDir;
    private final PlanningFactoryAccessService access;

    public ExportService(PlanMapper planMapper, ExportTaskMapper exportTaskMapper, PlanningFactoryAccessService access,
                        @Value("${mrp.file.upload-dir:./data/uploads}") String uploadDir) {
        this.planMapper = planMapper;
        this.exportTaskMapper = exportTaskMapper;
        this.access = access;
        this.exportDir = Paths.get(uploadDir, "exports");
        try {
            Files.createDirectories(this.exportDir);
        } catch (IOException e) {
            log.warn("Failed to create export directory: {}", this.exportDir, e);
        }
    }

    public ExportTask createExportTask(Long planVersionId, String factoryCode, List<String> fields,
                                       boolean includePriority, boolean includeActual,
                                       Long userId) {
        if (userId == null || !Objects.equals(userId, CurrentUser.getUserId())) throw forbidden();
        if (fields == null || fields.isEmpty() || fields.stream().anyMatch(f -> f == null || f.isBlank()))
            throw new ValidationException("请选择导出字段");
        log.info("Export request: planVersionId={}, factoryCode={}, fields={}, count={}",
                planVersionId, factoryCode, fields, fields != null ? fields.size() : 0);

        PlanVersion version = planMapper.selectVersionById(planVersionId);
        if (version == null) throw new BusinessException("MRP_NOT_FOUND", "Plan version not found");
        requireFactory(version.getFactoryCode());
        String scope = factoryCode == null || factoryCode.isBlank() ? version.getFactoryCode() : factoryCode;
        if (!Objects.equals(scope, version.getFactoryCode()) && !"ALL".equals(version.getFactoryCode()))
            throw forbidden();
        requireFactory(scope);

        String requestKey = buildRequestKey(planVersionId, scope, fields, includePriority, includeActual, userId);
        log.info("Export requestKey length={}", requestKey.length());
        ExportTask existing = exportTaskMapper.selectByRequestKey(requestKey);
        if (existing != null) {
            requireOwner(existing);
            if (ExportTask.STATUS_SUCCEEDED.equals(existing.getStatus()) && existing.getFilePath() != null) {
                return existing;
            }
            // Delete failed/incomplete task to allow retry
            exportTaskMapper.deleteById(existing.getId());
        }

        ExportTask task = new ExportTask(
                null, "PLAN_EXPORT", scope,
                requestKey, ExportTask.STATUS_PENDING, 0,
                null, null, null, 0, 0, 0, 3, null,
                null, null, null, null, null,
                userId, null, null, null, 0
        );
        exportTaskMapper.insert(task);

        // Generate Excel synchronously (small datasets)
        try {
            List<PlanDetail> details;
            if ("ALL".equals(scope)) {
                details = planMapper.selectDetailsByVersionId(planVersionId);
            } else {
                details = planMapper.selectDetailsByVersionAndFactory(planVersionId, scope);
            }
            for (String actualFactory : details.stream().map(PlanDetail::factoryCode).distinct().toList())
                requireFactory(actualFactory);
            log.info("Export query: planVersionId={}, factoryCode={}, detailCount={}", planVersionId, factoryCode, details.size());
            Path filePath = generateExcel(details, fields, includePriority, includeActual);
            String filePathStr = filePath.toString();
            exportTaskMapper.updateResultWithFile(task.getId(), ExportTask.STATUS_SUCCEEDED, filePathStr, 0);
            log.info("Export generated: taskId={}, file={}", task.getId(), filePath);
            ExportTask result = exportTaskMapper.selectById(task.getId());
            return result;
        } catch (ResponseStatusException e) {
            exportTaskMapper.updateFailure(task.getId(), ExportTask.STATUS_FAILED, "FORBIDDEN", "无权导出该工厂", 0);
            throw e;
        } catch (Exception e) {
            exportTaskMapper.updateFailure(task.getId(), ExportTask.STATUS_FAILED, "EXPORT_ERROR", e.getMessage(), 0);
            throw new BusinessException("MRP_EXPORT_ERROR", "Export failed: " + e.getMessage());
        }
    }

    public ExportTask getExportTask(Long id) {
        ExportTask task = exportTaskMapper.selectById(id);
        if (task != null) requireOwner(task);
        return task;
    }

    public Path getDownloadPath(Long id) {
        ExportTask task = getExportTask(id);
        if (task == null || !ExportTask.STATUS_SUCCEEDED.equals(task.getStatus()) || task.getFilePath() == null)
            throw exportFileNotFound();
        try {
            Path root = exportDir.toRealPath();
            Path file = Paths.get(task.getFilePath()).toRealPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".xlsx"))
                throw exportFileNotFound();
            return file;
        } catch (IOException | java.nio.file.InvalidPathException e) {
            throw exportFileNotFound();
        }
    }

    private static ResponseStatusException exportFileNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Export file not found");
    }

    private void requireOwner(ExportTask task) {
        Long actor = CurrentUser.getUserId();
        if (actor == null || !Objects.equals(actor, task.getCreatedBy()) || !"PLAN_EXPORT".equals(task.getTaskType()))
            throw forbidden();
        requireFactory(task.getBusinessScope());
    }

    private void requireFactory(String factory) {
        if (factory == null || factory.isBlank()) throw forbidden();
        access.requireFactory(factory);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该导出任务或工厂");
    }

    private Path generateExcel(List<PlanDetail> details, List<String> fields,
                              boolean includePriority, boolean includeActual) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("排产计划");

            // Header
            Row header = sheet.createRow(0);
            int col = 0;
            for (String field : fields) {
                header.createCell(col++).setCellValue(field);
            }
            if (includePriority) header.createCell(col++).setCellValue("优先级");
            if (includeActual) header.createCell(col++).setCellValue("实际完成");

            // Group by material
            Map<String, List<PlanDetail>> byMaterial = new LinkedHashMap<>();
            for (PlanDetail d : details) {
                byMaterial.computeIfAbsent(d.materialId(), k -> new ArrayList<>()).add(d);
            }

            int rowIdx = 1;
            for (var entry : byMaterial.entrySet()) {
                List<PlanDetail> materialDetails = entry.getValue();
                PlanDetail meta = materialDetails.get(0);

                Row row = sheet.createRow(rowIdx++);
                col = 0;
                for (String field : fields) {
                    switch (field) {
                        case "整机料号" -> row.createCell(col++).setCellValue(meta.materialId());
                        case "机型" -> row.createCell(col++).setCellValue(meta.model() != null ? meta.model() : "");
                        case "机头料号" -> row.createCell(col++).setCellValue(meta.meMaterialId() != null ? meta.meMaterialId() : "");
                        case "备注" -> row.createCell(col++).setCellValue("");
                        case "合计" -> {
                            long total = materialDetails.stream().mapToLong(PlanDetail::effectiveQuantity).sum();
                            row.createCell(col++).setCellValue(total);
                        }
                        default -> {
                            // Week columns
                            if (field.matches("\\d{4}-\\d{2}-\\d{2}")) {
                                LocalDate weekStart = LocalDate.parse(field);
                                PlanDetail weekDetail = materialDetails.stream()
                                        .filter(d -> d.weekStartDate().equals(weekStart))
                                        .findFirst().orElse(null);
                                row.createCell(col++).setCellValue(weekDetail != null ? weekDetail.effectiveQuantity() : 0);
                            } else {
                                row.createCell(col++).setCellValue("");
                            }
                        }
                    }
                }
                if (includePriority) row.createCell(col++).setCellValue("");
                if (includeActual) row.createCell(col++).setCellValue("");
            }

            Path filePath = exportDir.resolve("plan_" + UUID.randomUUID() + ".xlsx");
            try (var os = Files.newOutputStream(filePath)) {
                wb.write(os);
            }
            return filePath;
        }
    }

    private String buildRequestKey(Long planVersionId, String factoryCode, List<String> fields,
                                   boolean includePriority, boolean includeActual, Long userId) {
        StringBuilder raw = new StringBuilder();
        for (String part : List.of(userId.toString(), planVersionId.toString(), factoryCode,
                Boolean.toString(includePriority), Boolean.toString(includeActual)))
            raw.append(part.length()).append(':').append(part);
        for (String field : fields) raw.append(field.length()).append(':').append(field);
        return "export:v2:" + sha256Hex(raw.toString());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
