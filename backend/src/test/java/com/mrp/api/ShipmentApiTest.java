package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ShipmentApiTest {

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
    void importShipment_shouldReturn202() throws Exception {
        List<Map<String, Object>> details = List.of(
                Map.of("materialId", "M001", "planMonth", "2026-01-01", "shippedQty", 100),
                Map.of("materialId", "M002", "planMonth", "2026-01-01", "shippedQty", 200)
        );

        mockMvc.perform(post("/api/v1/shipment-imports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.batchId").exists())
                .andExpect(jsonPath("$.data.status").value("IMPORTED"))
                .andExpect(jsonPath("$.data.recordCount").value(2));
    }

    @Test
    void getLatestBatch_shouldReturnBatch() throws Exception {
        List<Map<String, Object>> details = List.of(
                Map.of("materialId", "M001", "planMonth", "2026-02-01", "shippedQty", 50)
        );

        mockMvc.perform(post("/api/v1/shipment-imports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/shipments/latest")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.source").value("IMPORT"));
    }

    @Test
    void getDetails_shouldReturnDetailList() throws Exception {
        List<Map<String, Object>> details = List.of(
                Map.of("materialId", "M003", "planMonth", "2026-03-01", "shippedQty", 300)
        );

        MvcResult importResult = mockMvc.perform(post("/api/v1/shipment-imports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isAccepted())
                .andReturn();

        String responseBody = importResult.getResponse().getContentAsString();
        long batchId = objectMapper.readTree(responseBody).path("data").path("batchId").asLong();

        mockMvc.perform(get("/api/v1/shipments/" + batchId + "/details")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].materialId").value("M003"))
                .andExpect(jsonPath("$.data[0].shippedQty").value(300));
    }

    @Test
    void getShippedQty_shouldReturnQuantity() throws Exception {
        List<Map<String, Object>> details = List.of(
                Map.of("materialId", "M004", "planMonth", "2026-04-01", "shippedQty", 150),
                Map.of("materialId", "M005", "planMonth", "2026-04-01", "shippedQty", 250)
        );

        mockMvc.perform(post("/api/v1/shipment-imports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/shipments/shipped-qty")
                        .header("Authorization", "Bearer " + token)
                        .param("materialId", "M004")
                        .param("planMonth", "2026-04-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materialId").value("M004"))
                .andExpect(jsonPath("$.data.shippedQty").value(150));
    }

    @Test
    void getShippedQty_noBatch_shouldReturnZero() throws Exception {
        mockMvc.perform(get("/api/v1/shipments/shipped-qty")
                        .header("Authorization", "Bearer " + token)
                        .param("materialId", "NONEXISTENT")
                        .param("planMonth", "2099-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippedQty").value(0));
    }
}
