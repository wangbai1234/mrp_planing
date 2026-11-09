package com.mrp.forecast;

import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.forecast.service.ForecastImportService;
import com.mrp.importexport.excel.ForecastExcelParser;
import com.mrp.importexport.excel.TestExcelFileBuilder;
import com.mrp.importexport.excel.TemplateGenerator;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ForecastFactoryRegressionTest {
    @TempDir Path dir;

    @Test void templatesUseSchedulableFactoryNames() throws Exception {
        var templates = new TemplateGenerator();
        try (var forecast = new XSSFWorkbook(new ByteArrayInputStream(templates.generateForecastTemplate()));
             var mapping = new XSSFWorkbook(new ByteArrayInputStream(templates.generateFactoryWarehouseTemplate()))) {
            var sheet = forecast.getSheetAt(0);
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("已维护且启用"));
            assertEquals("永惠", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("爱培科", sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("整机", sheet.getRow(3).getCell(1).getStringCellValue());
            assertTrue(mapping.getSheetAt(0).getRow(1).getCell(0).getStringCellValue().contains("已维护"));
            assertEquals("示例：永惠", mapping.getSheetAt(0).getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test void rejectsUnknownForecastFactoryBeforeConfirmation() throws Exception {
        MaterialMapper master = mock(MaterialMapper.class);
        Material material = mock(Material.class);
        when(master.selectByCode("A")).thenReturn(material);
        var parser = new ForecastExcelParser(master);
        var result = parser.parse(TestExcelFileBuilder.createForecastFile(dir, "unknown-factory.xlsx",
                new String[][]{{"YONGHUI", "单机", "A", "", "", "100"}},
                "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03"), null);
        assertTrue(result.hasErrors());
        assertEquals("INVALID_FACTORY", result.errors().getFirst().errorCode());
        assertEquals("YONGHUI", result.rawRows().getFirst().getFirst());
        assertEquals(3, result.errors().getFirst().rowNumber());
    }

    @Test void rejectsNumericMaterialCellBeforeMasterDataLookup() throws Exception {
        MaterialMapper master = mock(MaterialMapper.class);
        var parser = new ForecastExcelParser(master);
        Path file = TestExcelFileBuilder.createForecastFile(dir, "numeric-material.xlsx",
                new String[][]{{"爱培科", "整机", "000123456789012345", "", "", "1"}},
                "2026-11", "2026-12", "2027-01", "2027-02", "2027-03", "2027-04");
        try (var book = new XSSFWorkbook(Files.newInputStream(file))) {
            var cell = book.getSheetAt(0).getRow(2).getCell(2);
            cell.setCellValue(12345678901234567d);
            try (var out = Files.newOutputStream(file)) { book.write(out); }
        }
        var parsed = parser.parse(file, 9L);
        assertTrue(parsed.hasErrors());
        assertEquals("INVALID_MATERIAL_ID", parsed.errors().getFirst().errorCode());
        assertEquals(3, parsed.errors().getFirst().rowNumber());
        assertEquals(parsed.rawRows().getFirst().get(2), parsed.errors().getFirst().originalValue());
        verify(master, never()).selectByCode(anyString());
    }

    @Test void allowsSameMachineAcrossFactoriesButRejectsSameFactoryDuplicate() throws Exception {
        MaterialMapper master = mock(MaterialMapper.class);
        Material material = mock(Material.class);
        when(material.materialName()).thenReturn("Test machine");
        when(master.selectByCode("A")).thenReturn(material);
        var parser = new ForecastExcelParser(master);
        var crossFactory = parser.parse(TestExcelFileBuilder.createForecastFile(dir, "dual.xlsx", new String[][]{
                {"爱培科", "单机", "A", "", "", "100"},
                {"永惠", "单机", "A", "", "", "200"}
        }, "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03"), null);
        assertFalse(crossFactory.hasErrors());
        assertEquals(12, crossFactory.rows().size());
        var duplicate = parser.parse(TestExcelFileBuilder.createForecastFile(dir, "duplicate.xlsx", new String[][]{
                {"爱培科", "单机", "A", "", "", "100"},
                {"爱培科", "单机", "A", "", "", "200"}
        }, "2026-10", "2026-11", "2026-12", "2027-01", "2027-02", "2027-03"), null);
        assertTrue(duplicate.hasErrors());
        assertEquals("DUPLICATE_ROW", duplicate.errors().getFirst().errorCode());
        assertEquals(4, duplicate.errors().getFirst().rowNumber());
        assertEquals(2, duplicate.rawRows().size());
        assertEquals(6, duplicate.rows().size());
    }

    @Test void versionRowsReadEachFactorysOwnMonthAndTotal() {
        ForecastMapper mapper = mock(ForecastMapper.class);
        when(mapper.selectDistinctMaterials(1L, null, 0, 20)).thenReturn(List.of(
                Map.of("material_id", "A", "factory_code", "爱培科"),
                Map.of("material_id", "A", "factory_code", "永惠")));
        when(mapper.countDistinctMaterials(1L, null)).thenReturn(2L);
        when(mapper.selectDetailsByVersionIdAndMaterial(1L, "A", "爱培科")).thenReturn(List.of(detail("爱培科", "100")));
        when(mapper.selectDetailsByVersionIdAndMaterial(1L, "A", "永惠")).thenReturn(List.of(detail("永惠", "200")));
        var service = new ForecastImportService(null, null, mapper, null);
        var result = service.getVersionMaterialsPage(1L, null, 1, 20);
        assertEquals(2, result.total());
        assertEquals(new BigDecimal("100"), result.items().getFirst().get("total"));
        assertEquals(new BigDecimal("200"), result.items().getLast().get("total"));
        assertEquals(Map.of("2026-10-01", new BigDecimal("100")), result.items().getFirst().get("months"));
        assertEquals(Map.of("2026-10-01", new BigDecimal("200")), result.items().getLast().get("months"));
    }

    private ForecastDetail detail(String factory, String quantity) {
        return new ForecastDetail(null, 1L, factory, "A", "Test", null, "单机", null, null, null, null,
                LocalDate.of(2026, 10, 1), new BigDecimal(quantity));
    }
}
