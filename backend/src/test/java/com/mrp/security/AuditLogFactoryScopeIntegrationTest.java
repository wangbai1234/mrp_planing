package com.mrp.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.ProductionCalendarEntry;
import com.mrp.mrp.service.ConfigMasterService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditLogFactoryScopeIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ConfigMasterService configs;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    Long user, role;
    String action;
    final Set<Long> own = new HashSet<>(), global = new HashSet<>(), foreign = new HashSet<>(), uncertain = new HashSet<>();

    @BeforeEach void setup() {
        action = "scope-" + UUID.randomUUID().toString().substring(0, 20);
        user = insert("INSERT INTO user(username,password_hash) VALUES('" + action + "','unused')");
        role = insert("INSERT INTO role(code,name,status) VALUES('" + action + "','scope','ACTIVE')");
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')", user);
        own.add(log("factory_warehouse_mapping", "爱培科", null));
        own.add(log("mrp_production_calendar", "爱培科|2088-10-01", null));
        own.add(log("mrp_daily_plan_override", "1", "爱培科"));
        own.add(log("factory_warehouse_mapping", "IMPORT", "爱培科"));
        foreign.add(log("factory_warehouse_mapping", "永惠", null));
        foreign.add(log("mrp_production_calendar", "永惠|2088-10-01", null));
        foreign.add(log("mrp_daily_plan_override", "2", "永惠"));
        for (String type : List.of("USER", "ROLE", "PERMISSION", "material_policy", "business_week"))
            global.add(log(type, "global", null));
        uncertain.add(log("stock_policy", "WH|SKU", null));
        uncertain.add(log("unknown", "3", null));
        uncertain.add(log("factory_warehouse_mapping", "IMPORT", null));
        uncertain.add(log("mrp_production_calendar", "IMPORT", null));
        uncertain.add(log("mrp_daily_plan_override", "4", "ALL"));
    }

    @AfterEach void cleanup() {
        TestSecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM audit_log WHERE operator_id=?", user);
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", user);
        jdbc.update("DELETE FROM role_data_scope WHERE role_id=?", role);
        jdbc.update("DELETE FROM user_role WHERE user_id=?", user);
        jdbc.update("DELETE FROM role WHERE id=?", role);
        jdbc.update("DELETE FROM user WHERE id=?", user);
    }

    @Test void unfilteredAndFilteredListsAndCountShareFactoryPredicate() throws Exception {
        Set<Long> expected = union(own, global);
        assertEquals(expected, ids(read(Map.of("action", action), auth())));
        JsonNode unfiltered = read(Map.of(), auth());
        assertTrue(Collections.disjoint(ids(unfiltered), union(foreign, uncertain)));
        assertEquals(own.stream().filter(id -> jdbc.queryForObject("SELECT resource_type FROM audit_log WHERE id=?", String.class, id)
                .equals("factory_warehouse_mapping")).count(),
                read(Map.of("action", action, "resourceType", "factory_warehouse_mapping"), auth()).path("total").asLong());
        for (String type : List.of("factory_warehouse_mapping", "mrp_production_calendar")) {
            String id = type.equals("mrp_production_calendar") ? "永惠|2088-10-01" : "永惠";
            assertEquals(0, read(Map.of("resourceType", type, "resourceId", id), auth()).path("total").asLong());
        }
        assertEquals(0, read(Map.of("action", action, "resourceType", "stock_policy"), auth()).path("total").asLong());
    }

    @Test void visibleLogsIncludeOperatorAvatarWhenAvailable() throws Exception {
        jdbc.update("UPDATE user SET display_name=?, avatar_url=? WHERE id=?",
                "头像测试员", "https://example.invalid/avatar.png", user);
        JsonNode items = read(Map.of("action", action, "resourceType", "material_policy"), auth()).path("items");
        assertEquals(1, items.size());
        assertEquals("头像测试员", items.get(0).path("operator_name").asText());
        assertEquals("https://example.invalid/avatar.png", items.get(0).path("operator_avatar_url").asText());
    }

    @Test void stablePagesHaveNoGapsOrDuplicatesAndPageBoundsAreSafe() throws Exception {
        Set<Long> seen = new HashSet<>();
        int count = own.size() + global.size();
        for (int page = 1; page <= count; page++) {
            JsonNode data = read(Map.of("action", action, "page", "" + page, "pageSize", "1"), auth());
            assertEquals(count, data.path("total").asInt());
            assertEquals(1, data.path("items").size());
            assertTrue(seen.add(data.path("items").get(0).path("id").asLong()));
        }
        assertEquals(union(own, global), seen);
        assertEquals(1, read(Map.of("page", "-1", "pageSize", "0"), auth()).path("pageSize").asInt());
        assertEquals(100, read(Map.of("pageSize", "10000"), auth()).path("pageSize").asInt());
        assertEquals(0, read(Map.of("page", "2147483647"), auth()).path("items").size());
    }

    @Test void currentRevocationAndActiveRoleFactoryOrAllGrantsAreRespected() throws Exception {
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", user);
        assertEquals(global, ids(read(Map.of("action", action), auth())));
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)", user, role);
        jdbc.update("INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(?,'FACTORY','永惠')", role);
        assertEquals(union(global, foreign), ids(read(Map.of("action", action), auth())));
        jdbc.update("UPDATE role SET status='INACTIVE' WHERE id=?", role);
        assertEquals(global, ids(read(Map.of("action", action), auth())));
        jdbc.update("UPDATE role SET status='ACTIVE' WHERE id=?", role);
        jdbc.update("UPDATE role_data_scope SET scope_type='ALL',scope_value=NULL WHERE role_id=?", role);
        assertEquals(union(own, global, foreign, uncertain), ids(read(Map.of("action", action), auth())));
    }

    @Test void twoFactoryGrantsDoNotExposeUnknownOrMultiFactoryRecords() throws Exception {
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠')", user);
        assertEquals(union(own, global, foreign), ids(read(Map.of("action", action), auth())));
        long conflicting = log("business_week", "scoped-global", "OTHER");
        long explicit = log("factory_warehouse_mapping", "爱培科", "OTHER");
        assertFalse(ids(read(Map.of("action", action), auth())).contains(conflicting));
        assertFalse(ids(read(Map.of("action", action), auth())).contains(explicit));
    }

    @Test void factoryAndWarehouseLogsUseDynamicScopeAndCombinedForecastNeedsEveryEnabledFactory() throws Exception {
        String third = "第三工厂-" + UUID.randomUUID().toString().substring(0, 8);
        long factoryLog = log("mrp_factory", third, null);
        long warehouseLog = log("factory_warehouse_mapping", third + "|WH-A", null);
        long combined = log("forecast_version", "combined", "ALL");
        try {
            jdbc.update("INSERT INTO mrp_factory(factory_code,factory_name) VALUES(?,?)", third, third);
            jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠'),(?,?)", user, user, third);
            assertTrue(ids(read(Map.of("action", action), auth())).containsAll(Set.of(factoryLog, warehouseLog)));
            assertTrue(ids(read(Map.of("action", action), auth())).contains(combined));
            jdbc.update("DELETE FROM user_factory_scope WHERE user_id=? AND factory_code=?", user, third);
            assertFalse(ids(read(Map.of("action", action), auth())).contains(combined));
            assertFalse(ids(read(Map.of("action", action), auth())).contains(factoryLog));
            assertFalse(ids(read(Map.of("action", action), auth())).contains(warehouseLog));
        } finally {
            jdbc.update("DELETE FROM mrp_factory WHERE factory_code=?", third);
        }
    }

    @Test void legacyDeletedRowsAreVisibleFromBusinessKeyAndImportPayloadIsNotAFactoryGrant() throws Exception {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_production_calendar WHERE calendar_date='2088-10-01'", Integer.class));
        long fake = log("unknown", "payload-only", null);
        jdbc.update("UPDATE audit_log SET after_value='{\"factoryCode\":\"爱培科\"}' WHERE id=?", fake);
        assertTrue(ids(read(Map.of("action", action), auth())).containsAll(own));
        assertFalse(ids(read(Map.of("action", action), auth())).contains(fake));
        assertEquals(0, read(Map.of("action", action, "keyword", "' OR 1=1 --"), auth()).path("total").asInt());
        assertEquals(0, read(Map.of("action", action, "resourceType", "' OR 1=1 --"), auth()).path("total").asInt());
        assertEquals(0, read(Map.of("action", action, "startDate", "2099-01-01"), auth()).path("total").asInt());
        assertEquals(0, read(Map.of("action", action, "endDate", "2000-01-01"), auth()).path("total").asInt());
    }

    @Test void authenticationAndFunctionalPermissionAreStillRequired() throws Exception {
        TestSecurityContextHolder.clearContext();
        mvc.perform(get("/api/v1/audit-logs").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/audit-logs").with(authentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(user, "scope"), null, List.of())))).andExpect(status().isForbidden());
    }

    @Test void factoryAndWarehouseViewPermissionsOnlyReadMatchingScopedResourceLogs() throws Exception {
        long ownFactory = log("mrp_factory", "爱培科", null);
        long foreignFactory = log("mrp_factory", "永惠", null);
        long ownWarehouse = log("factory_warehouse_mapping", "爱培科|WH-1", null);
        long foreignWarehouse = log("factory_warehouse_mapping", "永惠|WH-2", null);
        var factoryViewer = scopedAuth("mrp_factory:view");
        var warehouseViewer = scopedAuth("mrp_warehouse_mapping:view");

        assertEquals(Set.of(ownFactory), ids(read(Map.of("action", action, "resourceType", "mrp_factory", "resourceId", "爱培科"), factoryViewer)));
        assertEquals(0, read(Map.of("action", action, "resourceType", "mrp_factory", "resourceId", "永惠"), factoryViewer).path("total").asLong());
        assertEquals(Set.of(ownWarehouse), ids(read(Map.of("action", action, "resourceType", "factory_warehouse_mapping", "resourceId", "爱培科|WH-1"), warehouseViewer)));
        assertEquals(0, read(Map.of("action", action, "resourceType", "factory_warehouse_mapping", "resourceId", "永惠|WH-2"), warehouseViewer).path("total").asLong());
        assertFalse(ids(read(Map.of("action", action), auth())).containsAll(Set.of(foreignFactory, foreignWarehouse)));
        for (var viewer : List.of(factoryViewer, warehouseViewer)) {
            mvc.perform(get("/api/v1/audit-logs").with(authentication(viewer))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/audit-logs").param("resourceType", "material_policy").param("resourceId", "global")
                    .with(authentication(viewer))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/audit-logs").param("resourceType", "factory_warehouse_mapping").param("resourceId", "爱培科|WH-1")
                .with(authentication(factoryViewer))).andExpect(status().isForbidden());
    }

    @Test void configurationViewPermissionsOnlyReadTheirOwnResourceType() throws Exception {
        var checks = Map.of(
                "mrp_stock_policy:view", "stock_policy",
                "mrp_config:view", "material_policy",
                "mrp_business_week:view", "business_week",
                "mrp_calendar:view", "mrp_production_calendar"
        );
        for (var entry : checks.entrySet()) {
            String resourceId = "mrp_production_calendar".equals(entry.getValue()) ? "爱培科|2088-10-01" : "global";
            boolean factoryScoped = "mrp_production_calendar".equals(entry.getValue()) || "stock_policy".equals(entry.getValue());
            long visible = log(entry.getValue(), resourceId, factoryScoped ? "爱培科" : null);
            var viewer = scopedAuth(entry.getKey());
            assertTrue(ids(read(Map.of("action", action, "resourceType", entry.getValue(), "resourceId", resourceId), viewer)).contains(visible));
            mvc.perform(get("/api/v1/audit-logs").with(authentication(viewer))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/audit-logs").param("resourceType", "USER").param("resourceId", "global")
                    .with(authentication(viewer))).andExpect(status().isForbidden());
        }
    }

    @Test void newCalendarRowAndSingleOrMixedImportSummariesCarryScopes() throws Exception {
        TestSecurityContextHolder.setAuthentication(auth());
        LocalDate date = LocalDate.of(2088, 11, 12);
        try {
            var row = calendar("爱培科", date);
            configs.createProductionCalendar(row);
            assertEquals("爱培科", scope("CREATE", "爱培科|" + date));
            configs.deleteProductionCalendar(row.getId());
            assertEquals("爱培科", scope("DELETE", "爱培科|" + date));
            configs.importProductionCalendar(List.of(calendar("爱培科", date)), "single.xlsx");
            assertEquals("爱培科", scope("IMPORT", "IMPORT"));
            configs.importProductionCalendar(List.of(calendar("爱培科", date), calendar("永惠", date)), "mixed.xlsx");
            assertEquals("ALL", scope("IMPORT", "IMPORT"));
        } finally {
            jdbc.update("DELETE FROM mrp_production_calendar WHERE calendar_date=?", date);
        }
    }

    private String scope(String action, String resourceId) {
        return jdbc.queryForObject("SELECT factory_scope FROM audit_log WHERE operator_id=? AND action=? AND resource_id=? ORDER BY id DESC LIMIT 1", String.class, user, action, resourceId);
    }
    private ProductionCalendarEntry calendar(String factory, LocalDate date) {
        var row = new ProductionCalendarEntry();
        row.setFactoryCode(factory); row.setCalendarDate(date); row.setProductionDay(true);
        row.setDailyCapacity(BigDecimal.valueOf(100)); row.setCapacityUnit("台"); return row;
    }
    private JsonNode read(Map<String, String> params, UsernamePasswordAuthenticationToken auth) throws Exception {
        var request = get("/api/v1/audit-logs").with(authentication(auth));
        params.forEach(request::param);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private Set<Long> ids(JsonNode data) {
        Set<Long> result = new HashSet<>();
        data.path("items").forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }
    @SafeVarargs private static Set<Long> union(Set<Long>... sets) {
        Set<Long> result = new HashSet<>(); for (var set : sets) result.addAll(set); return result;
    }
    private UsernamePasswordAuthenticationToken auth() {
        return scopedAuth("audit:view");
    }
    private UsernamePasswordAuthenticationToken scopedAuth(String permission) {
        return new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(user, "scope"), null,
                List.of(new SimpleGrantedAuthority(permission)));
    }
    private long log(String type, String resource, String scope) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var stmt = connection.prepareStatement("INSERT INTO audit_log(operator_id,action,resource_type,resource_id,factory_scope,after_value) VALUES(?,?,?,?,?,'{\"test\":true}')", Statement.RETURN_GENERATED_KEYS);
            stmt.setLong(1, user); stmt.setString(2, action); stmt.setString(3, type); stmt.setString(4, resource); stmt.setString(5, scope); return stmt;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private long insert(String sql) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
