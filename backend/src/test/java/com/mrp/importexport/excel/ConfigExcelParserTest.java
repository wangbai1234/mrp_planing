package com.mrp.importexport.excel;

import com.mrp.mrp.domain.BusinessWeek;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigExcelParserTest {

    private final ConfigExcelParser parser = new ConfigExcelParser();

    @Test void mappingRejectsUnknownFactoryInCorrectTemplateAndPreservesErrorSource() throws Exception {
        var parsed = parser.parseFactoryWarehouse(new ByteArrayInputStream(calendarWorkbook(
                java.util.List.of("工厂编码", "仓库编码", "仓库名称", "启用"),
                java.util.List.of("UNKNOWN", "WH01", "测试仓", "启用"))));
        assertTrue(parsed.hasErrors()); assertTrue(parsed.rows().isEmpty());
        assertEquals("UNKNOWN", parsed.rawRows().getFirst().getFirst());
        assertTrue(parsed.errors().stream().anyMatch(e -> e.rowNumber() == 3 && "ENUM".equals(e.errorCode())));
    }

    @Test void mappingRejectsAnotherTemplatesHeadersAndInvalidEnabledValue() throws Exception {
        var wrong = parser.parseFactoryWarehouse(new ByteArrayInputStream(calendarWorkbook(
                java.util.List.of("工厂编码", "日期", "生产日", "日产能", "单位", "备注"),
                java.util.List.of("爱培科", "2026-10-14", "是", "1000", "台"))));
        assertTrue(wrong.hasErrors()); assertTrue(wrong.rows().isEmpty());
        assertEquals("日期", wrong.headers().get(1));
        assertTrue(wrong.errors().stream().anyMatch(e -> "HEADER_MISMATCH".equals(e.errorCode())));
        var invalid = parser.parseFactoryWarehouse(new ByteArrayInputStream(calendarWorkbook(
                java.util.List.of("工厂编码", "仓库编码", "仓库名称", "仓库管理员", "对应供应商", "是否良品仓", "仓库属性", "优先级", "启用"),
                java.util.List.of("爱培科", "WH01", "测试仓", "", "", "是", "自有仓", "1", "unknown"))));
        assertTrue(invalid.hasErrors()); assertTrue(invalid.rows().isEmpty());
        var valid = parser.parseFactoryWarehouse(new ByteArrayInputStream(calendarWorkbook(
                java.util.List.of("工厂编码", "仓库编码", "仓库名称", "仓库管理员", "对应供应商", "是否良品仓", "仓库属性", "优先级", "启用"),
                java.util.List.of("爱培科", "WH02", "测试仓", "", "", "是", "自有仓", "1", "启用"))));
        assertFalse(valid.hasErrors()); assertEquals(1, valid.rows().size());
    }

    @Test void calendarExportRoundTripsUnitsCapacityAndRemarksWithoutWeight() throws Exception {
        var day = new com.mrp.mrp.domain.ProductionCalendarEntry();
        day.setFactoryCode("爱培科"); day.setCalendarDate(LocalDate.of(2026,10,14)); day.setProductionDay(true);
        day.setDailyCapacity(new java.math.BigDecimal("1234")); day.setCapacityUnit("台"); day.setReason("加班");
        var rest = new com.mrp.mrp.domain.ProductionCalendarEntry(); rest.setFactoryCode("永惠"); rest.setCalendarDate(LocalDate.of(2026,10,14));
        rest.setProductionDay(false); rest.setDailyCapacity(java.math.BigDecimal.ZERO);
        byte[] data = new TemplateGenerator().generateProductionCalendarExport(java.util.List.of(day,rest));
        var parsed = parser.parseProductionCalendar(new ByteArrayInputStream(data));
        assertTrue(parsed.errors().isEmpty()); assertEquals(2,parsed.rows().size());
        assertEquals(day.getDailyCapacity(),parsed.rows().getFirst().getDailyCapacity());
        assertEquals("台",parsed.rows().getFirst().getCapacityUnit()); assertEquals("加班",parsed.rows().getFirst().getReason());
        assertEquals(false,parsed.rows().getLast().getProductionDay());
        assertEquals("台",parsed.rows().getLast().getCapacityUnit());
        assertEquals(6,parsed.headers().size()); assertTrue(parsed.headers().stream().noneMatch(h -> h.contains("权重")));
    }

    @Test void calendarImportRejectsOldWeightTemplateAndPreservesOriginalHeaders() throws Exception {
        byte[] data = calendarWorkbook(java.util.List.of("工厂编码", "日期", "生产日", "分配权重", "日产能"),
                java.util.List.of("爱培科", "2026-10-14", "是", "1", "1000"));
        var parsed = parser.parseProductionCalendar(new ByteArrayInputStream(data));
        assertTrue(parsed.hasErrors()); assertTrue(parsed.rows().isEmpty());
        assertEquals("分配权重",parsed.headers().get(3));
        assertTrue(parsed.errors().stream().anyMatch(e -> "HEADER_MISMATCH".equals(e.errorCode())));
    }

    @Test void calendarImportRejectsMissingUnitsAndSparseRowReportsErrorInLastColumn() throws Exception {
        byte[] data = calendarWorkbook(java.util.List.of("工厂编码", "日期", "生产日", "日产能", "单位", "备注"),
                java.util.List.of("爱培科", "2026-10-14", "是", "1.5"));
        var parsed = parser.parseProductionCalendar(new ByteArrayInputStream(data));
        assertTrue(parsed.hasErrors()); assertEquals(6,parsed.rawRows().getFirst().size());
        assertEquals(3,parsed.errors().getFirst().rowNumber());
        byte[] report = new TemplateGenerator().generateConfigErrorReport(parsed.headers(),parsed.rawRows(),parsed.errors());
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(report))) {
            assertEquals("错误原因",book.getSheetAt(0).getRow(0).getCell(6).getStringCellValue());
            assertTrue(book.getSheetAt(0).getRow(1).getCell(6).getStringCellValue().contains("单位"));
        }
    }

    @Test void calendarImportRejectsFractionalMachineCapacityAndLegacyUnits() throws Exception {
        for (var values : java.util.List.of(java.util.List.of("爱培科", "2026-10-14", "是", "1000.5", "台"),
                java.util.List.of("爱培科", "2026-10-14", "是", "1000", "件"))) {
            var parsed = parser.parseProductionCalendar(new ByteArrayInputStream(calendarWorkbook(
                    java.util.List.of("工厂编码", "日期", "生产日", "日产能", "单位", "备注"), values)));
            assertTrue(parsed.hasErrors()); assertTrue(parsed.rows().isEmpty());
            assertTrue(parsed.errors().stream().anyMatch(error -> error.message().contains("整机")));
        }
    }

    private byte[] calendarWorkbook(java.util.List<String> headers, java.util.List<String> values) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(); var header = sheet.createRow(0); sheet.createRow(1);
            for (int i=0;i<headers.size();i++) header.createCell(i).setCellValue(headers.get(i));
            var row = sheet.createRow(2); for (int i=0;i<values.size();i++) row.createCell(i).setCellValue(values.get(i));
            book.write(output); return output.toByteArray();
        }
    }

    @Test
    void readsNumericDateCellsInsteadOfTheirDisplayFormat() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("业务月份");
            sheet.createRow(1).createCell(0).setCellValue("说明");
            CreationHelper helper = book.getCreationHelper();
            CellStyle monthStyle = book.createCellStyle();
            monthStyle.setDataFormat(helper.createDataFormat().getFormat("mmm-yy"));
            CellStyle dateStyle = book.createCellStyle();
            dateStyle.setDataFormat((short) 14);
            var row = sheet.createRow(2);
            var month = row.createCell(0);
            month.setCellValue(LocalDate.of(2026, 9, 1));
            month.setCellStyle(monthStyle);
            row.createCell(1).setCellValue("W1");
            var start = row.createCell(2);
            start.setCellValue(LocalDate.of(2026, 9, 1));
            start.setCellStyle(dateStyle);
            var end = row.createCell(3);
            end.setCellValue(LocalDate.of(2026, 9, 30));
            end.setCellStyle(dateStyle);
            book.write(output);
            workbook = output.toByteArray();
        }

        ParseResult<BusinessWeek> result = parser.parseBusinessWeeks(new ByteArrayInputStream(workbook));

        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.rows().size());
        BusinessWeek week = result.rows().get(0);
        assertEquals(LocalDate.of(2026, 9, 1), week.getBusinessMonth());
        assertEquals("W1", week.getWeekCode());
        assertEquals(LocalDate.of(2026, 9, 1), week.getStartDate());
        assertEquals(LocalDate.of(2026, 9, 30), week.getEndDate());
    }

    @Test
    void reportsShortMonthTextAsARowErrorInsteadOfCrashing() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            sheet.createRow(0);
            sheet.createRow(1);
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("9月-27");
            row.createCell(1).setCellValue("W1");
            row.createCell(2).setCellValue("2026-09-01");
            row.createCell(3).setCellValue("2026-09-30");
            book.write(output);
            workbook = output.toByteArray();
        }

        ParseResult<BusinessWeek> result = parser.parseBusinessWeeks(new ByteArrayInputStream(workbook));

        assertTrue(result.rows().isEmpty());
        assertEquals("业务月份格式应为 yyyy-MM", result.errors().get(0).message());
    }
    @Test
    void materialPolicyRejectsUnmaintainedAndKeepsSparseExcelLineNumbers() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("物料料号");
            sheet.createRow(1).createCell(0).setCellValue("说明");
            sheet.createRow(2).createCell(0).setCellValue("示例M");
            var row = sheet.createRow(4);
            row.createCell(0).setCellValue("M1");
            row.createCell(1).setCellValue("未维护");
            row.createCell(2).setCellValue("UNMAINTAINED");
            book.write(output); workbook = output.toByteArray();
        }
        var result = parser.parseMaterialPolicy(new ByteArrayInputStream(workbook));
        assertTrue(result.rows().isEmpty());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().allMatch(error -> error.rowNumber() == 5 && "ENUM".equals(error.errorCode())));
        assertEquals(1, result.totalRows());
        assertEquals(1, result.errorRows());
        assertEquals(3, result.rawRows().size());
        assertEquals(java.util.List.of("", "", ""), result.rawRows().get(1));
        assertEquals("M1", result.rawRows().get(2).getFirst());
        byte[] report = new TemplateGenerator().generateConfigErrorReport(result.headers(), result.rawRows(), result.errors());
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(report))) {
            var sheet = book.getSheetAt(0);
            assertEquals("错误原因", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals("M1", sheet.getRow(3).getCell(0).getStringCellValue());
            assertTrue(sheet.getRow(3).getCell(3).getStringCellValue().contains("供应方式"));
            assertTrue(sheet.getRow(3).getCell(3).getStringCellValue().contains("下钻策略"));
        }
    }

}
