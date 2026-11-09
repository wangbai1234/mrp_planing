package com.mrp.mrp.service;

import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.CategoryRoleDetail;
import com.mrp.mrp.repository.CategoryRoleDetailMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.mrp.masterdata.domain.Material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryRoleVersionServiceTest {

    @Test
    void resolvesCategoryRoleToMaterialCode() {
        CategoryRoleDetailMapper detailMapper = mock(CategoryRoleDetailMapper.class);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        CategoryRoleVersionService service = new CategoryRoleVersionService(detailMapper, materialMapper);

        CategoryRoleDetail role = new CategoryRoleDetail();
        role.setCategoryCode("02001");
        role.setRole("HEAD");
        role.setEnabled(true);
        when(detailMapper.selectByVersionId(7L)).thenReturn(List.of(role));
        when(materialMapper.selectCategoryCodesByMaterialCodes(List.of("MAT-HEAD")))
                .thenReturn(List.of(Map.of("materialCode", "MAT-HEAD", "categoryCode", "02001")));

        Map<String, CategoryRoleDetail> result = service.getMaterialRoleMap(7L, List.of("MAT-HEAD"));

        assertEquals("HEAD", result.get("MAT-HEAD").getRole());
    }

    @Test
    void leavesMaterialWithoutMaintainedCategoryUnresolved() {
        CategoryRoleDetailMapper detailMapper = mock(CategoryRoleDetailMapper.class);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        CategoryRoleVersionService service = new CategoryRoleVersionService(detailMapper, materialMapper);

        CategoryRoleDetail role = new CategoryRoleDetail();
        role.setCategoryCode("02001");
        role.setRole("HEAD");
        when(detailMapper.selectByVersionId(7L)).thenReturn(List.of(role));
        when(materialMapper.selectCategoryCodesByMaterialCodes(List.of("MAT-UNKNOWN")))
                .thenReturn(List.of(Map.of("materialCode", "MAT-UNKNOWN")));

        Map<String, CategoryRoleDetail> result = service.getMaterialRoleMap(7L, List.of("MAT-UNKNOWN"));

        assertFalse(result.containsKey("MAT-UNKNOWN"));
    }

    @Test
    void distinguishesExistingLocalMaterialsFromBomOnlyCodes() {
        CategoryRoleDetailMapper detailMapper = mock(CategoryRoleDetailMapper.class);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        CategoryRoleVersionService service = new CategoryRoleVersionService(detailMapper, materialMapper);
        Material existing = new Material(null, "IN-MASTER", null, null, null, null, null, null,
                null, null, null, true, null, null, null, null, false, null, null, null);
        when(materialMapper.selectByCodes(List.of("IN-MASTER", "BOM-ONLY"))).thenReturn(List.of(existing));

        assertEquals(Set.of("IN-MASTER"), service.getExistingMaterialCodes(List.of("IN-MASTER", "BOM-ONLY")));
    }
}
