package com.mrp.mrp.service;

import com.mrp.mrp.domain.CategoryRoleDetail;
import com.mrp.mrp.repository.CategoryRoleDetailMapper;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.masterdata.domain.Material;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
public class CategoryRoleVersionService {

    private final CategoryRoleDetailMapper detailMapper;
    private final MaterialMapper materialMapper;

    public CategoryRoleVersionService(CategoryRoleDetailMapper detailMapper, MaterialMapper materialMapper) {
        this.detailMapper = detailMapper;
        this.materialMapper = materialMapper;
    }

    public Map<String, CategoryRoleDetail> getDetailMap(Long versionId) {
        if (versionId == null) {
            return Map.of();
        }
        List<CategoryRoleDetail> details = detailMapper.selectByVersionId(versionId);
        return details.stream()
                .collect(Collectors.toMap(CategoryRoleDetail::getCategoryCode, d -> d, (a, b) -> a));
    }

    /**
     * Resolves the category-role configuration to material codes for one calculation.
     * Category roles are maintained by category code, while the BOM engine works by
     * material code; crossing those two namespaces directly causes every real material
     * to be reported as unmaintained.
     */
    public Map<String, CategoryRoleDetail> getMaterialRoleMap(Long versionId, Collection<String> materialCodes) {
        if (versionId == null || materialCodes == null || materialCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, CategoryRoleDetail> rolesByCategory = getDetailMap(versionId);
        if (rolesByCategory.isEmpty()) {
            return Map.of();
        }
        List<String> distinctCodes = materialCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
        if (distinctCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, CategoryRoleDetail> result = new LinkedHashMap<>();
        for (List<String> chunk : partition(distinctCodes, 500)) {
            for (Map<String, Object> row : materialMapper.selectCategoryCodesByMaterialCodes(chunk)) {
                String materialCode = value(row, "materialCode", "materialcode", "material_code");
                String categoryCode = value(row, "categoryCode", "categorycode", "category_code");
                CategoryRoleDetail role = rolesByCategory.get(categoryCode);
                if (materialCode != null && role != null) {
                    result.put(materialCode, role);
                }
            }
        }
        return result;
    }

    /** Distinguish a missing local material from an unmapped category role. */
    public Set<String> getExistingMaterialCodes(Collection<String> materialCodes) {
        if (materialCodes == null || materialCodes.isEmpty()) return Set.of();
        List<String> distinctCodes = materialCodes.stream()
                .filter(code -> code != null && !code.isBlank()).distinct().toList();
        Set<String> existing = new LinkedHashSet<>();
        for (List<String> chunk : partition(distinctCodes, 500)) {
            for (Material material : materialMapper.selectByCodes(chunk)) {
                existing.add(material.materialCode());
            }
        }
        return existing;
    }

    private List<List<String>> partition(List<String> values, int size) {
        if (values.isEmpty()) return Collections.emptyList();
        List<List<String>> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(values.size(), start + size)));
        }
        return chunks;
    }

    private String value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }
}
