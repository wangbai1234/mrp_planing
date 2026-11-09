package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.response.PageResult;
import com.mrp.mrp.service.ConfigFactoryAccessService;
import com.mrp.mrp.service.FactoryMasterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mrp/factory-master")
public class FactoryMasterController {
    private final FactoryMasterService factories;
    private final ConfigFactoryAccessService access;

    public FactoryMasterController(FactoryMasterService factories, ConfigFactoryAccessService access) {
        this.factories = factories;
        this.access = access;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<String> visible = access.visibleMasterCodes();
        return ApiResponse.ok(factories.list().stream().filter(row -> visible.contains(row.get("factoryCode"))).toList());
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(factories.page(keyword, status, page, pageSize, access.visibleMasterCodes()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody FactoryMasterService.FactoryInput input) {
        access.requireScope(input.factoryCode());
        return ApiResponse.ok(factories.create(input));
    }

    @PutMapping("/{code}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String code, @RequestBody FactoryMasterService.FactoryInput input) {
        access.requireScope(code);
        return ApiResponse.ok(factories.update(code, input));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String code) {
        access.requireScope(code);
        factories.delete(code);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
