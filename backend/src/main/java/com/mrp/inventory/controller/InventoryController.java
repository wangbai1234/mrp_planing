package com.mrp.inventory.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.service.InventoryImportService;
import com.mrp.task.domain.ImportTask;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryImportService inventoryImportService;

    public InventoryController(InventoryImportService inventoryImportService) {
        this.inventoryImportService = inventoryImportService;
    }

    @PostMapping("/inventory-imports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("snapshotDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate snapshotDate) throws IOException {
        Long userId = 1L;
        ImportTask task = inventoryImportService.upload(file.getInputStream(), file.getOriginalFilename(), snapshotDate, userId);
        ParseResult<InventoryDetail> staging = inventoryImportService.getStagingResult(task.id());

        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                "taskId", task.id(),
                "totalRows", staging.totalRows(),
                "successRows", staging.successRows(),
                "errorRows", staging.errorRows(),
                "errors", staging.errors()
        )));
    }

    @PostMapping("/inventory-imports/{taskId}/confirm")
    public ResponseEntity<ApiResponse<InventorySnapshot>> confirm(
            @PathVariable Long taskId,
            @RequestParam("snapshotDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate snapshotDate) {
        Long userId = 1L;
        InventorySnapshot snapshot = inventoryImportService.confirm(taskId, snapshotDate, userId);
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }
}
