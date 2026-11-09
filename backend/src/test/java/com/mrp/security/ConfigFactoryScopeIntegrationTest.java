package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.importexport.excel.TemplateGenerator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.*;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL + HTTP. Fixtures touch only users, scopes and current factory/calendar configuration. */
@AutoConfigureMockMvc
class ConfigFactoryScopeIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TemplateGenerator templates;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    Long user, role, ownMapping, foreignMapping, ownCalendar, foreignCalendar;
    static final String ROOT = "/api/v1/mrp";
    static final String MAPPING = ROOT + "/factory-warehouse-mappings";
    static final String CALENDAR = ROOT + "/production-calendars";
    static final String[] PERMISSIONS = {"mrp_calendar:view", "mrp_calendar:manage",
            "mrp_warehouse_mapping:view", "mrp_warehouse_mapping:manage"};

    @BeforeEach void setup() {
        // This class owns an isolated Testcontainers database; never used on the live database.
        jdbc.update("DELETE FROM mrp_production_calendar");
        jdbc.update("DELETE FROM factory_warehouse_mapping");
        user = insert("INSERT INTO user(username,password_hash) VALUES('scope-" + UUID.randomUUID() + "','unused')");
        role = insert("INSERT INTO role(code,name,status) VALUES('" + UUID.randomUUID().toString().substring(0, 24) + "','scope','ACTIVE')");
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')", user);
        ownMapping = mapping("爱培科", "OWN"); foreignMapping = mapping("永惠", "OTHER");
        ownCalendar = calendar("爱培科"); foreignCalendar = calendar("永惠");
    }

    @AfterEach void cleanup() {
        TestSecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM mrp_production_calendar");
        jdbc.update("DELETE FROM factory_warehouse_mapping");
        jdbc.update("DELETE FROM role_data_scope WHERE role_id=?", role);
        jdbc.update("DELETE FROM user_role WHERE user_id=?", user);
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", user);
        jdbc.update("DELETE FROM role WHERE id=?", role);
        jdbc.update("DELETE FROM user WHERE id=?", user);
    }

    @Test void listFiltersAndExplicitFactoryReadsIncludingSyntheticDatesAreProtected() throws Exception {
        mvc.perform(get(ROOT + "/factories").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value("爱培科"));
        mvc.perform(get(MAPPING).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(ownMapping));
        mvc.perform(get(MAPPING + "/page").param("keyword", "OWN").param("pageSize", "1")
                .with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(ownMapping));
        mvc.perform(get(MAPPING + "/page").param("keyword", "OTHER").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        for (String value : List.of("", " "))
            mvc.perform(get(CALENDAR).param("factoryCode", value).with(authentication(auth())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(ownCalendar));
        for (String suffix : List.of("", "/month", "/page", "/export")) {
            mvc.perform(get(CALENDAR + suffix).param("factoryCode", "永惠").param("month", "2088-10")
                    .with(authentication(auth()))).andExpect(status().isForbidden());
            mvc.perform(get(CALENDAR + suffix).param("factoryCode", "爱培科").param("month", "2088-10")
                    .with(authentication(auth()))).andExpect(status().isOk());
        }
        mvc.perform(get(CALENDAR + "/page").param("factoryCode", "爱培科").param("month", "2088-10")
                .param("status", "UNCONFIGURED").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(30));
    }

    @Test void recordIdentityCannotBeForgedAndDeniedWritesLeaveDataUnchanged() throws Exception {
        for (String resource : List.of(MAPPING + "/" + foreignMapping, CALENDAR + "/" + foreignCalendar)) {
            String body = resource.startsWith(MAPPING)
                    ? "{\"factoryCode\":\"爱培科\",\"warehouseCode\":\"FORGED\"}"
                    : calendarBody("爱培科", "2088-10-01", 999, 0);
            mvc.perform(put(resource).with(authentication(auth())).contentType("application/json")
                    .content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(resource).with(authentication(auth()))).andExpect(status().isForbidden());
        }
        mvc.perform(post(MAPPING).with(authentication(auth())).contentType("application/json")
                .content("{\"factoryCode\":\"永惠\",\"warehouseCode\":\"FORGED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(CALENDAR).with(authentication(auth())).contentType("application/json")
                .content(calendarBody("永惠", "2088-10-02", 999, 0))).andExpect(status().isForbidden());
        assertEquals("OTHER", jdbc.queryForObject("SELECT warehouse_code FROM factory_warehouse_mapping WHERE id=?", String.class, foreignMapping));
        assertEquals(100, jdbc.queryForObject("SELECT daily_capacity FROM mrp_production_calendar WHERE id=?", Integer.class, foreignCalendar));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_production_calendar", Integer.class));
    }

    @Test void copyRequiresBothSourceAndTargetAndOwnBatchStillCommits() throws Exception {
        for (String[] pair : List.of(new String[]{"爱培科", "永惠"}, new String[]{"永惠", "爱培科"}))
            for (String suffix : List.of("/batch/preview", "/batch"))
                mvc.perform(post(CALENDAR + suffix).with(authentication(auth())).contentType("application/json")
                        .content(batch(pair[0], pair[1], null))).andExpect(status().isForbidden());
        String preview = mvc.perform(post(CALENDAR + "/batch/preview").with(authentication(auth()))
                .contentType("application/json").content(batch("爱培科", null, null)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fingerprint = new com.fasterxml.jackson.databind.ObjectMapper().readTree(preview).path("data").path("fingerprint").asText();
        assertFalse(fingerprint.isBlank());
        mvc.perform(post(CALENDAR + "/batch").with(authentication(auth())).contentType("application/json")
                .content(batch("爱培科", null, fingerprint))).andExpect(status().isOk());
        assertEquals(120, jdbc.queryForObject("SELECT daily_capacity FROM mrp_production_calendar WHERE factory_code='爱培科' AND calendar_date='2088-10-02'", Integer.class));
    }

    @Test void mixedFactoryImportsAreRejectedBeforeFirstRowWrites() throws Exception {
        byte[] mappings = workbook(templates.generateFactoryWarehouseTemplate(),
                new String[]{"爱培科", "CHANGED", "本厂", "", "", "是", "自有仓", "1", "启用"},
                new String[]{"永惠", "CHANGED2", "另一厂", "", "", "是", "自有仓", "1", "启用"});
        byte[] calendars = workbook(templates.generateProductionCalendarTemplate(),
                new String[]{"爱培科", "2088-10-02", "是", "200", "台", "测试"},
                new String[]{"永惠", "2088-10-02", "是", "300", "台", "测试"});
        for (Object[] item : List.of(new Object[]{MAPPING, mappings}, new Object[]{CALENDAR, calendars})) {
            var file = new MockMultipartFile("file", "scope.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", (byte[]) item[1]);
            var preview = mvc.perform(multipart((String) item[0] + "/import/preview").file(file).with(authentication(auth())))
                    .andExpect(MAPPING.equals(item[0]) ? status().isForbidden() : status().isOk());
            if (!MAPPING.equals(item[0])) preview.andExpect(jsonPath("$.data.errorRows").value(0));
            mvc.perform(multipart((String) item[0] + "/import").file(file).with(authentication(auth())))
                    .andExpect(status().isForbidden());
        }
        assertEquals("OWN", jdbc.queryForObject("SELECT warehouse_code FROM factory_warehouse_mapping WHERE id=?", String.class, ownMapping));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_production_calendar", Integer.class));
        var ownFile = new MockMultipartFile("file", "own.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                workbook(templates.generateProductionCalendarTemplate(), new String[]{"爱培科", "2088-10-02", "是", "200", "台", "测试"}));
        mvc.perform(multipart(CALENDAR + "/import").file(ownFile).with(authentication(auth()))).andExpect(status().isOk());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_production_calendar", Integer.class));
    }

    @Test void currentScopeRevocationAndActiveRoleGrantsTakeEffectImmediately() throws Exception {
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", user);
        for (String url : List.of(ROOT + "/factories", MAPPING, CALENDAR))
            mvc.perform(get(url).with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get(CALENDAR + "/month").param("factoryCode", "爱培科").param("month", "2088-10")
                .with(authentication(auth()))).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)", user, role);
        jdbc.update("INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(?,'FACTORY','永惠')", role);
        mvc.perform(get(ROOT + "/factories").with(authentication(auth()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("永惠")).andExpect(jsonPath("$.data.length()").value(1));
        jdbc.update("UPDATE role SET status='INACTIVE' WHERE id=?", role);
        mvc.perform(get(ROOT + "/factories").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        jdbc.update("UPDATE role SET status='ACTIVE' WHERE id=?", role);
        jdbc.update("UPDATE role_data_scope SET scope_type='ALL',scope_value=NULL WHERE role_id=?", role);
        mvc.perform(get(CALENDAR).with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get(ROOT + "/factories").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test void ownCrudAndFunctionalPermissionsRemainIndependentOfFactoryScope() throws Exception {
        TestSecurityContextHolder.clearContext();
        mvc.perform(get(CALENDAR).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(CALENDAR).with(authentication(auth("mrp_calendar:manage")))).andExpect(status().isForbidden());
        mvc.perform(delete(CALENDAR + "/" + ownCalendar).with(authentication(auth("mrp_calendar:view")))).andExpect(status().isForbidden());
        mvc.perform(put(CALENDAR + "/" + ownCalendar).with(authentication(auth())).contentType("application/json")
                .content(calendarBody("永惠", "2088-10-09", 150, 0))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.factoryCode").value("爱培科")).andExpect(jsonPath("$.data.revision").value(1));
        mvc.perform(delete(CALENDAR + "/" + ownCalendar).with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(post(CALENDAR).with(authentication(auth())).contentType("application/json")
                .content(calendarBody("爱培科", "2088-10-01", 160, 0))).andExpect(status().isOk());
        mvc.perform(put(MAPPING + "/" + ownMapping).with(authentication(auth())).contentType("application/json")
                .content(mappingBody("永惠", "OWN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.factoryCode").value("爱培科"));
        mvc.perform(delete(MAPPING + "/" + ownMapping).with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(post(MAPPING).with(authentication(auth())).contentType("application/json")
                .content(mappingBody("爱培科", "NEW"))).andExpect(status().isOk());
        mvc.perform(delete(CALENDAR + "/9223372036854775807").with(authentication(auth()))).andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken auth(String... permissions) {
        String[] codes = permissions.length == 0 ? PERMISSIONS : permissions;
        return new UsernamePasswordAuthenticationToken(new com.mrp.common.security.CurrentUser.UserPrincipal(user, "scope-test"), null,
                Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList());
    }
    private long insert(String sql) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private long mapping(String factory, String warehouse) {
        return insert("INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code,enabled) VALUES('" + factory + "','" + warehouse + "',1)");
    }
    private long calendar(String factory) {
        return insert("INSERT INTO mrp_production_calendar(factory_code,calendar_date,is_production_day,allocation_weight,daily_capacity,capacity_unit,revision) VALUES('" + factory + "','2088-10-01',1,1,100,'台',0)");
    }
    private String calendarBody(String factory, String date, int capacity, int revision) {
        return "{\"factoryCode\":\"" + factory + "\",\"calendarDate\":\"" + date + "\",\"productionDay\":true,\"dailyCapacity\":" + capacity + ",\"capacityUnit\":\"台\",\"revision\":" + revision + "}";
    }
    private String mappingBody(String factory, String warehouse) {
        return "{\"factoryCode\":\"" + factory + "\",\"warehouseCode\":\"" + warehouse
                + "\",\"warehouseName\":\"测试仓\",\"goodWarehouse\":true,\"warehouseAttribute\":\"自有仓\"}";
    }
    private String batch(String target, String source, String fingerprint) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("factoryCode", target); body.put("startDate", "2088-10-02"); body.put("endDate", "2088-10-02");
        body.put("weekdays", List.of(1,2,3,4,5,6,7)); body.put("productionDay", true);
        body.put("dailyCapacity", 120); body.put("capacityUnit", "台"); body.put("overwrite", true);
        body.put("copyFromFactory", source); body.put("expectedFingerprint", fingerprint);
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
    }
    private byte[] workbook(byte[] template, String[]... rows) throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template)); var out = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            for (int i = sheet.getLastRowNum(); i >= 2; i--) sheet.removeRow(sheet.getRow(i));
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 2);
                for (int j = 0; j < rows[i].length; j++) row.createCell(j).setCellValue(rows[i][j]);
            }
            book.write(out); return out.toByteArray();
        }
    }
}
