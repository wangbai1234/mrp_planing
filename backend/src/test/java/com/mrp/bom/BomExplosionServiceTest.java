package com.mrp.bom;

import com.mrp.BaseIntegrationTest;
import com.mrp.bom.domain.BomDetail;
import com.mrp.bom.domain.BomExplosionResult;
import com.mrp.bom.local.repository.BomLocalMapper;
import com.mrp.bom.service.BomExplosionService;
import com.mrp.common.exception.ValidationException;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.domain.MaterialCategory;
import com.mrp.masterdata.repository.MaterialCategoryMapper;
import com.mrp.masterdata.repository.MaterialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class BomExplosionServiceTest extends BaseIntegrationTest {

    @Autowired
    private BomExplosionService bomExplosionService;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialCategoryMapper categoryMapper;

    @Autowired
    private BomLocalMapper bomLocalMapper;

    private MaterialCategory cat03;
    private MaterialCategory cat03001;
    private MaterialCategory cat03002;
    private MaterialCategory cat03003;
    private MaterialCategory cat03004;

    private Material matA;
    private Material matB;
    private Material matC;
    private Material matD;
    private Material matE;

    @BeforeEach
    void setUp() {
        // Create categories: 03 -> 03001, 03002, 03003, 03004
        cat03 = new MaterialCategory();
        cat03.setCode("03");
        cat03.setName("模具夹具");
        cat03.setParentId(null);
        cat03.setLevel(1);
        cat03.setSort(1);
        cat03.setEnabled(true);
        categoryMapper.insert(cat03);

        cat03001 = new MaterialCategory();
        cat03001.setCode("03001");
        cat03001.setName("工具(夹具及配套件)");
        cat03001.setParentId(cat03.getId());
        cat03001.setLevel(2);
        cat03001.setSort(1);
        cat03001.setEnabled(true);
        categoryMapper.insert(cat03001);

        cat03002 = new MaterialCategory();
        cat03002.setCode("03002");
        cat03002.setName("模具");
        cat03002.setParentId(cat03.getId());
        cat03002.setLevel(2);
        cat03002.setSort(2);
        cat03002.setEnabled(true);
        categoryMapper.insert(cat03002);

        cat03003 = new MaterialCategory();
        cat03003.setCode("03003");
        cat03003.setName("模型机");
        cat03003.setParentId(cat03.getId());
        cat03003.setLevel(2);
        cat03003.setSort(3);
        cat03003.setEnabled(true);
        categoryMapper.insert(cat03003);

        cat03004 = new MaterialCategory();
        cat03004.setCode("03004");
        cat03004.setName("手板模型机");
        cat03004.setParentId(cat03.getId());
        cat03004.setLevel(2);
        cat03004.setSort(4);
        cat03004.setEnabled(true);
        categoryMapper.insert(cat03004);

        // Create materials: A->03, B->03001, C->03002, D->03003, E->03004
        matA = createMaterial("MAT-A", "物料A", cat03.getId());
        matB = createMaterial("MAT-B", "物料B", cat03001.getId());
        matC = createMaterial("MAT-C", "物料C", cat03002.getId());
        matD = createMaterial("MAT-D", "物料D", cat03003.getId());
        matE = createMaterial("MAT-E", "物料E", cat03004.getId());
    }

    private Material createMaterial(String code, String name, Long categoryId) {
        Material m = new Material(null, code, name, null, null, null, null, null,
                null, null, null, true, null, null,
                Material.SOURCE_EXCEL_IMPORT, null, false, null, null, categoryId);
        materialMapper.insert(m);
        return materialMapper.selectByCode(code);
    }

    @Test
    void testTargetCategory03_onlyMatchesA() {
        // targetCategories = ["03"] -> only A
        BomExplosionResult result = bomExplosionService.explode(
                matA.id(), BigDecimal.valueOf(100), List.of("03"));

        assertEquals(1, result.getSchedulingNodeCount());
        assertEquals("MAT-A", result.getSchedulingNodes().get(0).getMaterialCode());
        assertEquals("03", result.getSchedulingNodes().get(0).getCategoryCode());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(
                result.getSchedulingNodes().get(0).getDemandQuantity()));
    }

    @Test
    void testTargetCategory03001_onlyMatchesB() {
        // targetCategories = ["03001"] -> only B
        BomExplosionResult result = bomExplosionService.explode(
                matA.id(), BigDecimal.valueOf(100), List.of("03001"));

        assertEquals(1, result.getSchedulingNodeCount());
        assertEquals("MAT-B", result.getSchedulingNodes().get(0).getMaterialCode());
        assertEquals("03001", result.getSchedulingNodes().get(0).getCategoryCode());
    }

    @Test
    void testTargetCategory03And03001_matchesAandB() {
        // targetCategories = ["03", "03001"] -> A and B
        BomExplosionResult result = bomExplosionService.explode(
                matA.id(), BigDecimal.valueOf(100), List.of("03", "03001"));

        assertEquals(2, result.getSchedulingNodeCount());
        List<String> codes = result.getSchedulingNodes().stream()
                .map(BomExplosionResult.SchedulingNode::getMaterialCode)
                .sorted()
                .toList();
        assertEquals(List.of("MAT-A", "MAT-B"), codes);
    }

    @Test
    void testTargetCategory03And03001And03002_matchesAandBandC() {
        // targetCategories = ["03", "03001", "03002"] -> A, B, C
        BomExplosionResult result = bomExplosionService.explode(
                matA.id(), BigDecimal.valueOf(100), List.of("03", "03001", "03002"));

        assertEquals(3, result.getSchedulingNodeCount());
        List<String> codes = result.getSchedulingNodes().stream()
                .map(BomExplosionResult.SchedulingNode::getMaterialCode)
                .sorted()
                .toList();
        assertEquals(List.of("MAT-A", "MAT-B", "MAT-C"), codes);
    }

    @Test
    void testSelecting03DoesNotAutoSelectChildren() {
        // Selecting 03 should NOT auto-select 03001~03004
        // This is verified by testTargetCategory03_onlyMatchesA which shows only A matches
        // (A is bound to 03, not to any child)
        BomExplosionResult result = bomExplosionService.explode(
                matA.id(), BigDecimal.valueOf(100), List.of("03"));

        // Only A should match, not B/C/D/E
        assertEquals(1, result.getSchedulingNodeCount());
        assertEquals("MAT-A", result.getSchedulingNodes().get(0).getMaterialCode());
    }

    @Test
    void testSelecting03And03001_doesNotAutoSelectOtherChildren() {
        // Selecting 03 + 03001 should NOT auto-select 03002~03004
        BomExplosionResult result = bomExplosionService.explode(
                matA.id(), BigDecimal.valueOf(100), List.of("03", "03001"));

        assertEquals(2, result.getSchedulingNodeCount());
        List<String> codes = result.getSchedulingNodes().stream()
                .map(BomExplosionResult.SchedulingNode::getMaterialCode)
                .sorted()
                .toList();
        // Only A and B, not C/D/E
        assertEquals(List.of("MAT-A", "MAT-B"), codes);
    }

    @Test
    void testBomQuantityCalculation() {
        // Create a BOM hierarchy:
        // TOP-A (root) -> MAT-A x2
        // MAT-A -> MAT-B x3
        // Target: ["03001"] should yield MAT-B = 100 * 2 * 3 = 600

        Material topA = createMaterial("TOP-A", "整机A", null);
        createBomDetail("TOP-A", "MAT-A", 2L);
        createBomDetail("MAT-A", "MAT-B", 3L);

        BomExplosionResult result = bomExplosionService.explode(
                topA.id(), BigDecimal.valueOf(100), List.of("03001"));

        assertEquals(1, result.getSchedulingNodeCount());
        assertEquals("MAT-B", result.getSchedulingNodes().get(0).getMaterialCode());
        assertEquals(0, BigDecimal.valueOf(600).compareTo(
                result.getSchedulingNodes().get(0).getDemandQuantity()));
    }

    @Test
    void testBomQuantitySingleLevel() {
        // TOP-A -> MAT-A x2
        // Target: ["03"] -> MAT-A = 100 * 2 = 200

        Material topA = createMaterial("TOP-A2", "整机A2", null);
        createBomDetail("TOP-A2", "MAT-A", 2L);

        BomExplosionResult result = bomExplosionService.explode(
                topA.id(), BigDecimal.valueOf(100), List.of("03"));

        assertEquals(1, result.getSchedulingNodeCount());
        assertEquals("MAT-A", result.getSchedulingNodes().get(0).getMaterialCode());
        assertEquals(0, BigDecimal.valueOf(200).compareTo(
                result.getSchedulingNodes().get(0).getDemandQuantity()));
    }

    @Test
    void testUnmatchedLeafMaterial() {
        // TOP-A -> MAT-A (hit 03) -> MAT-B (hit 03001)
        //              -> MAT-C (no BOM children, not in target)
        // MAT-C should appear in unmatched leaves

        Material topA = createMaterial("TOP-A3", "整机A3", null);
        createBomDetail("TOP-A3", "MAT-A", 1L);
        createBomDetail("TOP-A3", "MAT-C", 1L);

        BomExplosionResult result = bomExplosionService.explode(
                topA.id(), BigDecimal.valueOf(100), List.of("03"));

        // MAT-A matches 03, MAT-C doesn't match and has no BOM children
        assertEquals(1, result.getSchedulingNodeCount());
        assertEquals("MAT-A", result.getSchedulingNodes().get(0).getMaterialCode());

        // MAT-C should be in unmatched
        assertTrue(result.getUnmatchedLeafCount() > 0, "MAT-C should be in unmatched leaves");
        boolean hasMatC = result.getUnmatchedLeaves().stream()
                .anyMatch(l -> "MAT-C".equals(l.getMaterialCode()));
        assertTrue(hasMatC, "MAT-C should appear in unmatched leaves");
    }

    @Test
    void testValidation_emptyTargetCategories() {
        assertThrows(ValidationException.class, () ->
                bomExplosionService.explode(matA.id(), BigDecimal.valueOf(100), List.of()));
    }

    @Test
    void testValidation_nullDemandQuantity() {
        assertThrows(ValidationException.class, () ->
                bomExplosionService.explode(matA.id(), null, List.of("03")));
    }

    @Test
    void testValidation_zeroDemandQuantity() {
        assertThrows(ValidationException.class, () ->
                bomExplosionService.explode(matA.id(), BigDecimal.ZERO, List.of("03")));
    }

    @Test
    void testValidation_nonExistentCategory() {
        assertThrows(ValidationException.class, () ->
                bomExplosionService.explode(matA.id(), BigDecimal.valueOf(100), List.of("99")));
    }

    @Test
    void testDuplicateTargetCategories() {
        // Duplicate categories should be deduplicated
        BomExplosionResult result = bomExplosionService.explode(
                matA.id(), BigDecimal.valueOf(100), List.of("03", "03"));

        assertEquals(1, result.getSchedulingNodeCount());
        assertEquals("MAT-A", result.getSchedulingNodes().get(0).getMaterialCode());
    }

    private void createBomDetail(String parentCode, String childCode, Long childQty) {
        BomDetail detail = new BomDetail();
        detail.setParentCode(parentCode);
        detail.setChildCode(childCode);
        detail.setChildName(childCode);
        detail.setChildQty(childQty);
        detail.setVersion("V1.0");
        bomLocalMapper.insertDetail(detail);

        // If parent doesn't exist in bom_parent, insert it
        if (!bomLocalMapper.isParent(parentCode)) {
            com.mrp.bom.domain.BomMaterial parent = new com.mrp.bom.domain.BomMaterial();
            parent.setInvCode(parentCode);
            parent.setInvName(parentCode);
            parent.setVersion("V1.0");
            parent.setParentQty(1L);
            bomLocalMapper.insertParent(parent);
        }
    }
}
