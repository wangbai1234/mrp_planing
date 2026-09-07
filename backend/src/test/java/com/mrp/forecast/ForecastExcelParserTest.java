package com.mrp.forecast;

import com.mrp.MrpApiApplication;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.importexport.excel.ForecastExcelParser;
import com.mrp.importexport.excel.ParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MrpApiApplication.class)
@ActiveProfiles("test")
class ForecastExcelParserTest {

    @Autowired
    private ForecastExcelParser parser;

    @Test
    void parseTemplate_shouldNotCrash() throws Exception {
        Path template = new ClassPathResource("test-data/MRP经营计划导入模板.xlsx").getFile().toPath();

        ParseResult<ForecastDetail> result = parser.parse(template, 999L);

        assertNotNull(result);
        // Template may or may not have data rows depending on format
        // The key is that parsing doesn't throw
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
}
