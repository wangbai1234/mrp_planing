package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Entire legacy versions are the security boundary, including empty and inconsistent snapshots. */
@AutoConfigureMockMvc
class LegacyCalendarScopeIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    static final String ROOT = "/api/v1/mrp/production-calendar-versions";
    static final String[] PERMISSIONS = {"mrp_calendar:view", "mrp_calendar:manage", "mrp_batch:list"};
    Long user, role, own, foreign, mixed, mismatch, empty, unknown, unknownDay;
    List<Long> fixtures = new ArrayList<>();

    @BeforeEach void setup() {
        user = insert("INSERT INTO user(username,password_hash) VALUES('lc-"+UUID.randomUUID()+"','unused')");
        role = insert("INSERT INTO role(code,name,status) VALUES('"+UUID.randomUUID().toString().substring(0,24)+"','scope','ACTIVE')");
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')", user);
        own = version("爱培科", "爱培科"); foreign = version("永惠", "永惠");
        mixed = version("ALL", "爱培科", "永惠"); mismatch = version("爱培科", "永惠");
        empty = version("ALL"); unknown = version("UNKNOWN", "爱培科");
        unknownDay = version("爱培科", "UNKNOWN");
    }

    @AfterEach void cleanup() {
        TestSecurityContextHolder.clearContext();
        for (Long id : fixtures) {
            jdbc.update("DELETE FROM production_calendar_day WHERE version_id=?", id);
            jdbc.update("DELETE FROM production_calendar_version WHERE id=?", id);
        }
        jdbc.update("DELETE FROM role_data_scope WHERE role_id=?", role);
        jdbc.update("DELETE FROM user_role WHERE user_id=?", user);
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", user);
        jdbc.update("DELETE FROM role WHERE id=?", role);
        jdbc.update("DELETE FROM user WHERE id=?", user);
    }

    @Test void listsAndBatchChoicesFilterWholeVersionsInSql() throws Exception {
        assertEquals(Set.of(own), visible());
        var choices = mvc.perform(get("/api/v1/mrp/batch-creation-versions").with(authentication(auth())))
                .andExpect(status().isOk()).andReturn();
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(choices.getResponse().getContentAsString());
        assertEquals(1, tree.at("/data/productionCalendarVersions").size());
        assertEquals(own.longValue(), tree.at("/data/productionCalendarVersions/0/id").asLong());
        assertTrue(tree.at("/data/categoryRoleVersions").isArray());
        assertTrue(tree.at("/data/materialPolicyVersions").isArray());
        for (Long id : List.of(foreign, mixed, mismatch, empty, unknown, unknownDay)) {
            mvc.perform(get(ROOT+"/"+id).with(authentication(auth()))).andExpect(status().isForbidden());
            mvc.perform(get(ROOT+"/"+id+"/days").with(authentication(auth()))).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest @ValueSource(strings={"save","publish","delete"})
    void foreignWritesAreRejectedWithoutPartialMutation(String action) throws Exception {
        for (Long id : List.of(foreign, mixed, mismatch, empty, unknown, unknownDay)) {
            var request = switch (action) {
                case "save" -> post(ROOT+"/"+id+"/days").contentType("application/json").content("[]");
                case "publish" -> post(ROOT+"/"+id+"/publish");
                default -> delete(ROOT+"/"+id);
            };
            mvc.perform(request.with(authentication(auth()))).andExpect(status().isForbidden());
            assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM production_calendar_version WHERE id=?", String.class, id));
            assertEquals(0, jdbc.queryForObject("SELECT is_deleted FROM production_calendar_version WHERE id=?", Integer.class, id));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM production_calendar_day WHERE version_id=?", Integer.class, foreign));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM production_calendar_day WHERE version_id=?", Integer.class, mixed));
    }

    @Test void ownCrudRetainsHeaderFactoryAndPublishedVersionIsImmutable() throws Exception {
        mvc.perform(get(ROOT+"/"+own+"/days").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(post(ROOT+"/"+own+"/days").with(authentication(auth())).contentType("application/json").content(
                "[{\"factoryCode\":\"永惠\",\"calendarDate\":\"2035-01-03\",\"isProductionDay\":true,\"allocationWeight\":1,\"dailyCapacity\":120}]"))
                .andExpect(status().isOk());
        assertEquals(List.of("爱培科"), jdbc.queryForList("SELECT factory_code FROM production_calendar_day WHERE version_id=?", String.class, own));
        mvc.perform(post(ROOT+"/"+own+"/publish").with(authentication(auth()))).andExpect(status().isOk());
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM production_calendar_version WHERE id=?", String.class, own));
        assertNotNull(jdbc.queryForObject("SELECT checksum FROM production_calendar_version WHERE id=?", String.class, own));
        mvc.perform(post(ROOT+"/"+own+"/days").with(authentication(auth())).contentType("application/json").content("[]")).andExpect(status().isBadRequest());
        mvc.perform(delete(ROOT+"/"+own).with(authentication(auth()))).andExpect(status().isBadRequest());
        var result = mvc.perform(post(ROOT).param("factoryCode","爱培科").with(authentication(auth())))
                .andExpect(status().isOk()).andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
        fixtures.add(id);
        mvc.perform(delete(ROOT+"/"+id).with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(get(ROOT+"/"+id).with(authentication(auth()))).andExpect(status().isForbidden());
        for (String factory : List.of("永惠","ALL","UNKNOWN",""))
            mvc.perform(post(ROOT).param("factoryCode",factory).with(authentication(auth()))).andExpect(status().isForbidden());
    }

    @Test void twoFactoryGrantsCanReadMixedButCannotWriteAllHeaderOrSeeUnknowns() throws Exception {
        grant("FACTORY", "永惠");
        assertEquals(Set.of(own,foreign,mixed,mismatch), visible());
        mvc.perform(get(ROOT+"/"+mixed+"/days").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(post(ROOT+"/"+mixed+"/days").with(authentication(auth())).contentType("application/json").content("[]")).andExpect(status().isForbidden());
        mvc.perform(post(ROOT+"/"+mixed+"/publish").with(authentication(auth()))).andExpect(status().isForbidden());
        mvc.perform(delete(ROOT+"/"+mixed).with(authentication(auth()))).andExpect(status().isForbidden());
        mvc.perform(post(ROOT).param("factoryCode","ALL").with(authentication(auth()))).andExpect(status().isForbidden());
        // Even a grant for an unknown factory does not redefine the supported factory catalog.
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'UNKNOWN')", user);
        assertFalse(visible().contains(unknownDay));
        assertFalse(visible().contains(unknown));
    }

    @ParameterizedTest @ValueSource(strings={"", "ALL", "UNKNOWN"})
    void invalidDayFactoriesRequireActiveAllScope(String factory) throws Exception {
        long id = version("爱培科", factory);
        assertFalse(visible().contains(id));
        grant("ALL", null);
        assertTrue(visible().containsAll(fixtures));
        mvc.perform(get(ROOT+"/"+empty).with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(get(ROOT+"/"+id+"/days").with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(post(ROOT+"/"+empty+"/days").with(authentication(auth())).contentType("application/json").content("[]")).andExpect(status().isOk());
        jdbc.update("UPDATE role SET status='INACTIVE' WHERE id=?", role);
        assertFalse(visible().contains(id));
        assertFalse(visible().contains(empty));
    }

    @Test void revocationAndMissingFunctionalPermissionsAreEnforced() throws Exception {
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", user);
        assertTrue(visible().isEmpty());
        mvc.perform(get(ROOT+"/"+own).with(authentication(auth()))).andExpect(status().isForbidden());
        grant("FACTORY", "爱培科");
        assertEquals(Set.of(own), visible());
        TestSecurityContextHolder.clearContext();
        mvc.perform(get(ROOT).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).with(authentication(auth("mrp_calendar:manage")))).andExpect(status().isForbidden());
        mvc.perform(delete(ROOT+"/"+own).with(authentication(auth("mrp_calendar:view")))).andExpect(status().isForbidden());
        mvc.perform(get(ROOT+"/9223372036854775807").with(authentication(auth()))).andExpect(status().isForbidden());
    }

    private Set<Long> visible() throws Exception {
        var result = mvc.perform(get(ROOT).with(authentication(auth()))).andExpect(status().isOk()).andReturn();
        var rows = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("data");
        Set<Long> ids = new HashSet<>(); rows.forEach(row -> ids.add(row.get("id").asLong())); return ids;
    }

    private void grant(String type, String value) {
        jdbc.update("INSERT IGNORE INTO user_role(user_id,role_id) VALUES(?,?)", user,role);
        jdbc.update("INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(?,?,?)", role,type,value);
    }

    private long version(String factory, String... days) {
        var keys = new GeneratedKeyHolder();
        int versionNo = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM production_calendar_version", Integer.class));
        jdbc.update(c -> {
            var statement = c.prepareStatement("INSERT INTO production_calendar_version(version_no,factory_code,status) VALUES(?,?,'DRAFT')",Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1,versionNo); statement.setString(2,factory); return statement;
        }, keys);
        long id = Objects.requireNonNull(keys.getKey()).longValue(); fixtures.add(id);
        for (int i=0;i<days.length;i++) jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,allocation_weight,daily_capacity,revision) VALUES(?,?,?,1,1,100,0)", id,days[i],"2035-01-"+String.format("%02d",i+1));
        return id;
    }

    private long insert(String sql) {
        var keys = new GeneratedKeyHolder(); jdbc.update(c -> c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS),keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private UsernamePasswordAuthenticationToken auth(String... permissions) {
        return new UsernamePasswordAuthenticationToken(new com.mrp.common.security.CurrentUser.UserPrincipal(user,"legacy-calendar-test"),null,
                Arrays.stream(permissions.length==0?PERMISSIONS:permissions).map(SimpleGrantedAuthority::new).toList());
    }
}
