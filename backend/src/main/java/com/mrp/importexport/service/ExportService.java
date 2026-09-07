package com.mrp.importexport.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.ValidationException;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final PlanMapper planMapper;
    private final ExportTaskMapper exportTaskMapper;
    private final Path exportDir;

    public ExportService(PlanMapper planMapper, ExportTaskMapper exportTaskMapper,
                        @Value("${mrp.file.upload-dir:./data/uploads}") String uploadDir) {
        this.planMapper = planMapper;
        this.exportTaskMapper = exportTaskMapper;
        this.exportDir = Paths.get(uploadDir, "exports");
        try { Files.createDirectories(this.exportDir); } catch (IOException e) { /* ignore */ }
    }

    public ExportTask createExportTask(Long planVersionId, List<String> fields,
                                       boolean includePriority, boolean includeActual,
                                       Long userId) {
        PlanVersion version = planMapper.selectVersionById(planVersionId);
        if (version == null) throw new BusinessException("MRP_NOT_FOUND", "Plan version not found");

        String requestKey = "export:" + planVersionId + ":" + String.join(",", fields);
        ExportTask existing = exportTaskMapper.selectByRequestKey(requestKey);
        if (existing != null && ExportTask.STATUS_SUCCEEDED.equals(existing.status())) {
            return existing;
        }

        ExportTask task = new ExportTask(
                null, "PLAN_EXPORT", version.factoryCode(),
                requestKey, ExportTask.STATUS_PENDING, 0,
                null, null, null, 0, 0, 0, 3, null,
                null, null, null, null, null,
                userId, null, null, null, 0
        );
        exportTaskMapper.insert(task);

        // Generate Excel synchronously (small datasets)
        try {
            List<PlanDetail> details = planMapper.selectDetailsByVersionId(planVersionId);
            Path filePath = generateExcel(details, fields, includePriority, includeActual);
            exportTaskMapper.updateResult(task.id(), ExportTask.STATUS_SUCCEEDED, null, 0);
            log.info("Export generated: taskId={}, file={}", task.id(), filePath);
            return exportTaskMapper.selectById(task.id());
        } catch (Exception e) {
            exportTaskMapper.updateFailure(task.id(), ExportTask.STATUS_FAILED, "EXPORT_ERROR", e.getMessage(), 0);
            throw new BusinessException("MRP_EXPORT_ERROR", "Export failed: " + e.getMessage());
        }
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

            Path filePath = exportDir.resolve("plan_" + System.currentTimeMillis() + ".xlsx");
            try (var os = Files.newOutputStream(filePath)) {
                wb.write(os);
            }
            return filePath;
        }
    }
}
