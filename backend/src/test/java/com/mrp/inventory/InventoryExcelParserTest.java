package com.mrp.inventory;

import com.mrp.MrpApiApplication;
import com.mrp.importexport.excel.InventoryExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.inventory.domain.InventoryDetail;
import org.junit.jupiter.api.Test;
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
}
