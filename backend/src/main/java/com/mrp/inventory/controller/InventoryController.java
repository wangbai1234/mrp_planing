package com.mrp.inventory.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.importexport.excel.ParseError;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.TemplateGenerator;
import com.mrp.inventory.domain.InventoryDetail;
import com.mrp.inventory.domain.InventoryItem;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.service.InventoryImportService;
import com.mrp.task.domain.ImportTask;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {

    private final InventoryImportService inventoryImportService;
    private final TemplateGenerator templateGenerator;

    public InventoryController(InventoryImportService inventoryImportService, TemplateGenerator templateGenerator) {
        this.inventoryImportService = inventoryImportService;
        this.templateGenerator = templateGenerator;
    }

    // ========== Template download ==========

    @GetMapping("/inventory-imports/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] content = templateGenerator.generateInventoryTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory_template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .body(content);
    }

    // ========== Error report download ==========

    @GetMapping("/inventory-imports/{taskId}/errors")
    public ResponseEntity<byte[]> downloadErrorReport(@PathVariable Long taskId) throws IOException {
        ParseResult<InventoryItem> staging = inventoryImportService.getItemStagingResult(taskId);
        byte[] content = templateGenerator.generateInventoryErrorReport(staging.headers(), staging.rawRows(), staging.errors());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory_errors.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .body(content);
    }

    // ========== Upload and parse ==========

    @PostMapping("/inventory-imports/items")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadItem(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = CurrentUser.getUserId();
        ImportTask task = inventoryImportService.uploadItem(file.getInputStream(), file.getOriginalFilename(), userId);
        ParseResult<InventoryItem> staging = inventoryImportService.getItemStagingResult(task.id());

        List<Map<String, Object>> previewRows = buildPreviewRows(staging);

        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                "taskId", task.id(),
                "totalRows", staging.totalRows(),
                "successRows", staging.successRows(),
                "errorRows", staging.errorRows(),
                "errors", staging.errors(),
                "previewRows", previewRows
        )));
    }

    // ========== Confirm import ==========

    @PostMapping("/inventory-imports/items/{taskId}/confirm")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmItem(@PathVariable Long taskId) {
        Long userId = CurrentUser.getUserId();
        InventorySnapshot snapshot = inventoryImportService.confirmItem(taskId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "snapshotId", snapshot.getId(),
                "status", snapshot.getStatus()
        )));
    }

    // ========== Get staging result (for import-tasks endpoint) ==========

    @GetMapping("/inventory-import-tasks/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStaging(@PathVariable Long taskId) {
        ParseResult<InventoryItem> staging = inventoryImportService.getItemStagingResult(taskId);
        List<Map<String, Object>> previewRows = buildPreviewRows(staging);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "totalRows", staging.totalRows(),
                "successRows", staging.successRows(),
                "errorRows", staging.errorRows(),
                "errors", staging.errors(),
                "previewRows", previewRows
        )));
    }

    // ========== List snapshots ==========

    @GetMapping("/inventory-snapshots")
    public ResponseEntity<ApiResponse<List<InventorySnapshot>>> listSnapshots() {
        return ResponseEntity.ok(ApiResponse.ok(inventoryImportService.listSnapshots()));
    }

    // ========== Get snapshot details ==========

    @GetMapping("/inventory-snapshots/{snapshotId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSnapshotDetails(@PathVariable Long snapshotId,
                                                                               @RequestParam(defaultValue = "1") int page,
                                                                               @RequestParam(defaultValue = "20") int pageSize) {
        List<InventoryItem> items = inventoryImportService.getSnapshotItemsPage(snapshotId, page, pageSize);
        int total = inventoryImportService.countSnapshotItems(snapshotId);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (InventoryItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.id());
            row.put("productLine", item.productLine());
            row.put("inventoryCategory", item.inventoryCategory());
            row.put("materialId", item.materialId());
            row.put("projectModel", item.projectModel());
            row.put("materialName", item.materialName());
            row.put("supplierName", item.supplierName());
            row.put("productMode", item.productMode());
            row.put("odmSupplierQty", item.odmSupplierQty());
            row.put("xa400Qty", item.xa400Qty());
            row.put("xa378Qty", item.xa378Qty());
            row.put("xa226Qty", item.xa226Qty());
            row.put("shippingAvailableQty", item.shippingAvailableQty());
            row.put("classification", item.classification());
            row.put("barcode", item.barcode());
            row.put("remark", item.remark());
            row.put("orderPendingQty", item.orderPendingQty());
            row.put("salesStatus", item.salesStatus());
            row.put("parentRecord", item.parentRecord());
            rows.add(row);
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "items", rows,
                "total", total,
                "page", page,
                "pageSize", pageSize,
                "totalPages", (int) Math.ceil((double) total / pageSize)
        )));
    }

    // ========== Legacy endpoints (keep for backward compatibility) ==========

    @PostMapping("/inventory-imports")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("snapshotDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate snapshotDate) throws IOException {
        Long userId = CurrentUser.getUserId();
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
        Long userId = CurrentUser.getUserId();
        InventorySnapshot snapshot = inventoryImportService.confirm(taskId, snapshotDate, userId);
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }

    // ========== Helper ==========

    private List<Map<String, Object>> buildPreviewRows(ParseResult<InventoryItem> staging) {
        List<Map<String, Object>> previewRows = new ArrayList<>();

        for (ParseError error : staging.errors()) {
            Map<String, Object> errorRow = new LinkedHashMap<>();
            errorRow.put("type", "error");
            errorRow.put("row", error.rowNumber());
            errorRow.put("column", error.column());
            errorRow.put("value", error.originalValue());
            errorRow.put("errorCode", error.errorCode());
            errorRow.put("message", error.message());
            previewRows.add(errorRow);
        }

        int previewCount = Math.min(10, staging.rows().size());
        for (int i = 0; i < previewCount; i++) {
            InventoryItem item = staging.rows().get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "success");
            row.put("productLine", item.productLine());
            row.put("inventoryCategory", item.inventoryCategory());
            row.put("materialId", item.materialId());
            row.put("projectModel", item.projectModel());
            row.put("materialName", item.materialName());
            row.put("supplierName", item.supplierName());
            row.put("productMode", item.productMode());
            row.put("odmSupplierQty", item.odmSupplierQty());
            row.put("xa400Qty", item.xa400Qty());
            row.put("xa378Qty", item.xa378Qty());
            row.put("xa226Qty", item.xa226Qty());
            row.put("shippingAvailableQty", item.shippingAvailableQty());
            row.put("classification", item.classification());
            row.put("barcode", item.barcode());
            row.put("remark", item.remark());
            row.put("orderPendingQty", item.orderPendingQty());
            row.put("salesStatus", item.salesStatus());
            row.put("parentRecord", item.parentRecord());
            previewRows.add(row);
        }

        return previewRows;
    }
}
