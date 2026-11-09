package com.mrp.importexport.excel;

import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.StockPolicy;
import com.mrp.mrp.repository.ConfigMasterMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockPolicyExcelParserTest {

    @Mock private ConfigMasterMapper mappings;
    @Mock private MaterialMapper materials;

    @Test
    void rejectsWarehouseOutsideMappingAndUnknownMaterial() throws IOException {
        FactoryWarehouseMapping mapping = new FactoryWarehouseMapping();
        mapping.setWarehouseCode("YH");
        mapping.setEnabled(true);
        when(mappings.selectFactoryWarehouseMappings()).thenReturn(List.of(mapping));
        when(materials.selectByCodes(any())).thenReturn(List.of(material("M001")));

        ParseResult<StockPolicy> result = new StockPolicyExcelParser(mappings, materials).parse(workbook(
                new String[]{"OTHER", "M001", "10", "40"},
                new String[]{"YH", "MISSING", "10", "40"},
                new String[]{"YH", "M001", "10", "40"}));

        assertTrue(result.rows().isEmpty(), "任一错误行都要让整份不导入");
        assertEquals(List.of("仓库不在工厂仓库映射中或未启用", "物料料号不存在"),
                result.errors().stream().map(ParseError::message).toList());
    }

    @Test
    void acceptsMappedWarehouseAndKnownMaterial() throws IOException {
        FactoryWarehouseMapping mapping = new FactoryWarehouseMapping();
        mapping.setWarehouseCode("YH");
        mapping.setEnabled(true);
        when(mappings.selectFactoryWarehouseMappings()).thenReturn(List.of(mapping));
        when(materials.selectByCodes(List.of("M001"))).thenReturn(List.of(material("M001")));

        ParseResult<StockPolicy> result = new StockPolicyExcelParser(mappings, materials).parse(workbook(
                new String[]{"示例-不导入", "M000", "1", "1"},
                new String[]{"YH", "M001", "10", "40"}));

        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.rows().size());
        assertEquals("YH", result.rows().get(0).getWarehouseCode());
        assertEquals("M001", result.rows().get(0).getMaterialCode());
    }

    private static ByteArrayInputStream workbook(String[]... dataRows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("仓库编码");
            var instruction = sheet.createRow(1);
            instruction.createCell(0).setCellValue("填写仓库编码");
            for (int index = 0; index < dataRows.length; index++) {
                var row = sheet.createRow(index + 2);
                for (int column = 0; column < dataRows[index].length; column++) {
                    row.createCell(column).setCellValue(dataRows[index][column]);
                }
            }
            workbook.write(output);
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private static Material material(String code) {
        return new Material(1L, code, code, null, null, null, null, null, null, null, null, true,
                null, null, null, null, false, null, null, null);
    }
}
