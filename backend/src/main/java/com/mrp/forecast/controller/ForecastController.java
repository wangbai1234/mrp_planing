package com.mrp.forecast.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.task.domain.ImportTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ForecastController {

    private final ForecastImportService forecastImportService;

    public ForecastController(ForecastImportService forecastImportService) {
        this.forecastImportService = forecastImportService;
    }

    @GetMapping("/forecast-versions")
    public ResponseEntity<ApiResponse<List<ForecastVersion>>> listVersions() {
        return ResponseEntity.ok(ApiResponse.ok(forecastImportService.listVersions()));
    }

    @PostMapping("/forecast-imports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        // TODO: get userId from JWT
        Long userId = 1L;
        ImportTask task = forecastImportService.upload(file.getInputStream(), file.getOriginalFilename(), userId);
        ParseResult<ForecastDetail> staging = forecastImportService.getStagingResult(task.id());

        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                "taskId", task.id(),
                "recognizedMonths", staging.recognizedMonths(),
                "totalRows", staging.totalRows(),
                "successRows", staging.successRows(),
                "errorRows", staging.errorRows(),
                "errors", staging.errors()
        )));
    }

    @PostMapping("/forecast-imports/{taskId}/confirm")
    public ResponseEntity<ApiResponse<ForecastVersion>> confirm(@PathVariable Long taskId) {
        Long userId = 1L;
        ForecastVersion version = forecastImportService.confirm(taskId, userId);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }

    @GetMapping("/import-tasks/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStaging(@PathVariable Long taskId) {
        ParseResult<ForecastDetail> staging = forecastImportService.getStagingResult(taskId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "recognizedMonths", staging.recognizedMonths(),
                "totalRows", staging.totalRows(),
                "successRows", staging.successRows(),
                "errorRows", staging.errorRows(),
                "errors", staging.errors()
        )));
    }
}
