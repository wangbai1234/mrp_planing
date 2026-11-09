package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.importexport.excel.ParseError;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.StockPolicyExcelParser;
import com.mrp.mrp.domain.BusinessWeekDetail;
import com.mrp.mrp.domain.FactoryWarehouseMappingDetail;
import com.mrp.mrp.domain.StockPolicyDetail;
import com.mrp.mrp.service.Phase2ScopeConfigService;
import com.mrp.mrp.service.LegacyScopeVersionAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mrp")
public class Phase2ScopeConfigController {
    private final Phase2ScopeConfigService service;
    private final LegacyScopeVersionAccessService access;
    private final StockPolicyExcelParser stockPolicyExcelParser;

    public Phase2ScopeConfigController(Phase2ScopeConfigService service,
                                       StockPolicyExcelParser stockPolicyExcelParser, LegacyScopeVersionAccessService access) {
        this.service = service;
        this.access = access;
        this.stockPolicyExcelParser = stockPolicyExcelParser;
    }

    @PostMapping("/factory-warehouse-mapping-versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createFactoryWarehouseMapping(
            @RequestBody(required = false) EffectiveDateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(access.createMapping(
                request == null ? null : request.effectiveDate())));
    }

    @GetMapping("/factory-warehouse-mapping-versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listFactoryWarehouseMappings() {
        return ResponseEntity.ok(ApiResponse.ok(access.mappings()));
    }

    @GetMapping("/factory-warehouse-mapping-versions/{id}/details")
    public ResponseEntity<ApiResponse<List<FactoryWarehouseMappingDetail>>> getFactoryWarehouseDetails(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(access.mappingDetails(id)));
    }

    @PostMapping("/factory-warehouse-mapping-versions/{id}/details")
    public ResponseEntity<ApiResponse<Void>> saveFactoryWarehouseDetails(@PathVariable Long id,
                                                                          @RequestBody List<FactoryWarehouseMappingDetail> details) {
        access.saveMapping(id, details);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/factory-warehouse-mapping-versions/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> publishFactoryWarehouseMapping(@PathVariable Long id) {
        access.publishMapping(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/factory-warehouse-mapping-versions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFactoryWarehouseMapping(@PathVariable Long id) {
        access.deleteMapping(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/stock-policy-versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createStockPolicy(
            @RequestBody(required = false) EffectiveDateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(access.createStock(
                request == null ? null : request.effectiveDate())));
    }

    @PostMapping("/stock-policy-versions/{id}/incremental")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createIncrementalStockPolicy(
            @PathVariable Long id, @RequestBody IncrementalStockPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(access.incrementalStock(
                id, request.effectiveDate(), request.overrides())));
    }

    @PostMapping("/stock-policies/import/preview-legacy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewStockPolicyImport(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("rows", List.of(), "errors", List.of("该接口已停用"))));
    }

    @GetMapping("/stock-policy-versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listStockPolicies() {
        return ResponseEntity.ok(ApiResponse.ok(access.stocks()));
    }

    @GetMapping("/stock-policy-versions/{id}/details")
    public ResponseEntity<ApiResponse<List<StockPolicyDetail>>> getStockPolicyDetails(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(access.stockDetails(id)));
    }

    @PostMapping("/stock-policy-versions/{id}/details")
    public ResponseEntity<ApiResponse<Void>> saveStockPolicyDetails(@PathVariable Long id,
                                                                    @RequestBody List<StockPolicyDetail> details) {
        access.saveStock(id, details);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/stock-policy-versions/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> publishStockPolicy(@PathVariable Long id) {
        access.publishStock(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/stock-policy-versions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStockPolicy(@PathVariable Long id) {
        access.deleteStock(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/business-week-versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createBusinessWeek(@RequestBody BusinessMonthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.createBusinessWeekVersion(
                request.businessMonth(), CurrentUser.getUserId())));
    }

    @GetMapping("/business-week-versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listBusinessWeeks() {
        return ResponseEntity.ok(ApiResponse.ok(service.listBusinessWeekVersions()));
    }

    @GetMapping("/business-week-versions/{id}/details")
    public ResponseEntity<ApiResponse<List<BusinessWeekDetail>>> getBusinessWeekDetails(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getBusinessWeekDetails(id)));
    }

    @PostMapping("/business-week-versions/{id}/details")
    public ResponseEntity<ApiResponse<Void>> saveBusinessWeekDetails(@PathVariable Long id,
                                                                     @RequestBody List<BusinessWeekDetail> details) {
        service.saveBusinessWeekDetails(id, details);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/business-week-versions/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> publishBusinessWeek(@PathVariable Long id) {
        service.publishBusinessWeekVersion(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/business-week-versions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBusinessWeek(@PathVariable Long id) {
        service.deleteBusinessWeekVersion(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    public record EffectiveDateRequest(LocalDate effectiveDate) {}
    public record IncrementalStockPolicyRequest(LocalDate effectiveDate, List<StockPolicyDetail> overrides) {}
    public record BusinessMonthRequest(LocalDate businessMonth) {}
}
