package com.mrp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.MrpApiApplication;
import com.mrp.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(classes = MrpApiApplication.class)
@AutoConfigureMockMvc
class MaterialApiTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> loginRequest = Map.of("username", "admin", "password", BaseIntegrationTest.TEST_ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        token = objectMapper.readTree(responseBody).path("data").path("token").asText();
    }

    @Test
    void listMaterials_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/materials")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void listMaterials_withSearch_shouldReturnFilteredList() throws Exception {
        String code = "test-material-" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO material(material_code, material_name) VALUES (?, ?)", code, "Search fixture");
        try {
            mockMvc.perform(get("/api/v1/materials")
                            .header("Authorization", "Bearer " + token)
                            .param("keyword", code)
                            .param("page", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items[0].materialCode").value(code))
                    .andExpect(jsonPath("$.data.total").value(1));
        } finally {
            jdbcTemplate.update("DELETE FROM material WHERE material_code = ?", code);
        }
    }

    @Test
    void listMaterials_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/materials"))
                .andExpect(status().isUnauthorized());
    }
}
