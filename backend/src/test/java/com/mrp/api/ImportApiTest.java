package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import com.mrp.BaseIntegrationTest;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
class ImportApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin", "password", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        token = objectMapper.readTree(responseBody).path("data").path("token").asText();
    }

    @Test
    void uploadForecast_shouldReturn202() throws Exception {
        byte[] fileContent = createForecastExcel();

        MockMultipartFile file = new MockMultipartFile(
                "file", "forecast.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileContent
        );

        mockMvc.perform(multipart("/api/v1/forecast-imports")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").exists());
    }

    @Test
    void uploadInventory_shouldReturn202() throws Exception {
        jdbc.update("INSERT IGNORE INTO material(material_code,material_name,is_active,is_deleted) VALUES('M001','API测试物料',1,0)");
        jdbc.update("INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code,warehouse_name,is_good_warehouse,warehouse_attribute,quality_configured,enabled) VALUES('永惠','WH01','测试仓',1,'自有仓',1,1)");
        try {
        byte[] fileContent = createInventoryExcel();

        MockMultipartFile file = new MockMultipartFile(
                "file", "inventory.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileContent
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/inventory-imports/items")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").exists())
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.previewRows[0].warehouseCode").value("WH01"))
                .andExpect(jsonPath("$.data.previewRows[0].remark").value("API备注"))
                .andExpect(jsonPath("$.data.previewRows[0].inventoryCategory").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.previewRows[0].supplierName").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        long taskId = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
                .path("data").path("taskId").asLong();
        MvcResult confirmResult = mockMvc.perform(post("/api/v1/inventory-imports/items/" + taskId + "/confirm")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        long snapshotId = objectMapper.readTree(confirmResult.getResponse().getContentAsString())
                .path("data").path("snapshotId").asLong();

        mockMvc.perform(get("/api/v1/inventory-snapshots/" + snapshotId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].remark").value("API备注"))
                .andExpect(jsonPath("$.data.items[0].inventoryCategory").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.items[0].supplierName").value(org.hamcrest.Matchers.nullValue()));
        } finally {
            jdbc.update("DELETE FROM factory_warehouse_mapping WHERE warehouse_code='WH01'");
        }
    }

    @Test
    void uploadInvalidFile_shouldReturn202WithErrors() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt",
                "text/plain",
                "invalid content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/forecast-imports")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.errorRows").value(1))
                .andExpect(jsonPath("$.data.errors").isArray())
                .andExpect(jsonPath("$.data.errors[0].errorCode").value("PARSE_ERROR"));
    }

    private byte[] createForecastExcel() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("经营计划导入");

            String[] headers = {"业务线/工厂", "形态", "料号", "模具", "状态", "2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Row tipsRow = sheet.createRow(1);
            for (int i = 5; i < 11; i++) {
                tipsRow.createCell(i).setCellValue("fcst");
            }

            Row dataRow = sheet.createRow(2);
            dataRow.createCell(0).setCellValue("永惠");
            dataRow.createCell(1).setCellValue("A");
            dataRow.createCell(2).setCellValue("M001");
            dataRow.createCell(3).setCellValue("MOLD001");
            dataRow.createCell(4).setCellValue("正常");
            for (int i = 5; i < 11; i++) {
                dataRow.createCell(i).setCellValue(100);
            }

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            wb.write(os);
            return os.toByteArray();
        }
    }

    private byte[] createInventoryExcel() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("库存快照导入");

            String[] headers = {"仓库编码*", "仓库名称", "库存分类", "料号*", "供应商名称", "库存数量*", "备注"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Row tipsRow = sheet.createRow(1);
            tipsRow.createCell(0).setCellValue("必填");
            tipsRow.createCell(1).setCellValue("可选");
            tipsRow.createCell(2).setCellValue("非必填");
            tipsRow.createCell(3).setCellValue("必填");
            tipsRow.createCell(4).setCellValue("非必填");
            tipsRow.createCell(5).setCellValue("必填");

            Row dataRow = sheet.createRow(2);
            dataRow.createCell(0).setCellValue("WH01");
            dataRow.createCell(1).setCellValue("测试仓");
            dataRow.createCell(3).setCellValue("M001");
            dataRow.createCell(5).setCellValue(500);
            dataRow.createCell(6).setCellValue("API备注");

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            wb.write(os);
            return os.toByteArray();
        }
    }
}
