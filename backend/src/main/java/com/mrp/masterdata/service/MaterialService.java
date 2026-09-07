package com.mrp.masterdata.service;

import com.mrp.common.exception.BusinessException;
import com.mrp.common.exception.NotFoundException;
import com.mrp.common.exception.ValidationException;
import com.mrp.common.response.PageResult;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final MaterialMapper materialMapper;

    public MaterialService(MaterialMapper materialMapper) {
        this.materialMapper = materialMapper;
    }

    public Material getById(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new NotFoundException("MRP_MATERIAL_NOT_FOUND", "Material not found: " + id);
        }
        return material;
    }

    public Material getByCode(String materialCode) {
        Material material = materialMapper.selectByCode(materialCode);
        if (material == null) {
            throw new NotFoundException("MRP_MATERIAL_NOT_FOUND", "Material not found: " + materialCode);
        }
        return material;
    }

    public List<Material> list(String category, String region, Boolean isActive, String keyword) {
        return materialMapper.selectAll(category, region, isActive, keyword);
    }

    public PageResult<Material> listPage(String category, String region, Boolean isActive, String keyword,
                                         int page, int pageSize) {
        // Ensure valid page/pageSize
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 100) pageSize = 100;

        int offset = (page - 1) * pageSize;
        List<Material> items = materialMapper.selectPage(category, region, isActive, keyword, offset, pageSize);
        int total = materialMapper.countTotal(category, region, isActive, keyword);

        return new PageResult<>(items, total, page, pageSize);
    }

    @Transactional
    public Material create(Material material) {
        // Check duplicate code
        if (materialMapper.countByCode(material.materialCode()) > 0) {
            throw new ValidationException("Material code already exists: " + material.materialCode());
        }

        materialMapper.insert(material);
        log.info("Material created: code={}, id={}", material.materialCode(), material.id());
        return material;
    }

    @Transactional
    public Material update(Material material) {
        // Check exists
        Material existing = getById(material.id());

        // Check code conflict (if code changed)
        if (!existing.materialCode().equals(material.materialCode())) {
            if (materialMapper.countByCode(material.materialCode()) > 0) {
                throw new ValidationException("Material code already exists: " + material.materialCode());
            }
        }

        materialMapper.update(material);
        log.info("Material updated: id={}", material.id());
        return getById(material.id());
    }

    @Transactional
    public void delete(Long id) {
        getById(id); // Check exists
        materialMapper.logicalDelete(id);
        log.info("Material logically deleted: id={}", id);
    }

    @Transactional
    public List<Material> batchCreate(List<Material> materials) {
        if (materials.isEmpty()) {
            return materials;
        }

        // Check duplicates within batch
        long uniqueCodes = materials.stream().map(Material::materialCode).distinct().count();
        if (uniqueCodes < materials.size()) {
            throw new ValidationException("Duplicate material codes in batch");
        }

        // Check existing codes
        for (Material m : materials) {
            if (materialMapper.countByCode(m.materialCode()) > 0) {
                throw new ValidationException("Material code already exists: " + m.materialCode());
            }
        }

        // Batch insert (500 per batch)
        for (int i = 0; i < materials.size(); i += 500) {
            List<Material> batch = materials.subList(i, Math.min(i + 500, materials.size()));
            materialMapper.insertBatch(batch);
        }

        log.info("Batch created {} materials", materials.size());
        return materials;
    }

    @Transactional
    public Material syncFromExternal(String externalId, String materialCode, String materialName,
                                     String projectModel, String specModel, String unit,
                                     Long moq, Long mpq, String region, String attribute,
                                     String category, Boolean isActive, Integer leadTimeDays,
                                     String originPlace) {
        // Check if already synced by externalId
        Material existing = null;
        if (externalId != null) {
            // Use count + select to avoid loading all data
            if (materialMapper.countByExternalId(externalId) > 0) {
                // Find by externalId - use list with filter
                List<Material> found = materialMapper.selectAll(null, null, null, externalId);
                existing = found.stream()
                        .filter(m -> externalId.equals(m.externalId()))
                        .findFirst()
                        .orElse(null);
            }
        }

        if (existing != null) {
            // Update existing
            Material updated = new Material(
                    existing.id(), materialCode, materialName, projectModel, specModel, unit,
                    moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                    Material.SOURCE_API_SYNC, externalId, false, existing.createdAt(), null
            );
            materialMapper.update(updated);
            log.info("Material synced (updated): externalId={}, code={}", externalId, materialCode);
            return getById(existing.id());
        } else {
            // Check by material code
            existing = materialMapper.selectByCode(materialCode);
            if (existing != null) {
                // Update externalId
                Material updated = new Material(
                        existing.id(), materialCode, materialName, projectModel, specModel, unit,
                        moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                        Material.SOURCE_API_SYNC, externalId, false, existing.createdAt(), null
                );
                materialMapper.update(updated);
                log.info("Material synced (linked): code={}, externalId={}", materialCode, externalId);
                return getById(existing.id());
            } else {
                // Create new
                Material newMaterial = new Material(
                        null, materialCode, materialName, projectModel, specModel, unit,
                        moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                        Material.SOURCE_API_SYNC, externalId, false, null, null
                );
                materialMapper.insert(newMaterial);
                log.info("Material synced (created): code={}, externalId={}", materialCode, externalId);
                return newMaterial;
            }
        }
    }
}
