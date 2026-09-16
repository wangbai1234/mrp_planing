package com.mrp.inventory;

import com.mrp.MrpApiApplication;
import com.mrp.importexport.excel.InventoryExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.TestExcelFileBuilder;
import com.mrp.inventory.domain.InventoryDetail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MrpApiApplication.class)
@ActiveProfiles("test")
class InventoryExcelParserTest {

    @Autowired
    private InventoryExcelParser parser;

    @TempDir
    Path tempDir;

    @Test
    void parseTemplate_shouldNotCrash() throws Exception {
        Path template = new ClassPathResource("test-data/MRP库存快照导入模板.xlsx").getFile().toPath();

        ParseResult<InventoryDetail> result = parser.parse(template, 999L);

        assertNotNull(result);
        System.out.println("=== Inventory Parse Result ===");
        System.out.println("Total rows: " + result.totalRows());
        System.out.println("Success rows: " + result.successRows());
        System.out.println("Error rows: " + result.errorRows());
        if (result.hasErrors()) {
            result.errors().forEach(e -> System.out.println("Error: " + e));
        }
    }

    @Test
    void parseTemplate_materialIdNotDate() throws Exception {
        Path template = new ClassPathResource("test-data/MRP库存快照导入模板.xlsx").getFile().toPath();

        ParseResult<InventoryDetail> result = parser.parse(template, 999L);

        for (InventoryDetail detail : result.rows()) {
            assertNotNull(detail.materialId(), "material_id should not be null");
            assertFalse(detail.materialId().matches("\\d{4}-\\d{2}-\\d{2}.*"),
                    "material_id should not be date format: " + detail.materialId());
        }
    }

    @Test
    void parseTemplate_headersNotNull() throws Exception {
        Path template = new ClassPathResource("test-data/MRP库存快照导入模板.xlsx").getFile().toPath();

        ParseResult<InventoryDetail> result = parser.parse(template, 999L);

        assertNotNull(result.headers(), "Headers should not be null");
    }

    @Test
    void parseTemplate_rawRowsNotNull() throws Exception {
        Path template = new ClassPathResource("test-data/MRP库存快照导入模板.xlsx").getFile().toPath();

        ParseResult<InventoryDetail> result = parser.parse(template, 999L);

        assertNotNull(result.rawRows(), "Raw rows should not be null");
    }

    @Test
    void parseTemplate_materialIdIsText() throws Exception {
        Path template = new ClassPathResource("test-data/MRP库存快照导入模板.xlsx").getFile().toPath();

        ParseResult<InventoryDetail> result = parser.parse(template, 999L);

        for (InventoryDetail detail : result.rows()) {
            // Material ID should not be scientific notation
            assertFalse(detail.materialId().matches("\\d\\.\\d+E\\d+"),
                    "material_id should not be scientific notation: " + detail.materialId());
        }
    }

    @Test
    void parseTemplate_quantityIsNumeric() throws Exception {
        Path template = new ClassPathResource("test-data/MRP库存快照导入模板.xlsx").getFile().toPath();

        ParseResult<InventoryDetail> result = parser.parse(template, 999L);

        for (InventoryDetail detail : result.rows()) {
            // Quantity should be numeric
            assertNotNull(detail.quantity(), "quantity should not be null");
            assertTrue(detail.quantity() >= 0, "quantity should be non-negative: " + detail.quantity());
        }
    }

    @Test
    void parse_multipleWarehouses() throws Exception {
        String[][] data = {
            {"6830AA800561", "YH-FG-01", "永惠成品仓", "良品仓", "永惠", "200"},
            {"6830AA800561", "YH-WIP-01", "永惠在制品仓", "在制品仓", "永惠", "50"},
            {"6830AA800561", "APK-FG-01", "APK成品仓", "良品仓", "APK", "150"},
        };
        Path file = TestExcelFileBuilder.createInventoryFile(tempDir, "inventory-multi-warehouse.xlsx", data);

        ParseResult<InventoryDetail> result = parser.parse(file, 999L);

        assertNotNull(result);
        assertFalse(result.rows().isEmpty(), "Should parse inventory rows");

        var yonghuiItems = result.rows().stream()
                .filter(i -> "永惠".equals(i.factoryCode()))
                .toList();
        var apkItems = result.rows().stream()
                .filter(i -> "APK".equals(i.factoryCode()))
                .toList();

        assertFalse(yonghuiItems.isEmpty(), "Should have 永惠 items");
        assertFalse(apkItems.isEmpty(), "Should have APK items");
        assertEquals(2, yonghuiItems.size(), "永惠 should have 2 warehouses");
        assertEquals(1, apkItems.size(), "APK should have 1 warehouse");
    }

    @Test
    void parse_excludeDefectiveWarehouse() throws Exception {
        // Parser marks defective warehouse as isInCalc=false (still in results, but excluded from calculation)
        // Checks: !"不良品".equals(warehouseType) && !"不良品仓".equals(warehouseName)
        String[][] data = {
            {"6830AA800561", "YH-FG-01", "永惠成品仓", "良品仓", "永惠", "200"},
            {"6830AA800561", "YH-BAD-01", "永惠不良品仓", "不良品", "永惠", "46"},
        };
        Path file = TestExcelFileBuilder.createInventoryFile(tempDir, "inventory-with-defective.xlsx", data);

        ParseResult<InventoryDetail> result = parser.parse(file, 999L);

        assertNotNull(result);
        assertEquals(2, result.rows().size(), "Should parse both rows");

        // Good warehouse should be in calculation
        InventoryDetail goodRow = result.rows().stream()
                .filter(i -> i.warehouseName() != null && i.warehouseName().contains("成品"))
                .findFirst().orElse(null);
        assertNotNull(goodRow, "Good warehouse row should exist");
        assertTrue(goodRow.isInCalculation(), "Good warehouse should be in calculation");

        // Defective warehouse should NOT be in calculation
        InventoryDetail defectiveRow = result.rows().stream()
                .filter(i -> i.warehouseName() != null && i.warehouseName().contains("不良"))
                .findFirst().orElse(null);
        assertNotNull(defectiveRow, "Defective warehouse row should exist in results");
        assertFalse(defectiveRow.isInCalculation(), "Defective warehouse should not be in calculation");
    }

    @Test
    void parse_emptyFile() throws Exception {
        Path file = TestExcelFileBuilder.createEmptyInventoryFile(tempDir);

        ParseResult<InventoryDetail> result = parser.parse(file, 999L);

        assertNotNull(result);
        assertTrue(result.rows().isEmpty(), "Empty file should produce no rows");
        assertFalse(result.hasErrors(), "Empty file should not produce errors");
    }
}
