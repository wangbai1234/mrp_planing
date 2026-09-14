package com.mrp.forecast.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.importexport.excel.ParseError;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.TemplateGenerator;
import com.mrp.task.domain.ImportTask;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.mrp.common.security.CurrentUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ForecastController {

    private final ForecastImportService forecastImportService;
    private final TemplateGenerator templateGenerator;

    public ForecastController(ForecastImportService forecastImportService, TemplateGenerator templateGenerator) {
        this.forecastImportService = forecastImportService;
        this.templateGenerator = templateGenerator;
    }

    @GetMapping("/forecast-versions")
    public ResponseEntity<ApiResponse<PageResult<ForecastVersion>>> listVersions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "3") int pageSize,
            @RequestParam(required = false) String keyword) {
        PageResult<ForecastVersion> result = forecastImportService.listVersionsPage(keyword, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/forecast-versions/{versionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVersionDetails(
            @PathVariable Long versionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        PageResult<Map<String, Object>> result = forecastImportService.getVersionMaterialsPage(versionId, keyword, page, pageSize);
        
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "items", result.items(),
                "total", result.total(),
                "page", result.page(),
                "pageSize", result.pageSize(),
                "totalPages", result.totalPages()
        )));
    }

    @GetMapping("/forecast-imports/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] content = templateGenerator.generateForecastTemplate();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"forecast_template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .body(content);
    }

    @GetMapping("/forecast-imports/{taskId}/errors")
    public ResponseEntity<byte[]> downloadErrorReport(@PathVariable Long taskId) throws IOException {
        ParseResult<ForecastDetail> staging = forecastImportService.getStagingResult(taskId);
        byte[] content = templateGenerator.generateForecastErrorReport(staging.headers(), staging.rawRows(), staging.errors());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"forecast_errors.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping("/forecast-imports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = CurrentUser.getUserId();
        ImportTask task = forecastImportService.upload(file.getInputStream(), file.getOriginalFilename(), userId);
        ParseResult<ForecastDetail> staging = forecastImportService.getStagingResult(task.id());

        // 准备预览数据：错误行 + 前10行成功数据
        List<Map<String, Object>> previewRows = new ArrayList<>();
        
        // 添加错误行
        for (ParseError error : staging.errors()) {
            Map<String, Object> errorRow = new HashMap<>();
            errorRow.put("type", "error");
            errorRow.put("row", error.rowNumber());
            errorRow.put("column", error.column());
            errorRow.put("value", error.originalValue());
            errorRow.put("errorCode", error.errorCode());
            errorRow.put("message", error.message());
            previewRows.add(errorRow);
        }
        
        // 添加前10行成功数据
        int previewCount = Math.min(10, staging.rows().size());
        for (int i = 0; i < previewCount; i++) {
            ForecastDetail detail = staging.rows().get(i);
            Map<String, Object> row = new HashMap<>();
            row.put("type", "success");
            row.put("factoryCode", detail.factoryCode());
            row.put("formType", detail.formType());
            row.put("materialId", detail.materialId());
            row.put("materialName", detail.materialName());
            row.put("project", detail.project());
            row.put("platform", detail.platform());
            row.put("mold", detail.mold());
            row.put("status", detail.status());
            row.put("planMonth", detail.planMonth().toString());
            row.put("forecastQty", detail.forecastQty());
            previewRows.add(row);
        }

        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                "taskId", task.id(),
                "recognizedMonths", staging.recognizedMonths(),
                "totalRows", staging.totalRows(),
                "successRows", staging.successRows(),
                "errorRows", staging.errorRows(),
                "errors", staging.errors(),
                "previewRows", previewRows
        )));
    }

    @PostMapping("/forecast-imports/{taskId}/confirm")
    public ResponseEntity<ApiResponse<ForecastVersion>> confirm(@PathVariable Long taskId) {
        Long userId = CurrentUser.getUserId();
        ForecastVersion version = forecastImportService.confirm(taskId, userId);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }

    @GetMapping("/import-tasks/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStaging(@PathVariable Long taskId) {
        ParseResult<ForecastDetail> staging = forecastImportService.getStagingResult(taskId);
        
        // 准备预览数据：错误行 + 前10行成功数据
        List<Map<String, Object>> previewRows = new ArrayList<>();
        
        // 添加错误行
        for (ParseError error : staging.errors()) {
            Map<String, Object> errorRow = new HashMap<>();
            errorRow.put("type", "error");
            errorRow.put("row", error.rowNumber());
            errorRow.put("column", error.column());
            errorRow.put("value", error.originalValue());
            errorRow.put("errorCode", error.errorCode());
            errorRow.put("message", error.message());
            previewRows.add(errorRow);
        }
        
        // 添加前10行成功数据
        int previewCount = Math.min(10, staging.rows().size());
        for (int i = 0; i < previewCount; i++) {
            ForecastDetail detail = staging.rows().get(i);
            Map<String, Object> row = new HashMap<>();
            row.put("type", "success");
            row.put("factoryCode", detail.factoryCode());
            row.put("formType", detail.formType());
            row.put("materialId", detail.materialId());
            row.put("materialName", detail.materialName());
            row.put("project", detail.project());
            row.put("platform", detail.platform());
            row.put("mold", detail.mold());
            row.put("status", detail.status());
            row.put("planMonth", detail.planMonth().toString());
            row.put("forecastQty", detail.forecastQty());
            previewRows.add(row);
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "recognizedMonths", staging.recognizedMonths(),
                "totalRows", staging.totalRows(),
                "successRows", staging.successRows(),
                "errorRows", staging.errorRows(),
                "errors", staging.errors(),
                "previewRows", previewRows
        )));
    }
}
