package com.mrp.inventory;

import com.mrp.importexport.excel.InventoryItemExcelParser;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.TemplateGenerator;
import com.mrp.inventory.domain.InventoryItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryItemExcelParserTest {

    @TempDir
    Path tempDir;

    @Test
    void formulaQuantityPreservesOriginalValueAndRejectsPreview() throws Exception {
        Path file = tempDir.resolve("inventory-formula.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存导入");
            String[] columns = {"仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            sheet.createRow(1).createCell(0).setCellValue("说明");
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("WH1");
            data.createCell(3).setCellValue("M1");
            data.createCell(5).setCellFormula("1+2");
            workbook.write(output);
            Files.write(file, output.toByteArray());
        }
        ParseResult<InventoryItem> parsed = new InventoryItemExcelParser().parse(file);
        assertTrue(parsed.rows().isEmpty());
        assertTrue(parsed.errors().stream().anyMatch(error -> error.rowNumber() == 3
                && "inventory_qty".equals(error.column()) && "INVALID_QTY".equals(error.errorCode())
                && "=1+2".equals(error.originalValue())), parsed.errors().toString());
        assertEquals("=1+2", parsed.rawRows().get(0).get(5));
    }

    @Test
    void emptyWorkbookReportsNoDataOnPreview() throws Exception {
        Path file = tempDir.resolve("inventory-empty.xlsx");
        Files.write(file, new TemplateGenerator().generateInventoryTemplate());
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int row = sheet.getLastRowNum(); row >= 2; row--) {
                Row existing = sheet.getRow(row);
                if (existing != null) sheet.removeRow(existing);
            }
            workbook.write(output);
            Files.write(file, output.toByteArray());
        }
        ParseResult<InventoryItem> parsed = new InventoryItemExcelParser().parse(file);
        assertTrue(parsed.hasErrors());
        assertEquals(0, parsed.totalRows());
        assertTrue(parsed.errors().stream().anyMatch(error -> "NO_DATA".equals(error.errorCode())));
    }

    @Test
    void rejectsQuantityOutsideDecimal18SixAtPreview() throws Exception {
        Path file = tempDir.resolve("inventory-overprecision.xlsx");
        for (String qty : new String[]{"1.1234567", "1000000000000"}) {
            try (XSSFWorkbook workbook = new XSSFWorkbook();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet("库存导入");
                String[] columns = {"仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注"};
                Row header = sheet.createRow(0);
                for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
                sheet.createRow(1).createCell(0).setCellValue("说明");
                Row data = sheet.createRow(2);
                data.createCell(0).setCellValue("WH1");
                data.createCell(3).setCellValue("M1");
                data.createCell(5).setCellValue(qty);
                workbook.write(output);
                Files.write(file, output.toByteArray());
            }
            ParseResult<InventoryItem> parsed = new InventoryItemExcelParser().parse(file);
            assertTrue(parsed.hasErrors(), qty);
            assertTrue(parsed.errors().stream().anyMatch(error -> error.rowNumber() == 3
                    && "INVALID_QTY".equals(error.errorCode()) && qty.equals(error.originalValue())), qty);
        }
    }

    @Test
    void partialRowMissingMaterialReportsErrorInsteadOfSilentlySkipping() throws Exception {
        Path file = tempDir.resolve("missing-material.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存导入");
            String[] columns = {"仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            sheet.createRow(1).createCell(0).setCellValue("说明");
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("WH1");
            data.createCell(5).setCellValue("12");
            workbook.write(output);
            Files.write(file, output.toByteArray());
        }
        ParseResult<InventoryItem> parsed = new InventoryItemExcelParser().parse(file);
        assertTrue(parsed.hasErrors());
        assertTrue(parsed.rows().isEmpty());
        assertTrue(parsed.errors().stream().anyMatch(error -> error.rowNumber() == 3
                && "material_id".equals(error.column()) && "REQUIRED_FIELD".equals(error.errorCode())));
    }

    @Test
    void numericMaterialCellCannotSilentlyLoseLeadingZeros() throws Exception {
        Path file = tempDir.resolve("numeric-material.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存导入");
            String[] columns = {"仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            sheet.createRow(1).createCell(0).setCellValue("说明");
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("WH1");
            data.createCell(3).setCellValue(12345678901234567d);
            data.createCell(5).setCellValue("1.000001");
            workbook.write(output);
            Files.write(file, output.toByteArray());
        }
        ParseResult<InventoryItem> parsed = new InventoryItemExcelParser().parse(file);
        assertTrue(parsed.rows().isEmpty());
        assertEquals("INVALID_MATERIAL_ID", parsed.errors().getFirst().errorCode());
        assertEquals(3, parsed.errors().getFirst().rowNumber());
        assertEquals(parsed.rawRows().getFirst().get(3), parsed.errors().getFirst().originalValue());
    }

    @Test
    void textualMaterialAndSixDecimalQuantityRemainExact() throws Exception {
        Path file = tempDir.resolve("exact-quantity.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存导入");
            String[] columns = {"仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            sheet.createRow(1).createCell(0).setCellValue("说明");
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("WH1");
            data.createCell(3).setCellValue("000123456789012345");
            data.createCell(5).setCellValue("123456789012.123456");
            workbook.write(output);
            Files.write(file, output.toByteArray());
        }
        ParseResult<InventoryItem> parsed = new InventoryItemExcelParser().parse(file);
        assertFalse(parsed.hasErrors(), parsed.errors().toString());
        assertEquals("000123456789012345", parsed.rows().getFirst().materialId());
        assertEquals(new BigDecimal("123456789012.123456"), parsed.rows().getFirst().shippingAvailableQty());
    }

    @Test
    void chineseQualityValuesAreNormalizedToInternalCodes() throws Exception {
        Path template = tempDir.resolve("inventory-template.xlsx");
        Files.write(template, new TemplateGenerator().generateInventoryTemplate());
        Path legacy = tempDir.resolve("inventory-legacy-quality.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存导入");
            String[] columns = {"仓库编码*", "仓库名称", "库存分类", "料号*", "库存品质*", "供应商名称", "库存数量*", "备注"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            sheet.createRow(1).createCell(0).setCellValue("填写说明");
            Row good = sheet.createRow(2);
            String[] first = {"XA378_YONGHUI", "永惠仓", "原材料", "3308AA800180", "良品", "供应商A", "1000", "常规库存"};
            for (int i = 0; i < first.length; i++) good.createCell(i).setCellValue(first[i]);
            Row defective = sheet.createRow(3);
            String[] second = {"XA400_APK", "爱培科仓", "", "6610AA800551", "不良品", "", "25", ""};
            for (int i = 0; i < second.length; i++) defective.createCell(i).setCellValue(second[i]);
            workbook.write(output);
            Files.write(legacy, output.toByteArray());
        }

        ParseResult<InventoryItem> result = new InventoryItemExcelParser().parse(legacy);

        assertFalse(result.hasErrors(), () -> "unexpected parse errors: " + result.errors());
        assertEquals(2, result.rows().size());
        assertEquals("GOOD", result.rows().get(0).inventoryQuality());
        assertEquals("DEFECTIVE", result.rows().get(1).inventoryQuality());
        assertEquals("XA378_YONGHUI", result.rows().get(0).warehouseCode());
        assertEquals(new BigDecimal("1000"), result.rows().get(0).shippingAvailableQty());
        assertEquals("常规库存", result.rows().get(0).remark());
        assertTrue(result.rows().get(1).inventoryCategory().isBlank());
        assertTrue(result.rows().get(1).supplierName().isBlank());
        assertEquals(8, result.headers().size());
    }

    @Test
    void sparseOptionalSupplierCellDoesNotShiftQuantityColumn() throws Exception {
        Path file = tempDir.resolve("sparse-inventory.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存导入模板");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("仓库编码*");
            header.createCell(1).setCellValue("仓库名称");
            header.createCell(2).setCellValue("库存分类");
            header.createCell(3).setCellValue("料号*");
            header.createCell(4).setCellValue("品质*");
            header.createCell(5).setCellValue("供应商名称");
            header.createCell(6).setCellValue("库存数量*");
            header.createCell(7).setCellValue("备注");
            sheet.createRow(1).createCell(0).setCellValue("填写说明");
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("YH");
            data.createCell(2).setCellValue("原材料");
            data.createCell(3).setCellValue("M-SPARSE");
            data.createCell(4).setCellValue("良品");
            // Intentionally omit F3. The parser must still read G3 as quantity.
            data.createCell(6).setCellValue("12.500000");
            workbook.write(output);
            Files.write(file, output.toByteArray());
        }

        ParseResult<InventoryItem> result = new InventoryItemExcelParser().parse(file);

        assertFalse(result.hasErrors(), () -> "unexpected parse errors: " + result.errors());
        assertEquals(1, result.rows().size());
        assertEquals(new BigDecimal("12.500000"), result.rows().get(0).shippingAvailableQty());
        assertTrue(result.rows().get(0).supplierName() == null || result.rows().get(0).supplierName().isBlank());
        assertEquals("原材料", result.rows().get(0).inventoryCategory());
        assertEquals(8, result.rawRows().get(0).size());
        assertEquals("12.500000", result.rawRows().get(0).get(6));
        assertEquals("", result.rawRows().get(0).get(7));
    }
}
