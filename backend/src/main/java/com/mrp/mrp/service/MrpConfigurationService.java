package com.mrp.mrp.service;

import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MrpConfigurationService {
    
    private final ConfigMasterService configMasterService;
    private final CategoryRoleVersionMapper categoryRoleVersionMapper;
    private final CategoryRoleDetailMapper categoryRoleDetailMapper;
    private final MaterialPolicyVersionMapper materialPolicyVersionMapper;
    private final MaterialPolicyDetailMapper materialPolicyDetailMapper;
    private final ProductionCalendarVersionMapper productionCalendarVersionMapper;
    private final ProductionCalendarDayMapper productionCalendarDayMapper;
    
    public MrpConfigurationService(
            CategoryRoleVersionMapper categoryRoleVersionMapper,
            CategoryRoleDetailMapper categoryRoleDetailMapper,
            MaterialPolicyVersionMapper materialPolicyVersionMapper,
            MaterialPolicyDetailMapper materialPolicyDetailMapper,
            ProductionCalendarVersionMapper productionCalendarVersionMapper,
            ProductionCalendarDayMapper productionCalendarDayMapper, ConfigMasterService configMasterService) {
        this.configMasterService = configMasterService;
        this.categoryRoleVersionMapper = categoryRoleVersionMapper;
        this.categoryRoleDetailMapper = categoryRoleDetailMapper;
        this.materialPolicyVersionMapper = materialPolicyVersionMapper;
        this.materialPolicyDetailMapper = materialPolicyDetailMapper;
        this.productionCalendarVersionMapper = productionCalendarVersionMapper;
        this.productionCalendarDayMapper = productionCalendarDayMapper;
    }
    
    // ==================== 分类角色版本 ====================
    
    @Transactional
    public CategoryRoleVersion createCategoryRoleVersion(Long createdBy) {
        CategoryRoleVersion version = new CategoryRoleVersion();
        version.setVersionNo(generateVersionNo());
        version.setStatus("DRAFT");
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        categoryRoleVersionMapper.insert(version);
        return version;
    }
    
    @Transactional
    public void saveCategoryRoleDetails(Long versionId, List<CategoryRoleDetail> details) {
        CategoryRoleVersion version = categoryRoleVersionMapper.selectById(versionId);
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Version not found or not in DRAFT status");
        }

        // Delete existing details before inserting new ones to avoid duplicates
        categoryRoleDetailMapper.deleteByVersionId(versionId);

        for (CategoryRoleDetail detail : details) {
            detail.setVersionId(versionId);
            detail.setRevision(0);
            categoryRoleDetailMapper.insert(detail);
        }
    }
    
    @Transactional
    public void publishCategoryRoleVersion(Long versionId) {
        CategoryRoleVersion version = categoryRoleVersionMapper.selectById(versionId);
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Version not found or not in DRAFT status");
        }
        
        List<CategoryRoleDetail> details = categoryRoleDetailMapper.selectByVersionId(versionId);
        String checksum = computeChecksum(details);
        
        version.setStatus("PUBLISHED");
        version.setChecksum(checksum);
        version.setPublishedAt(LocalDateTime.now());
        categoryRoleVersionMapper.update(version);
    }
    
    public List<CategoryRoleVersion> getCategoryRoleVersions() {
        return categoryRoleVersionMapper.selectAll();
    }
    
    public List<CategoryRoleDetail> getCategoryRoleDetails(Long versionId) {
        return categoryRoleDetailMapper.selectByVersionId(versionId);
    }
    
    // ==================== 物料策略版本 ====================
    
    @Transactional
    public MaterialPolicyVersion createMaterialPolicyVersion(Long createdBy) {
        MaterialPolicyVersion version = new MaterialPolicyVersion();
        version.setVersionNo(generateVersionNo());
        version.setStatus("DRAFT");
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        materialPolicyVersionMapper.insert(version);
        return version;
    }
    
    @Transactional
    public void saveMaterialPolicyDetails(Long versionId, List<MaterialPolicyDetail> details) {
        MaterialPolicyVersion version = materialPolicyVersionMapper.selectById(versionId);
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Version not found or not in DRAFT status");
        }

        // Delete existing details before inserting new ones to avoid duplicates
        configMasterService.validateMaterialPolicyDetails(details);
        materialPolicyDetailMapper.deleteByVersionId(versionId);

        for (MaterialPolicyDetail detail : details) {
            detail.setVersionId(versionId);
            detail.setRevision(0);
            materialPolicyDetailMapper.insert(detail);
        }
    }
    
    @Transactional
    public void publishMaterialPolicyVersion(Long versionId) {
        MaterialPolicyVersion version = materialPolicyVersionMapper.selectById(versionId);
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Version not found or not in DRAFT status");
        }
        
        List<MaterialPolicyDetail> details = materialPolicyDetailMapper.selectByVersionId(versionId);
        configMasterService.validateMaterialPolicyDetails(details);
        String checksum = computeChecksum(details);
        
        version.setStatus("PUBLISHED");
        version.setChecksum(checksum);
        version.setPublishedAt(LocalDateTime.now());
        materialPolicyVersionMapper.update(version);
    }

    @Transactional
    public void deleteMaterialPolicyVersion(Long versionId) {
        MaterialPolicyVersion version = materialPolicyVersionMapper.selectById(versionId);
        if (version == null) throw new IllegalStateException("物料策略版本不存在");
        if (!"DRAFT".equals(version.getStatus())) throw new IllegalStateException("已发布的物料策略版本不允许删除");
        if (materialPolicyVersionMapper.markDeleted(versionId) == 0) {
            throw new IllegalStateException("已发布的物料策略版本不允许删除");
        }
    }
    
    public List<MaterialPolicyVersion> getMaterialPolicyVersions() {
        return materialPolicyVersionMapper.selectAll();
    }

    public MaterialPolicyVersion getMaterialPolicyVersion(Long versionId) {
        return materialPolicyVersionMapper.selectById(versionId);
    }
    
    public List<MaterialPolicyDetail> getMaterialPolicyDetails(Long versionId) {
        return materialPolicyDetailMapper.selectByVersionId(versionId);
    }
    
    // ==================== 生产日历版本 ====================
    
    @Transactional
    public ProductionCalendarVersion createProductionCalendarVersion(String factoryCode, Long createdBy) {
        ProductionCalendarVersion version = new ProductionCalendarVersion();
        version.setVersionNo(generateVersionNo());
        version.setFactoryCode(factoryCode);
        version.setStatus("DRAFT");
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        productionCalendarVersionMapper.insert(version);
        return version;
    }
    
    @Transactional
    public void saveProductionCalendarDays(Long versionId, List<ProductionCalendarDay> days) {
        ProductionCalendarVersion version = productionCalendarVersionMapper.selectById(versionId);
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Version not found or not in DRAFT status");
        }

        // Delete existing days before inserting new ones to avoid duplicates
        productionCalendarDayMapper.deleteByVersionId(versionId);

        for (ProductionCalendarDay day : days) {
            day.setVersionId(versionId);
            day.setFactoryCode(version.getFactoryCode());
            day.setRevision(0);
            productionCalendarDayMapper.insert(day);
        }
    }
    
    @Transactional
    public void publishProductionCalendarVersion(Long versionId) {
        ProductionCalendarVersion version = productionCalendarVersionMapper.selectById(versionId);
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new IllegalStateException("Version not found or not in DRAFT status");
        }
        
        List<ProductionCalendarDay> days = productionCalendarDayMapper.selectByVersionId(versionId);
        String checksum = computeChecksum(days);
        
        version.setStatus("PUBLISHED");
        version.setChecksum(checksum);
        version.setPublishedAt(LocalDateTime.now());
        productionCalendarVersionMapper.update(version);
    }

    @Transactional
    public void deleteProductionCalendarVersion(Long versionId) {
        ProductionCalendarVersion version = productionCalendarVersionMapper.selectById(versionId);
        if (version == null) throw new IllegalStateException("生产日历版本不存在");
        if (!"DRAFT".equals(version.getStatus())) throw new IllegalStateException("已发布的生产日历版本不允许删除");
        if (productionCalendarVersionMapper.markDeleted(versionId) == 0) {
            throw new IllegalStateException("已发布的生产日历版本不允许删除");
        }
    }
    
    public List<ProductionCalendarVersion> getProductionCalendarVersions() {
        return productionCalendarVersionMapper.selectAll();
    }

    public ProductionCalendarVersion getProductionCalendarVersion(Long versionId) {
        return productionCalendarVersionMapper.selectById(versionId);
    }

    public CategoryRoleVersion getCategoryRoleVersion(Long versionId) {
        return categoryRoleVersionMapper.selectById(versionId);
    }
    
    public List<ProductionCalendarDay> getProductionCalendarDays(Long versionId) {
        return productionCalendarDayMapper.selectByVersionId(versionId);
    }
    
    // ==================== 批次创建所需版本列表 ====================
    
    public BatchCreationVersions getBatchCreationVersions() {
        List<CategoryRoleVersion> categoryRoleVersions = categoryRoleVersionMapper.selectAll();
        List<MaterialPolicyVersion> materialPolicyVersions = materialPolicyVersionMapper.selectAll();
        List<ProductionCalendarVersion> productionCalendarVersions = productionCalendarVersionMapper.selectAll();
        
        return new BatchCreationVersions(
                categoryRoleVersions,
                materialPolicyVersions,
                productionCalendarVersions
        );
    }
    
    public record BatchCreationVersions(
            List<CategoryRoleVersion> categoryRoleVersions,
            List<MaterialPolicyVersion> materialPolicyVersions,
            List<ProductionCalendarVersion> productionCalendarVersions
    ) {}
    
    // ==================== 辅助方法 ====================
    
    private Integer generateVersionNo() {
        return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
    }
    
    private String computeChecksum(Object data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.toString().getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
