package com.mrp.masterdata.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.masterdata.domain.MaterialCategory;
import com.mrp.masterdata.service.MaterialCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/material-categories")
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;

    public MaterialCategoryController(MaterialCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<MaterialCategory>>> getTree() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getTree()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaterialCategory>>> list(
            @RequestParam(required = false) Boolean enabled) {
        if (Boolean.TRUE.equals(enabled)) {
            return ResponseEntity.ok(ApiResponse.ok(categoryService.listEnabled()));
        }
        return ResponseEntity.ok(ApiResponse.ok(categoryService.listAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialCategory>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MaterialCategory>> create(@RequestBody MaterialCategory category) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.create(category)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MaterialCategory>> update(@PathVariable Long id,
                                                                 @RequestBody MaterialCategory category) {
        category.setId(id);
        // Load existing to preserve fields not provided in request
        MaterialCategory existing = categoryService.getById(id);
        if (category.getCode() == null) category.setCode(existing.getCode());
        if (category.getParentId() == null) category.setParentId(existing.getParentId());
        if (category.getLevel() == null) category.setLevel(existing.getLevel());
        if (category.getEnabled() == null) category.setEnabled(existing.getEnabled());
        if (category.getSort() == null) category.setSort(existing.getSort());
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(category)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @RequestMapping(value = "/{id}/enabled", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<ApiResponse<Void>> toggleEnabled(@PathVariable Long id,
                                                            @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_PARAM", "enabled is required"));
        }
        categoryService.toggleEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
