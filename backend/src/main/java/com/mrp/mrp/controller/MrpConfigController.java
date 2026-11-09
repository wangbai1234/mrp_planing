package com.mrp.mrp.controller;

import com.mrp.common.response.ApiResponse;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.service.MrpConfigurationService;
import com.mrp.mrp.service.LegacyCalendarAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mrp")
public class MrpConfigController {
    
    private final MrpConfigurationService configService;
    private final LegacyCalendarAccessService calendars;
    
    public MrpConfigController(MrpConfigurationService configService, LegacyCalendarAccessService calendars) {
        this.configService = configService;
        this.calendars = calendars;
    }
    
    // ==================== 分类角色版本 ====================
    
    @PostMapping("/category-role-versions")
    public ResponseEntity<ApiResponse<CategoryRoleVersion>> createCategoryRoleVersion() {
        CategoryRoleVersion version = configService.createCategoryRoleVersion(CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(version));
    }
    
    @GetMapping("/category-role-versions")
    public ResponseEntity<ApiResponse<List<CategoryRoleVersion>>> getCategoryRoleVersions() {
        List<CategoryRoleVersion> versions = configService.getCategoryRoleVersions();
        return ResponseEntity.ok(ApiResponse.ok(versions));
    }
    
    @GetMapping("/category-role-versions/{id}")
    public ResponseEntity<ApiResponse<CategoryRoleVersion>> getCategoryRoleVersion(@PathVariable Long id) {
        List<CategoryRoleVersion> versions = configService.getCategoryRoleVersions();
        CategoryRoleVersion version = versions.stream().filter(v -> v.getId().equals(id)).findFirst().orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }
    
    @GetMapping("/category-role-versions/{id}/details")
    public ResponseEntity<ApiResponse<List<CategoryRoleDetail>>> getCategoryRoleDetails(@PathVariable Long id) {
        List<CategoryRoleDetail> details = configService.getCategoryRoleDetails(id);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }
    
    @PostMapping("/category-role-versions/{id}/details")
    public ResponseEntity<ApiResponse<Void>> saveCategoryRoleDetails(@PathVariable Long id, @RequestBody List<CategoryRoleDetail> details) {
        configService.saveCategoryRoleDetails(id, details);
        return ResponseEntity.ok(ApiResponse.ok());
    }
    
    @PostMapping("/category-role-versions/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> publishCategoryRoleVersion(@PathVariable Long id) {
        configService.publishCategoryRoleVersion(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
    
    // ==================== 物料策略版本 ====================
    
    @PostMapping("/material-policy-versions")
    public ResponseEntity<ApiResponse<MaterialPolicyVersion>> createMaterialPolicyVersion() {
        MaterialPolicyVersion version = configService.createMaterialPolicyVersion(CurrentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(version));
    }
    
    @GetMapping("/material-policy-versions")
    public ResponseEntity<ApiResponse<List<MaterialPolicyVersion>>> getMaterialPolicyVersions() {
        List<MaterialPolicyVersion> versions = configService.getMaterialPolicyVersions();
        return ResponseEntity.ok(ApiResponse.ok(versions));
    }
    
    @GetMapping("/material-policy-versions/{id}")
    public ResponseEntity<ApiResponse<MaterialPolicyVersion>> getMaterialPolicyVersion(@PathVariable Long id) {
        List<MaterialPolicyVersion> versions = configService.getMaterialPolicyVersions();
        MaterialPolicyVersion version = versions.stream().filter(v -> v.getId().equals(id)).findFirst().orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }
    
    @GetMapping("/material-policy-versions/{id}/details")
    public ResponseEntity<ApiResponse<List<MaterialPolicyDetail>>> getMaterialPolicyDetails(@PathVariable Long id) {
        List<MaterialPolicyDetail> details = configService.getMaterialPolicyDetails(id);
        return ResponseEntity.ok(ApiResponse.ok(details));
    }
    
    @PostMapping("/material-policy-versions/{id}/details")
    public ResponseEntity<ApiResponse<Void>> saveMaterialPolicyDetails(@PathVariable Long id, @RequestBody List<MaterialPolicyDetail> details) {
        configService.saveMaterialPolicyDetails(id, details);
        return ResponseEntity.ok(ApiResponse.ok());
    }
    
    @PostMapping("/material-policy-versions/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> publishMaterialPolicyVersion(@PathVariable Long id) {
        configService.publishMaterialPolicyVersion(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/material-policy-versions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMaterialPolicyVersion(@PathVariable Long id) {
        configService.deleteMaterialPolicyVersion(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
    
    // ==================== 生产日历版本 ====================
    
    @PostMapping("/production-calendar-versions")
    public ResponseEntity<ApiResponse<ProductionCalendarVersion>> createProductionCalendarVersion(@RequestParam String factoryCode) {
        ProductionCalendarVersion version = calendars.create(factoryCode);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }
    
    @GetMapping("/production-calendar-versions")
    public ResponseEntity<ApiResponse<List<ProductionCalendarVersion>>> getProductionCalendarVersions() {
        List<ProductionCalendarVersion> versions = calendars.list();
        return ResponseEntity.ok(ApiResponse.ok(versions));
    }
    
    // ==================== 批次创建所需版本列表 ====================
    
    @GetMapping("/batch-creation-versions")
    public ResponseEntity<ApiResponse<MrpConfigurationService.BatchCreationVersions>> getBatchCreationVersions() {
        MrpConfigurationService.BatchCreationVersions versions = calendars.batchCreationVersions();
        return ResponseEntity.ok(ApiResponse.ok(versions));
    }
    
    @GetMapping("/production-calendar-versions/{id}")
    public ResponseEntity<ApiResponse<ProductionCalendarVersion>> getProductionCalendarVersion(@PathVariable Long id) {
        ProductionCalendarVersion version = calendars.get(id);
        return ResponseEntity.ok(ApiResponse.ok(version));
    }
    
    @GetMapping("/production-calendar-versions/{id}/days")
    public ResponseEntity<ApiResponse<List<ProductionCalendarDay>>> getProductionCalendarDays(@PathVariable Long id) {
        List<ProductionCalendarDay> days = calendars.days(id);
        return ResponseEntity.ok(ApiResponse.ok(days));
    }
    
    @PostMapping("/production-calendar-versions/{id}/days")
    public ResponseEntity<ApiResponse<Void>> saveProductionCalendarDays(@PathVariable Long id, @RequestBody List<ProductionCalendarDay> days) {
        calendars.save(id, days);
        return ResponseEntity.ok(ApiResponse.ok());
    }
    
    @PostMapping("/production-calendar-versions/{id}/publish")
    public ResponseEntity<ApiResponse<Void>> publishProductionCalendarVersion(@PathVariable Long id) {
        calendars.publish(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/production-calendar-versions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProductionCalendarVersion(@PathVariable Long id) {
        calendars.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
