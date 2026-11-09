package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.importexport.excel.TemplateGenerator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/** Current stock-policy endpoints on disposable MySQL; no live master data or protected plans are touched. */
@AutoConfigureMockMvc
class StockPolicyScopeIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TemplateGenerator templates;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    static final String ROOT="/api/v1/mrp/stock-policies";
    static final String[] PERMISSIONS={"mrp_stock_policy:view","mrp_stock_policy:manage","audit:view"};
    Long user,role,own,foreign,unknown,disabled;
    String material;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM stock_policy");
        jdbc.update("DELETE FROM factory_warehouse_mapping");
        material="stock-scope-"+UUID.randomUUID();
        jdbc.update("INSERT INTO material(material_code,material_name) VALUES(?,'库存策略权限测试夹具')",material);
        user=insert("INSERT INTO user(username,password_hash) VALUES('stock-"+UUID.randomUUID()+"','unused')");
        role=insert("INSERT INTO role(code,name,status) VALUES('"+UUID.randomUUID().toString().substring(0,24)+"','scope','ACTIVE')");
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')",user);
        jdbc.update("INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code,enabled) VALUES('爱培科','OWN',1),('永惠','OTHER',1),('DISABLED_FACTORY','DISABLED',0)");
        own=policy("OWN"); foreign=policy("OTHER"); unknown=policy("UNKNOWN"); disabled=policy("DISABLED");
    }

    @AfterEach void cleanup() {
        TestSecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM stock_policy"); jdbc.update("DELETE FROM factory_warehouse_mapping");
        jdbc.update("DELETE FROM material WHERE material_code=?",material);
        jdbc.update("DELETE FROM role_data_scope WHERE role_id=?",role);
        jdbc.update("DELETE FROM user_role WHERE user_id=?",user);
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?",user);
        jdbc.update("DELETE FROM role WHERE id=?",role); jdbc.update("DELETE FROM user WHERE id=?",user);
    }

    @Test void filteringOccursBeforePagingAndCountAndWarehouseDropdown() throws Exception {
        mvc.perform(get(ROOT).with(authentication(auth()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(own));
        mvc.perform(get(ROOT+"/page").param("pageSize","1").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(own));
        mvc.perform(get(ROOT+"/page").param("page","2").param("pageSize","1").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        for(String field:List.of("warehouseCode","keyword"))
            mvc.perform(get(ROOT+"/page").param(field,"OTHER").with(authentication(auth())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(ROOT+"/page").param("keyword",material).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get(ROOT+"/warehouses").with(authentication(auth()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].code").value("OWN"));
    }

    @Test void originalWarehouseControlsWritesAndOwnKeysStayImmutable() throws Exception {
        for(Long id:List.of(foreign,unknown,disabled)) {
            mvc.perform(put(ROOT+"/"+id).with(authentication(auth())).contentType("application/json").content(body("OWN",7)))
                    .andExpect(status().isForbidden());
            mvc.perform(delete(ROOT+"/"+id).with(authentication(auth()))).andExpect(status().isForbidden());
        }
        mvc.perform(post(ROOT).with(authentication(auth())).contentType("application/json").content(body("OTHER",7)))
                .andExpect(status().isForbidden());
        mvc.perform(put(ROOT+"/"+own).with(authentication(auth())).contentType("application/json").content(body("OTHER",7)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.warehouseCode").value("OWN"));
        assertEquals(7,jdbc.queryForObject("SELECT safety_stock_qty FROM stock_policy WHERE id=?",Integer.class,own));
        assertEquals(2,jdbc.queryForObject("SELECT safety_stock_qty FROM stock_policy WHERE id=?",Integer.class,foreign));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM stock_policy",Integer.class));
        mvc.perform(delete(ROOT+"/"+own).with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(post(ROOT).with(authentication(auth())).contentType("application/json").content(body(" OWN ",5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.warehouseCode").value("OWN"));
        assertEquals("爱培科",jdbc.queryForObject("SELECT factory_scope FROM audit_log WHERE operator_id=? AND resource_type='stock_policy' ORDER BY id DESC LIMIT 1",String.class,user));
    }

    @Test void mixedImportDeniedAtomicallyAndOwnImportAuditIsReadable() throws Exception {
        var mixed=file(new String[]{"OWN",material,"8","80"},new String[]{"OTHER",material,"9","90"});
        mvc.perform(multipart(ROOT+"/import/preview").file(mixed).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.errorRows").value(0));
        mvc.perform(multipart(ROOT+"/import").file(mixed).with(authentication(auth()))).andExpect(status().isForbidden());
        assertEquals(2,jdbc.queryForObject("SELECT safety_stock_qty FROM stock_policy WHERE id=?",Integer.class,own));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE operator_id=? AND resource_type='stock_policy'",Integer.class,user));
        mvc.perform(multipart(ROOT+"/import").file(file(new String[]{"OWN",material,"3","30"})).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updated").value(1));
        assertEquals(List.of("爱培科","爱培科"),jdbc.queryForList("SELECT factory_scope FROM audit_log WHERE operator_id=? AND resource_type='stock_policy' ORDER BY id",String.class,user));
        mvc.perform(get("/api/v1/audit-logs").param("resourceType","stock_policy").param("resourceId","OWN|"+material).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠')",user);
        mvc.perform(multipart(ROOT+"/import").file(mixed).with(authentication(auth()))).andExpect(status().isOk());
        assertEquals("ALL",jdbc.queryForObject("SELECT factory_scope FROM audit_log WHERE operator_id=? AND resource_type='stock_policy' ORDER BY id DESC LIMIT 1",String.class,user));
    }

    @Test void disabledMappingStillOwnsExistingPoliciesButCannotBeUsedForNewOnes() throws Exception {
        jdbc.update("UPDATE factory_warehouse_mapping SET enabled=0 WHERE warehouse_code='OWN'");
        mvc.perform(get(ROOT).with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get(ROOT+"/warehouses").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put(ROOT+"/"+own).with(authentication(auth())).contentType("application/json").content(body("OWN",3)))
                .andExpect(status().isOk());
        mvc.perform(delete(ROOT+"/"+own).with(authentication(auth()))).andExpect(status().isOk());
        mvc.perform(post(ROOT).with(authentication(auth())).contentType("application/json").content(body("OWN",3)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings={"OWN|X", "NO_MAPPING|X"})
    void delimiterInWarehouseNeverChangesAuditFactory(String warehouse) throws Exception {
        jdbc.update("UPDATE factory_warehouse_mapping SET warehouse_code=? WHERE factory_code='爱培科'",warehouse);
        jdbc.update("UPDATE factory_warehouse_mapping SET warehouse_code='OWN' WHERE factory_code='永惠'");
        jdbc.update("DELETE FROM stock_policy WHERE id=?",own);
        mvc.perform(post(ROOT).with(authentication(auth())).contentType("application/json").content(body(warehouse,3)))
                .andExpect(status().isOk());
        Long id=jdbc.queryForObject("SELECT id FROM stock_policy WHERE warehouse_code=? AND material_code=?",Long.class,warehouse,material);
        mvc.perform(put(ROOT+"/"+id).with(authentication(auth())).contentType("application/json").content(body("OWN",4)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.warehouseCode").value(warehouse));
        mvc.perform(multipart(ROOT+"/import").file(file(new String[]{warehouse,material,"5","50"})).with(authentication(auth())))
                .andExpect(status().isOk());
        mvc.perform(delete(ROOT+"/"+id).with(authentication(auth()))).andExpect(status().isOk());
        // Also cover the import INSERT path, alongside the preceding import UPDATE path.
        mvc.perform(multipart(ROOT+"/import").file(file(new String[]{warehouse,material,"6","60"})).with(authentication(auth())))
                .andExpect(status().isOk());
        assertEquals(Collections.nCopies(7,"爱培科"),jdbc.queryForList(
                "SELECT factory_scope FROM audit_log WHERE operator_id=? AND resource_type='stock_policy' ORDER BY id",String.class,user));
        String resource=warehouse+"|"+material;
        mvc.perform(get("/api/v1/audit-logs").param("resourceType","stock_policy").param("resourceId",resource).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(5));
        jdbc.update("UPDATE user_factory_scope SET factory_code='永惠' WHERE user_id=?",user);
        mvc.perform(get("/api/v1/audit-logs").param("resourceType","stock_policy").param("resourceId",resource).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test void twoFactoryGrantsDoNotExposeUnknownWarehousesAndAllRoleDoes() throws Exception {
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠')",user);
        mvc.perform(get(ROOT+"/page").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
        grantRole("ALL",null);
        mvc.perform(get(ROOT+"/page").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(4));
        mvc.perform(put(ROOT+"/"+unknown).with(authentication(auth())).contentType("application/json").content(body("OWN",4)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.warehouseCode").value("UNKNOWN"));
        assertEquals("ALL",jdbc.queryForObject("SELECT factory_scope FROM audit_log WHERE operator_id=? AND resource_type='stock_policy' ORDER BY id DESC LIMIT 1",String.class,user));
        mvc.perform(delete(ROOT+"/"+unknown).with(authentication(auth()))).andExpect(status().isOk());
    }

    @Test void revocationAndInactiveRolesTakeEffectOnNextRequest() throws Exception {
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?",user);
        mvc.perform(get(ROOT).with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get(ROOT+"/page").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(ROOT+"/warehouses").with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete(ROOT+"/"+own).with(authentication(auth()))).andExpect(status().isForbidden());
        grantRole("FACTORY","永惠");
        mvc.perform(get(ROOT).with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(foreign));
        jdbc.update("UPDATE role SET status='INACTIVE' WHERE id=?",role);
        mvc.perform(get(ROOT).with(authentication(auth()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void functionalPermissionsRemainIndependentAndPagingOverflowIsRejected() throws Exception {
        TestSecurityContextHolder.clearContext();
        mvc.perform(get(ROOT).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).with(authentication(auth("mrp_stock_policy:manage")))).andExpect(status().isForbidden());
        mvc.perform(delete(ROOT+"/"+own).with(authentication(auth("mrp_stock_policy:view")))).andExpect(status().isForbidden());
        mvc.perform(get(ROOT+"/page").param("page","2147483647").param("pageSize","100").with(authentication(auth())))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(ROOT+"/9223372036854775807").with(authentication(auth()))).andExpect(status().isBadRequest());
    }

    private void grantRole(String type,String value) {
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)",user,role);
        jdbc.update("INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(?,?,?)",role,type,value);
    }
    private UsernamePasswordAuthenticationToken auth(String... permissions) {
        return new UsernamePasswordAuthenticationToken(new com.mrp.common.security.CurrentUser.UserPrincipal(user,"stock-test"),null,
                Arrays.stream(permissions.length==0?PERMISSIONS:permissions).map(SimpleGrantedAuthority::new).toList());
    }
    private long insert(String sql) {
        var keys=new GeneratedKeyHolder(); jdbc.update(c -> c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS),keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private long policy(String warehouse) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c -> {var statement=c.prepareStatement("INSERT INTO stock_policy(warehouse_code,material_code,safety_stock_qty,warning_stock_qty) VALUES(?,?,2,20)",Statement.RETURN_GENERATED_KEYS);
            statement.setString(1,warehouse); statement.setString(2,material); return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private String body(String warehouse,int safety) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("warehouseCode",warehouse,"materialCode",material,"safetyStockQty",safety,"warningStockQty",80));
    }
    private MockMultipartFile file(String[]... rows) throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(templates.generateStockPolicyTemplate())); var out=new ByteArrayOutputStream()) {
            var sheet=book.getSheetAt(0);
            for(int i=sheet.getLastRowNum();i>=2;i--) sheet.removeRow(sheet.getRow(i));
            for(int i=0;i<rows.length;i++) {var row=sheet.createRow(i+2); for(int j=0;j<rows[i].length;j++) row.createCell(j).setCellValue(rows[i][j]);}
            book.write(out);return new MockMultipartFile("file","scope.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
}
