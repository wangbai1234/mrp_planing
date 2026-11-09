package com.mrp.forecast;

import com.mrp.BaseIntegrationTest;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.importexport.excel.ForecastExcelParser;
import com.mrp.importexport.excel.ParseError;
import com.mrp.importexport.excel.ParseResult;
import com.mrp.importexport.excel.TestExcelFileBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForecastExcelParserTest extends BaseIntegrationTest {

    @Autowired
    private ForecastExcelParser parser;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @TempDir
    Path tempDir;

    @Test
    void parse_uncachedFormulaQuantity_shouldRejectBeforeConfirmation() throws Exception {
        jdbc.update("INSERT IGNORE INTO material(material_code,material_name,is_active,is_deleted) VALUES('TEST_FCST_FORMULA_20261102','Test',1,0)");
        Path file = TestExcelFileBuilder.createForecastFile(tempDir, "formula-quantity.xlsx",
                new String[][]{{"爱培科", "整机", "TEST_FCST_FORMULA_20261102", "", "", "1", "0", "0", "0", "0", "0"}},
                "2026-11", "2026-12", "2027-01", "2027-02", "2027-03", "2027-04");
        try (XSSFWorkbook book = new XSSFWorkbook(Files.newInputStream(file))) {
            book.getSheetAt(0).getRow(2).getCell(5).setCellFormula("1+2");
            try (var out = Files.newOutputStream(file)) {
                book.write(out);
            }
        }
        ParseResult<ForecastDetail> result = parser.parse(file, 999L);
        assertTrue(result.errors().stream().anyMatch(e ->
                e.rowNumber() == 3 && "INVALID_QTY".equals(e.errorCode())
                        && "fcst_2026-11".equals(e.column())), result.errors().toString());
    }

    @Test
    void parse_headerOnly_shouldReportNoDataDuringPreview() throws Exception {
        Path file = TestExcelFileBuilder.createForecastFile(tempDir, "header-only.xlsx", new String[0][],
                "2026-11", "2026-12", "2027-01", "2027-02", "2027-03", "2027-04");
        ParseResult<ForecastDetail> result = parser.parse(file, 999L);
        assertTrue(result.hasErrors());
        assertEquals(0, result.totalRows());
        assertTrue(result.errors().stream().anyMatch(e -> "NO_DATA".equals(e.errorCode())));
    }

    @Test
    void parse_missingMaterialAndInvalidMachineQuantities_shouldRejectSourceRows() throws Exception {
        jdbc.update("INSERT IGNORE INTO material(material_code,material_name,is_active,is_deleted) VALUES('TEST_FCST_NEG_20261102','Test',1,0)");
        jdbc.update("INSERT IGNORE INTO material(material_code,material_name,is_active,is_deleted) VALUES('TEST_FCST_SCALE_20261102','Test',1,0)");
        String[][] data = {
                {"爱培科", "整机", null, "", "", "1", "0", "0", "0", "0", "0"},
                {"爱培科", "整机", "TEST_FCST_NEG_20261102", "", "", "-7", "0", "0", "0", "0", "0"},
                {"爱培科", "整机", "TEST_FCST_SCALE_20261102", "", "", "1.1234567", "0", "0", "0", "0", "0"}
        };
        Path file = TestExcelFileBuilder.createForecastFile(tempDir, "invalid-forecast.xlsx", data,
                "2026-11", "2026-12", "2027-01", "2027-02", "2027-03", "2027-04");
        ParseResult<ForecastDetail> result = parser.parse(file, 999L);
        assertEquals(3, result.totalRows());
        assertEquals(3, result.errorRows());
        assertTrue(result.errors().stream().anyMatch(e -> e.rowNumber() == 3 && "REQUIRED_FIELD".equals(e.errorCode())));
        assertTrue(result.errors().stream().anyMatch(e -> e.rowNumber() == 4 && "INVALID_QTY".equals(e.errorCode())));
        assertTrue(result.errors().stream().anyMatch(e -> e.rowNumber() == 5 && "INVALID_QTY".equals(e.errorCode())));
    }

    @Test
    void parse_fractionalMachineQuantity_shouldFailBeforeFormalVersion() throws Exception {
        jdbc.update("INSERT IGNORE INTO material(material_code,material_name,is_active,is_deleted) VALUES('TEST_FCST_FRACTION_20261103','Test',1,0)");
        Path file = TestExcelFileBuilder.createForecastFile(tempDir, "fractional-machine.xlsx",
                new String[][]{{"爱培科", "整机", "TEST_FCST_FRACTION_20261103", "", "", "1.123456", "0", "0", "0", "0", "0"}},
                "2026-11", "2026-12", "2027-01", "2027-02", "2027-03", "2027-04");
        ParseResult<ForecastDetail> result = parser.parse(file, 999L);
        assertEquals(1, result.totalRows());
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(error -> error.rowNumber() == 3
                && "fcst_2026-11".equals(error.column())
                && "1.123456".equals(error.originalValue())
                && "INVALID_QTY".equals(error.errorCode())), result.errors().toString());
    }

    @Test
    void parse_duplicateMonthHeader_shouldRejectBeforeConfirmation() throws Exception {
        Path file = TestExcelFileBuilder.createForecastFile(tempDir, "duplicate-month.xlsx",
                new String[][]{{"爱培科", "整机", "TEST_FCST_NEG_20261102", "", "", "1", "2", "3", "4", "5", "6"}},
                "2026-11", "2026-11", "2027-01", "2027-02", "2027-03", "2027-04");
        ParseResult<ForecastDetail> result = parser.parse(file, 999L);
        assertTrue(result.errors().stream().anyMatch(e -> "INVALID_MONTH_SEQUENCE".equals(e.errorCode())));
        assertTrue(result.hasErrors());
    }

    @Test
    void parseTemplate_shouldNotCrash() throws Exception {
        Path template = new ClassPathResource("test-data/MRP经营计划导入模板.xlsx").getFile().toPath();

        ParseResult<ForecastDetail> result = parser.parse(template, 999L);

        assertNotNull(result);
        System.out.println("=== Parse Result ===");
        System.out.println("Total rows: " + result.totalRows());
        System.out.println("Success rows: " + result.successRows());
        System.out.println("Error rows: " + result.errorRows());
        System.out.println("Recognized months: " + result.recognizedMonths());
        if (result.hasErrors()) {
            result.errors().forEach(e -> System.out.println("Error: " + e));
        }
    }

    @Test
    void parseTemplate_materialIdNotDate() throws Exception {
        Path template = new ClassPathResource("test-data/MRP经营计划导入模板.xlsx").getFile().toPath();

        ParseResult<ForecastDetail> result = parser.parse(template, 999L);

        for (ForecastDetail detail : result.rows()) {
            assertNotNull(detail.materialId(), "material_id should not be null");
            assertFalse(detail.materialId().matches("\\d{4}-\\d{2}-\\d{2}.*"),
                    "material_id should not be date format: " + detail.materialId());
        }
    }

    @Test
    void parseTemplate_recognizedMonthsAreValid() throws Exception {
        Path template = new ClassPathResource("test-data/MRP经营计划导入模板.xlsx").getFile().toPath();

        ParseResult<ForecastDetail> result = parser.parse(template, 999L);

        // Recognized months should be valid strings like "2026-08"
        assertNotNull(result.recognizedMonths());
        for (String ym : result.recognizedMonths()) {
            assertNotNull(ym, "Recognized month should not be null");
            assertTrue(ym.matches("\\d{4}-\\d{2}"), "Month should match YYYY-MM format: " + ym);
        }
    }

    @Test
    void parseTemplate_headersNotNull() throws Exception {
        Path template = new ClassPathResource("test-data/MRP经营计划导入模板.xlsx").getFile().toPath();

        ParseResult<ForecastDetail> result = parser.parse(template, 999L);

        assertNotNull(result.headers(), "Headers should not be null");
    }

    @Test
    void parseTemplate_rawRowsNotNull() throws Exception {
        Path template = new ClassPathResource("test-data/MRP经营计划导入模板.xlsx").getFile().toPath();

        ParseResult<ForecastDetail> result = parser.parse(template, 999L);

        assertNotNull(result.rawRows(), "Raw rows should not be null");
    }

    @Test
    void parseTemplate_materialIdIsText() throws Exception {
        Path template = new ClassPathResource("test-data/MRP经营计划导入模板.xlsx").getFile().toPath();

        ParseResult<ForecastDetail> result = parser.parse(template, 999L);

        for (ForecastDetail detail : result.rows()) {
            // Material ID should not be scientific notation
            assertFalse(detail.materialId().matches("\\d\\.\\d+E\\d+"),
                    "material_id should not be scientific notation: " + detail.materialId());
            // Material ID should not start with0 followed by digits (unless it's a real leading zero)
            if (detail.materialId().startsWith("0")) {
                assertTrue(detail.materialId().length() > 1, "Leading zero material ID should have more digits");
            }
        }
    }

    @Test
    void parse_crossYearMonths() throws Exception {
        // Col 0-4: factory, formType, materialId, mold, status
        // Col 5-10: 6 month fcst quantities
        String[][] data = {
            {"永惠", "单机", "6830AA800561", "A01", "国内",
             "100", "200", "300", "400", "500", "600"}
        };
        Path file = TestExcelFileBuilder.createForecastFile(
                tempDir, "forecast-cross-year.xlsx", data,
                "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03");

        ParseResult<ForecastDetail> result = parser.parse(file, 999L);

        assertNotNull(result);
        List<String> months = result.recognizedMonths();
        assertEquals(6, months.size(), "Should recognize 6 months");
        assertEquals("2026-10", months.get(0));
        assertEquals("2026-11", months.get(1));
        assertEquals("2026-12", months.get(2));
        assertEquals("2027-01", months.get(3));
        assertEquals("2027-02", months.get(4));
        assertEquals("2027-03", months.get(5));
        assertEquals(1, result.totalRows(), "Total rows must count source Excel rows");
        assertEquals(result.totalRows() - result.errorRows(), result.successRows(),
                "Success rows must be source rows, not generated month details");
    }

    @Test
    void parse_generalDateFormat() throws Exception {
        // Month header stored as Excel date serial number text (46388 = 2026-12-01)
        // Parser's parseMonth can handle numeric strings
        String[][] data = {
            {"永惠", "单机", "6830AA800561", "A01", "国内",
             "100", "200", "300", "400", "500", "600"}
        };
        Path file = TestExcelFileBuilder.createForecastFile(
                tempDir, "forecast-general-date.xlsx", data,
                "2026-12");

        ParseResult<ForecastDetail> result = parser.parse(file, 999L);

        assertNotNull(result);
        List<String> months = result.recognizedMonths();
        assertFalse(months.isEmpty(), "Should recognize at least one month");
        assertEquals("2026-12", months.get(0), "Should recognize 2026-12");
    }

    @Test
    void parse_materialNumberAsText() throws Exception {
        String[][] data = {
            {"永惠", "单机", "6830AA800561", "A01", "国内",
             "100", "200", "300", "400", "500", "600"}
        };
        Path file = TestExcelFileBuilder.createForecastFile(
                tempDir, "forecast-text-material.xlsx", data,
                "2026-08", "2026-09", "2026-10", "2026-11", "2026-12", "2027-01");

        ParseResult<ForecastDetail> result = parser.parse(file, 999L);

        assertNotNull(result);
        if (result.rows().isEmpty()) {
            // Material not in DB - verify no INVALID_MATERIAL_ID error (text format preserved)
            boolean hasInvalidMaterialId = result.errors().stream()
                    .anyMatch(e -> "INVALID_MATERIAL_ID".equals(e.errorCode()));
            assertFalse(hasInvalidMaterialId, "Material ID should be preserved as text, not converted to date/scientific");
        } else {
            // Material found in DB - verify material ID is correct text format
            assertTrue(result.rows().get(0).materialId().startsWith("6830"),
                    "Material ID should start with 6830: " + result.rows().get(0).materialId());
        }
    }
}
