package com.mrp.importexport.excel;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForecastErrorReportTest {
    @Test
    void headerErrorIsVisibleInFinalErrorColumn() throws Exception {
        byte[] report = new TemplateGenerator().generateForecastErrorReport(
                List.of("工厂", "2026-11", "2026-11"),
                List.of(List.of("爱培科", "1", "2")),
                List.of(ParseError.of(1, "month_2", "2026-11", "INVALID_MONTH_SEQUENCE", "月份必须连续且不重复")));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(report))) {
            var sheet = workbook.getSheetAt(0);
            assertTrue(sheet.getRow(0).getCell(3).getStringCellValue().contains("月份必须连续且不重复"));
            assertEquals("爱培科", sheet.getRow(1).getCell(0).getStringCellValue());
        }
    }
}
