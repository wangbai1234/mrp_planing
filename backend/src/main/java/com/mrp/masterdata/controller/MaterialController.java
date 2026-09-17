package com.mrp.masterdata.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.importexport.excel.TemplateGenerator;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.service.MaterialImportService;
import com.mrp.masterdata.service.MaterialImportService.ImportResult;
import com.mrp.masterdata.service.MaterialImportService.StagingResult;
import com.mrp.masterdata.service.MaterialService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MaterialController {

    private final MaterialService materialService;
    private final MaterialImportService materialImportService;
    private final TemplateGenerator templateGenerator;

    public MaterialController(MaterialService materialService, MaterialImportService materialImportService,
                              TemplateGenerator templateGenerator) {
        this.materialService = materialService;
        this.materialImportService = materialImportService;
        this.templateGenerator = templateGenerator;
    }

    // CRUD APIs

    @GetMapping("/materials/{id}")
    public ResponseEntity<ApiResponse<Material>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.getById(id)));
    }

    @GetMapping("/materials")
    public ResponseEntity<ApiResponse<PageResult<Material>>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Long materialCategoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<Material> result = materialService.listPage(category, region, isActive, keyword,
                materialCategoryId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/materials")
    public ResponseEntity<ApiResponse<Material>> create(@RequestBody Material material) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.create(material)));
    }

    @PutMapping("/materials/{id}")
    public ResponseEntity<ApiResponse<Material>> update(@PathVariable Long id, @RequestBody Material material) {
        Material updated = materialService.update(material);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/materials/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        materialService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/materials/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchCreate(@RequestBody List<Material> materials) {
        List<Material> created = materialService.batchCreate(materials);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "count", created.size(),
                "message", "Batch created successfully"
        )));
    }

    @PostMapping("/materials/sync")
    public ResponseEntity<ApiResponse<Material>> sync(@RequestBody Map<String, Object> payload) {
        Material synced = materialService.syncFromExternal(
                (String) payload.get("externalId"),
                (String) payload.get("materialCode"),
                (String) payload.get("materialName"),
                (String) payload.get("projectModel"),
                (String) payload.get("specModel"),
                (String) payload.get("unit"),
                payload.get("moq") != null ? Long.valueOf(payload.get("moq").toString()) : null,
                payload.get("mpq") != null ? Long.valueOf(payload.get("mpq").toString()) : null,
                (String) payload.get("region"),
                (String) payload.get("attribute"),
                (String) payload.get("category"),
                payload.get("isActive") != null ? Boolean.valueOf(payload.get("isActive").toString()) : true,
                payload.get("leadTimeDays") != null ? Integer.valueOf(payload.get("leadTimeDays").toString()) : null,
                (String) payload.get("originPlace")
        );
        return ResponseEntity.ok(ApiResponse.ok(synced));
    }

    // Template Download

    @GetMapping("/materials/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] content = templateGenerator.generateMaterialTemplate();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"material_template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(content.length)
                .body(content);
    }

    // Import APIs

    /**
     * Upload and parse Excel file
     * Returns checksum for confirmation
     */
    @PostMapping("/materials/import")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        StagingResult staging = materialImportService.upload(file.getInputStream(), file.getOriginalFilename());

        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of(
                "checksum", staging.checksum(),
                "totalRows", staging.result().totalRows(),
                "successRows", staging.result().successRows(),
                "errorRows", staging.result().errorRows(),
                "errors", staging.result().errors()
        )));
    }

    /**
     * Confirm import with batch processing
     */
    @PostMapping("/materials/import/confirm")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirm(
            @RequestBody Map<String, Object> payload) {
        String checksum = (String) payload.get("checksum");
        boolean skipDuplicates = payload.get("skipDuplicates") != null ? 
                Boolean.parseBoolean(payload.get("skipDuplicates").toString()) : true;

        ImportResult result = materialImportService.confirm(checksum, skipDuplicates);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "imported", result.imported(),
                "skipped", result.skipped(),
                "failed", result.failed(),
                "total", result.total()
        )));
    }

    /**
     * Update material category from Excel file
     */
    @PostMapping("/materials/update-category")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCategory(
            @RequestParam("file") MultipartFile file) throws IOException {
        Map<String, Object> result = materialService.updateCategoryFromExcel(file.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
