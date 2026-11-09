package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.service.Phase2ScopeConfigService;
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

@AutoConfigureMockMvc
class LegacyScopeVersionAccessIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired Phase2ScopeConfigService snapshots;
    static final String M="/api/v1/mrp/factory-warehouse-mapping-versions", S="/api/v1/mrp/stock-policy-versions";
    static final String[] PERMS={"mrp_warehouse_mapping:view","mrp_warehouse_mapping:manage","mrp_stock_policy:view","mrp_stock_policy:manage"};
    Long user,role,own,foreign,mixed,empty,unknown,stock,unbound,missing,emptyStock;
    List<Long> mappings=new ArrayList<>(),stocks=new ArrayList<>(),batches=new ArrayList<>();

    @BeforeEach void setup() {
        user=insert("INSERT INTO user(username,password_hash) VALUES('ls-"+UUID.randomUUID()+"','unused')");
        role=insert("INSERT INTO role(code,name,status) VALUES('"+UUID.randomUUID().toString().substring(0,24)+"','scope','ACTIVE')");
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')",user);
        own=mapping("爱培科","LS_APK"); foreign=mapping("永惠","LS_YH");
        mixed=mapping("爱培科","LS_APK","永惠","LS_YH");empty=mapping();unknown=mapping("UNKNOWN","LS_X");
        stock=stock("LS_APK");unbound=stock("LS_YH");missing=stock("LS_MISSING");emptyStock=stock();
        bind(stock,own);bind(missing,own);bind(emptyStock,own);
    }
    @AfterEach void cleanup() {
        TestSecurityContextHolder.clearContext();
        for(long id:batches)jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?",id);
        for(long id:stocks){jdbc.update("DELETE FROM stock_policy_detail WHERE version_id=?",id);jdbc.update("DELETE FROM stock_policy_version WHERE id=?",id);}
        for(long id:mappings){jdbc.update("DELETE FROM factory_warehouse_mapping_detail WHERE version_id=?",id);jdbc.update("DELETE FROM factory_warehouse_mapping_version WHERE id=?",id);}
        jdbc.update("DELETE FROM factory_warehouse_mapping WHERE warehouse_code='LS_APK'");
        jdbc.update("DELETE FROM role_data_scope WHERE role_id=?",role);jdbc.update("DELETE FROM user_role WHERE user_id=?",user);
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?",user);jdbc.update("DELETE FROM role WHERE id=?",role);jdbc.update("DELETE FROM user WHERE id=?",user);
    }

    @Test void listsAndDetailsAuthorizeTheWholeVersion() throws Exception {
        assertEquals(Set.of(own),visible(M));assertEquals(Set.of(stock),visible(S));
        mvc.perform(get(M+"/"+own+"/details").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get(S+"/"+stock+"/details").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].warehouseCode").value("LS_APK"));
        for(long id:List.of(foreign,mixed,empty,unknown))mvc.perform(get(M+"/"+id+"/details").with(authentication(auth()))).andExpect(status().isForbidden());
        for(long id:List.of(unbound,missing,emptyStock))mvc.perform(get(S+"/"+id+"/details").with(authentication(auth()))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"save","publish","delete"})
    void foreignOrMixedMappingWritesCannotPartiallyMutate(String action) throws Exception {
        for(long id:List.of(foreign,mixed,empty,unknown)) {
            var request=switch(action){case "save" -> post(M+"/"+id+"/details").contentType("application/json").content("[{\"factoryCode\":\"爱培科\",\"warehouseCode\":\"LS_APK\"}]");case "publish" -> post(M+"/"+id+"/publish");default -> delete(M+"/"+id);};
            mvc.perform(request.with(authentication(auth()))).andExpect(status().isForbidden());
            assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM factory_warehouse_mapping_version WHERE id=?",String.class,id));
            assertEquals(0,jdbc.queryForObject("SELECT is_deleted FROM factory_warehouse_mapping_version WHERE id=?",Integer.class,id));
        }
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM factory_warehouse_mapping_detail WHERE version_id=?",Integer.class,mixed));
    }
    @Test void replacementAuthorizesOldAndEverySubmittedFactoryBeforeDeleting() throws Exception {
        mvc.perform(post(M+"/"+own+"/details").with(authentication(auth())).contentType("application/json").content("[{\"factoryCode\":\"爱培科\",\"warehouseCode\":\"LS_CHANGED\"},{\"factoryCode\":\"永惠\",\"warehouseCode\":\"LS_YH\"}]")).andExpect(status().isForbidden());
        assertEquals(List.of("LS_APK"),jdbc.queryForList("SELECT warehouse_code FROM factory_warehouse_mapping_detail WHERE version_id=?",String.class,own));
        mvc.perform(post(M+"/"+own+"/details").with(authentication(auth())).contentType("application/json").content("[{\"factoryCode\":\" 爱培科 \",\"warehouseCode\":\"LS_APK\",\"enabled\":false}]")).andExpect(status().isOk());
        assertEquals("爱培科",jdbc.queryForObject("SELECT factory_code FROM factory_warehouse_mapping_detail WHERE version_id=?",String.class,own));
        mvc.perform(post(M+"/"+own+"/publish").with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(post(M+"/"+own+"/details").with(authentication(auth())).contentType("application/json").content("[]")).andExpect(status().isBadRequest());
    }
    @Test void historyUsesFrozenMappingsEvenWhenCurrentMappingPointsElsewhere() throws Exception {
        jdbc.update("INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code,enabled) VALUES('LS_CURRENT','LS_APK',1)");
        mvc.perform(get(S+"/"+stock+"/details").with(authentication(auth()))).andExpect(status().isOk());
        jdbc.update("UPDATE factory_warehouse_mapping_detail SET enabled=0 WHERE version_id=?",own);
        assertEquals(Set.of(stock),visible(S));
        long alternate=mapping("永惠","LS_APK");bind(stock,alternate);
        assertFalse(visible(S).contains(stock));
        mvc.perform(get(S+"/"+stock+"/details").with(authentication(auth()))).andExpect(status().isForbidden());
        grant("FACTORY","永惠");assertTrue(visible(S).contains(stock));
    }
    @Test void missingFrozenMappingAndUnknownFactoryNeverUseCurrentOwnership() throws Exception {
        bind(stock,null);assertFalse(visible(S).contains(stock));
        bind(unbound,unknown);
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'UNKNOWN')",user);
        assertFalse(visible(M).contains(unknown));assertFalse(visible(S).contains(unbound));
    }
    @Test void emptyGlobalCreatesAndLegacyStockWritesRequireAllScope() throws Exception {
        mvc.perform(post(M).with(authentication(auth())).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(S).with(authentication(auth())).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        for(long id:List.of(stock,unbound,missing,emptyStock)) {
            mvc.perform(post(S+"/"+id+"/details").with(authentication(auth())).contentType("application/json").content("[]")).andExpect(status().isForbidden());
            mvc.perform(post(S+"/"+id+"/publish").with(authentication(auth()))).andExpect(status().isForbidden());
            mvc.perform(delete(S+"/"+id).with(authentication(auth()))).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE stock_policy_version SET status='PUBLISHED' WHERE id=?",stock);
        mvc.perform(post(S+"/"+stock+"/incremental").with(authentication(auth())).contentType("application/json").content("{\"overrides\":[{\"warehouseCode\":\"LS_APK\",\"materialCode\":\"LS_M\"}]}")).andExpect(status().isForbidden());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stock_policy_detail WHERE version_id=?",Integer.class,stock));
    }
    @Test void allScopeRetainsLegacyDraftCrudAndIncrementalCopy() throws Exception {
        grant("ALL",null);assertTrue(visible(M).containsAll(mappings));assertTrue(visible(S).containsAll(stocks));
        var created=mvc.perform(post(M).with(authentication(auth())).contentType("application/json").content("{}")).andExpect(status().isOk()).andReturn();
        long mid=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();mappings.add(mid);
        mvc.perform(delete(M+"/"+mid).with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(post(S+"/"+unbound+"/publish").with(authentication(auth()))).andExpect(status().isOk());
        var copy=mvc.perform(post(S+"/"+unbound+"/incremental").with(authentication(auth())).contentType("application/json").content("{\"overrides\":[{\"warehouseCode\":\"LS_YH\",\"materialCode\":\"LS_M\",\"safetyStockQty\":2,\"warningStockQty\":3}]}")).andExpect(status().isOk()).andReturn();
        long sid=new com.fasterxml.jackson.databind.ObjectMapper().readTree(copy.getResponse().getContentAsString()).at("/data/id").asLong();stocks.add(sid);
        assertEquals(new java.math.BigDecimal("2.000000"),jdbc.queryForObject("SELECT safety_stock_qty FROM stock_policy_detail WHERE version_id=?",java.math.BigDecimal.class,sid));
        mvc.perform(post(S+"/"+sid+"/details").with(authentication(auth())).contentType("application/json").content("[]")).andExpect(status().isOk());
        mvc.perform(delete(S+"/"+sid).with(authentication(auth()))).andExpect(status().isOk());
    }
    @Test void roleRevocationAndFunctionalPermissionsRemainEnforced() throws Exception {
        grant("ALL",null);jdbc.update("UPDATE role SET status='INACTIVE' WHERE id=?",role);assertEquals(Set.of(own),visible(M));
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?",user);assertTrue(visible(M).isEmpty());assertTrue(visible(S).isEmpty());
        mvc.perform(get(M+"/"+own+"/details").with(authentication(auth()))).andExpect(status().isForbidden());
        mvc.perform(get(S+"/"+stock+"/details").with(authentication(auth()))).andExpect(status().isForbidden());
        TestSecurityContextHolder.clearContext();mvc.perform(get(M).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(S).with(authentication(auth("mrp_stock_policy:manage")))).andExpect(status().isForbidden());
        mvc.perform(delete(M+"/"+own).with(authentication(auth("mrp_warehouse_mapping:view")))).andExpect(status().isForbidden());
    }
    @Test void deletedAndMissingVersionsAreDeniedAndWorkerReadsNeedNoHttpUser() throws Exception {
        jdbc.update("UPDATE factory_warehouse_mapping_version SET is_deleted=1 WHERE id=?",own);
        jdbc.update("UPDATE stock_policy_version SET is_deleted=1 WHERE id=?",stock);grant("ALL",null);
        mvc.perform(get(M+"/"+own+"/details").with(authentication(auth()))).andExpect(status().isForbidden());
        mvc.perform(get(S+"/"+stock+"/details").with(authentication(auth()))).andExpect(status().isForbidden());
        mvc.perform(get(S+"/9223372036854775807/details").with(authentication(auth()))).andExpect(status().isForbidden());
        TestSecurityContextHolder.clearContext();assertEquals(1,snapshots.getFactoryWarehouseDetails(foreign).size());assertEquals(1,snapshots.getStockPolicyDetails(unbound).size());
    }
    private Set<Long> visible(String root) throws Exception {
        var result=mvc.perform(get(root).with(authentication(auth()))).andExpect(status().isOk()).andReturn();
        var rows=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("data");Set<Long> ids=new HashSet<>();rows.forEach(r->ids.add(r.get("id").asLong()));return ids;
    }
    private long mapping(String... values) {
        long id=insert("INSERT INTO factory_warehouse_mapping_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'DRAFT' FROM factory_warehouse_mapping_version");mappings.add(id);
        for(int i=0;i<values.length;i+=2)jdbc.update("INSERT INTO factory_warehouse_mapping_detail(version_id,factory_code,warehouse_code,enabled) VALUES(?,?,?,1)",id,values[i],values[i+1]);return id;
    }
    private long stock(String... warehouses) {
        long id=insert("INSERT INTO stock_policy_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'DRAFT' FROM stock_policy_version");stocks.add(id);
        for(String w:warehouses)jdbc.update("INSERT INTO stock_policy_detail(version_id,warehouse_code,material_code,safety_stock_qty,warning_stock_qty) VALUES(?,?,'LS_M',1,2)",id,w);return id;
    }
    private void bind(long stockId,Long mappingId) {
        long id=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,stock_policy_version_id,factory_warehouse_mapping_version_id) VALUES('爱培科','CANCELLED',1,'v4-rolling-business-weeks',"+stockId+","+mappingId+")");batches.add(id);
    }
    private void grant(String type,String value){jdbc.update("INSERT IGNORE INTO user_role(user_id,role_id) VALUES(?,?)",user,role);jdbc.update("INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(?,?,?)",role,type,value);}
    private long insert(String sql){var keys=new GeneratedKeyHolder();jdbc.update(c->c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS),keys);return Objects.requireNonNull(keys.getKey()).longValue();}
    private UsernamePasswordAuthenticationToken auth(String... permissions){return new UsernamePasswordAuthenticationToken(new com.mrp.common.security.CurrentUser.UserPrincipal(user,"legacy-scope"),null,Arrays.stream(permissions.length==0?PERMS:permissions).map(SimpleGrantedAuthority::new).toList());}
}
