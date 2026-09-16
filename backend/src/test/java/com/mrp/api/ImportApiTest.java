package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ImportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin", "password", "admin123");

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
        byte[] fileContent = createInventoryExcel();

        MockMultipartFile file = new MockMultipartFile(
                "file", "inventory.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileContent
        );

        mockMvc.perform(multipart("/api/v1/inventory-imports/items")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").exists());
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
            dataRow.createCell(0).setCellValue("F001");
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

            String[] headers = {"物料编码", "仓库编码", "仓库名称", "仓库类型", "工厂编码", "数量"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("M001");
            dataRow.createCell(1).setCellValue("WH01");
            dataRow.createCell(2).setCellValue("主仓库");
            dataRow.createCell(3).setCellValue("原材料");
            dataRow.createCell(4).setCellValue("F001");
            dataRow.createCell(5).setCellValue(500);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            wb.write(os);
            return os.toByteArray();
        }
    }
}
