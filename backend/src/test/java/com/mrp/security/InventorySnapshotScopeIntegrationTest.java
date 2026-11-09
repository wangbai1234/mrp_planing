package com.mrp.security;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.inventory.service.InventorySnapshotAccessService;
import com.mrp.mrp.service.InventoryNormalizationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Statement;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL, SQL pagination, and the HTTP security chain; no material/category/BOM mutations. */
@AutoConfigureMockMvc
class InventorySnapshotScopeIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired InventorySnapshotAccessService access;
    @Autowired InventoryNormalizationService normalization;
    final List<Long> snapshots=new ArrayList<>(), users=new ArrayList<>(), roles=new ArrayList<>();
    String marker, a, b, wa, wb;
    Long user, other;

    @BeforeEach void setup() {
        marker="scope-"+UUID.randomUUID().toString().substring(0,8);
        a=marker+"-A"; b=marker+"-B"; wa=marker+"-WA"; wb=marker+"-WB";
        user=user(); other=user();
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,?)",user,a);
        jdbc.update("INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code) VALUES(?,?),(?,?)",a,wa,b,wb);
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        for (Long id:snapshots) {
            jdbc.update("DELETE FROM mrp_inventory_balance WHERE inventory_snapshot_id=?",id);
            jdbc.update("DELETE FROM inventory_item WHERE snapshot_id=?",id);
            jdbc.update("DELETE FROM inventory_detail WHERE snapshot_id=?",id);
            jdbc.update("DELETE FROM inventory_snapshot WHERE id=?",id);
        }
        jdbc.update("DELETE FROM factory_warehouse_mapping WHERE factory_code IN (?,?)",a,b);
        for(Long id:users) {
            jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?",id);
            jdbc.update("DELETE FROM user_role WHERE user_id=?",id);
            jdbc.update("DELETE FROM user WHERE id=?",id);
        }
        for(Long id:roles) {
            jdbc.update("DELETE FROM role_data_scope WHERE role_id=?",id);
            jdbc.update("DELETE FROM role WHERE id=?",id);
        }
    }

    @Test void mixedSnapshotRequiresAllFactoriesBeforeReadOrNormalize() throws Exception {
        long own=snapshot(null,wa), foreign=snapshot(null,wb), mixed=snapshot(null,wa,wb);
        for (long id:List.of(foreign,mixed)) {
            call(user,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isForbidden());
            call(user,get("/api/v1/mrp/inventory/balances/"+id)).andExpect(status().isForbidden());
            call(user,normalize(id,a)).andExpect(status().isForbidden());
            assertEquals(0,countBalances(id));
        }
        call(user,get("/api/v1/inventory-snapshots/"+own)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].warehouseCode").value(wa));
        call(user,normalize(own,a)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].factoryCode").value(a));
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,?)",user,b);
        call(user,normalize(mixed,a)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        call(user,get("/api/v1/mrp/inventory/balances/"+mixed)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test void detailSearchFindsLaterPagesAndKeepsWholeSnapshotAuthorization() throws Exception {
        long own=snapshot(null,wa), mixed=snapshot(null,wa,wb);
        for (int i=0;i<25;i++)
            jdbc.update("INSERT INTO inventory_item(snapshot_id,inventory_category,material_id,warehouse_code,inventory_quality,shipping_available_qty) VALUES(?,'TEST',? ,?,'GOOD',10)",own,marker+"-"+i,wa);
        jdbc.update("INSERT INTO inventory_item(snapshot_id,inventory_category,material_id,warehouse_code,inventory_quality,shipping_available_qty) VALUES(?,'TEST',? ,?,'GOOD',10)",own,marker+"-target",wa);
        call(user,get("/api/v1/inventory-snapshots/"+own).param("page","1").param("pageSize","20")
                .param("keyword","-target"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].materialId").value(marker+"-target"));
        call(user,get("/api/v1/inventory-snapshots/"+own).param("keyword",wa))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(27));
        call(user,get("/api/v1/inventory-snapshots/"+mixed).param("keyword",wa))
                .andExpect(status().isForbidden());
    }

    @Test void listCountAndWarehouseFilterExcludeInvisibleSnapshotsBeforePagination() throws Exception {
        long first=snapshot(null,wa), second=snapshot(null,wa);
        snapshot(null,wb); snapshot(null,wa,wb); snapshot(null,"unmapped"); snapshot(null);
        call(user,get("/api/v1/inventory-snapshots/page").param("keyword",marker).param("pageSize","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(second));
        call(user,get("/api/v1/inventory-snapshots/page").param("keyword",marker).param("pageSize","1").param("page","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(first));
        call(user,get("/api/v1/mrp/inventory/snapshots").param("warehouseCode",wa))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        call(user,get("/api/v1/mrp/inventory/snapshots").param("warehouseCode",wb))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        call(other,get("/api/v1/inventory-snapshots"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void activeRoleScopesWorkAndDisabledRolesCannotGrantAccess() throws Exception {
        long own=snapshot(null,wa), mixed=snapshot(null,wa,wb), empty=snapshot(null), unknown=snapshot(null,"unmapped");
        long role=role(other,"FACTORY",a);
        call(other,get("/api/v1/inventory-snapshots/"+own)).andExpect(status().isOk());
        call(other,get("/api/v1/inventory-snapshots/"+mixed)).andExpect(status().isForbidden());
        jdbc.update("UPDATE role_data_scope SET scope_type='ALL' WHERE role_id=?",role);
        for(long id:List.of(own,mixed,empty,unknown))
            call(other,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isOk());
        jdbc.update("UPDATE role SET status='DISABLED' WHERE id=?",role);
        for(long id:List.of(own,mixed,empty,unknown))
            call(other,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isForbidden());
    }

    @Test void headerLegacyAndExistingBalanceFactoriesCannotBeHiddenByItems() throws Exception {
        long header=snapshot(wb,wa), legacy=snapshot(null,wa), balance=snapshot(null,wa);
        jdbc.update("INSERT INTO inventory_detail(snapshot_id,factory_code,material_id,warehouse_code,quantity) VALUES(?,?,?, ?,1)",legacy,b,marker,wb);
        jdbc.update("INSERT INTO mrp_inventory_balance(inventory_snapshot_id,factory_code,inventory_org,warehouse_scope,material_code,available_quantity) VALUES(?,?,?,?,?,7)",balance,b,wb,wb,marker);
        for(long id:List.of(header,legacy,balance)) {
            call(user,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isForbidden());
            call(user,get("/api/v1/mrp/inventory/balances/"+id)).andExpect(status().isForbidden());
            call(user,normalize(id,a)).andExpect(status().isForbidden());
        }
        assertEquals(1,countBalances(balance));
        assertEquals(7,jdbc.queryForObject("SELECT available_quantity FROM mrp_inventory_balance WHERE inventory_snapshot_id=?",Integer.class,balance));
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,?)",user,b);
        for(long id:List.of(header,legacy,balance))
            call(user,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isOk());
    }

    @Test void emptyUnmappedAndDisabledWarehouseMappingsFailClosed() throws Exception {
        long empty=snapshot(null), unknown=snapshot(null,"unmapped"), blank=snapshot(null," "), disabled=snapshot(null,wa);
        jdbc.update("UPDATE factory_warehouse_mapping SET enabled=0 WHERE warehouse_code=?",wa);
        for(long id:List.of(empty,unknown,blank,disabled)) {
            call(user,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isForbidden());
            call(user,normalize(id,a)).andExpect(status().isForbidden());
        }
        // Malformed legacy mapping must not assign an unknown/blank warehouse to a factory.
        jdbc.update("UPDATE factory_warehouse_mapping SET enabled=1,warehouse_code=' ' WHERE warehouse_code=?",wa);
        call(user,get("/api/v1/inventory-snapshots/"+blank)).andExpect(status().isForbidden());
    }

    @Test void legacyFactoryOnlySnapshotRemainsScoped() throws Exception {
        long id=snapshot(null);
        jdbc.update("INSERT INTO inventory_detail(snapshot_id,factory_code,material_id,warehouse_code,quantity) VALUES(?,?,?,?,1)",id,a,marker,wa);
        call(user,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isOk());
        call(other,get("/api/v1/inventory-snapshots/"+id)).andExpect(status().isForbidden());
    }

    @Test void missingSnapshotInvalidPagesAndUnauthenticatedServiceCallsAreRejected() throws Exception {
        long id=snapshot(null,wa);
        call(user,get("/api/v1/inventory-snapshots/9223372036854775807")).andExpect(status().isNotFound());
        for(String[] args:List.of(new String[]{"0","20"},new String[]{"1","0"},new String[]{"1","101"},new String[]{"2147483647","100"})) {
            call(user,get("/api/v1/inventory-snapshots/page").param("page",args[0]).param("pageSize",args[1])).andExpect(status().isBadRequest());
            call(user,get("/api/v1/inventory-snapshots/"+id).param("page",args[0]).param("pageSize",args[1])).andExpect(status().isBadRequest());
        }
        SecurityContextHolder.clearContext();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->access.items(id));
        assertTrue(access.list(null).isEmpty());
    }

    @Test void backgroundNormalizationRemainsIndependentAndPreservesQuality() {
        long id=snapshot(null,wa,wb);
        jdbc.update("UPDATE inventory_item SET inventory_quality='DEFECTIVE' WHERE snapshot_id=? AND warehouse_code=?",id,wb);
        SecurityContextHolder.clearContext();
        var result=normalization.normalizeSnapshot(id,"ALL","ignored");
        assertEquals(2,result.size());
        assertTrue(result.stream().anyMatch(x->a.equals(x.getFactoryCode()) && Boolean.TRUE.equals(x.getIncludedFlag())));
        assertTrue(result.stream().anyMatch(x->b.equals(x.getFactoryCode()) && "DEFECTIVE".equals(x.getInventoryQuality()) && !x.getIncludedFlag()));
    }

    private long snapshot(String header, String... warehouses) {
        long id=insert("INSERT INTO inventory_snapshot(snapshot_date,warehouse_code,status) VALUES('2026-10-16',?,?)",header,marker); snapshots.add(id);
        for(String warehouse:warehouses)
            jdbc.update("INSERT INTO inventory_item(snapshot_id,inventory_category,material_id,supplier_name,warehouse_code,inventory_quality,shipping_available_qty) VALUES(?,'TEST',?,'TEST',?,'GOOD',10)",id,marker,warehouse);
        return id;
    }
    private long role(Long actor,String type,String value) {
        long id=insert("INSERT INTO role(code,name,status) VALUES(?,?,'ACTIVE')",marker,marker);roles.add(id);
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)",actor,id);
        jdbc.update("INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(?,?,?)",id,type,value);return id;
    }
    private long user() { long id=insert("INSERT INTO user(username,password_hash) VALUES(?,'test-only')",UUID.randomUUID().toString());users.add(id);return id; }
    private long insert(String sql,Object... args) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private int countBalances(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM mrp_inventory_balance WHERE inventory_snapshot_id=?",Integer.class,id); }
    private MockHttpServletRequestBuilder normalize(long id,String factory) {
        return post("/api/v1/mrp/inventory/normalize/"+id).param("factoryCode",factory).param("inventoryOrg",wa);
    }
    private org.springframework.test.web.servlet.ResultActions call(Long actor,MockHttpServletRequestBuilder request) throws Exception {
        var auth=new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(actor,"scope-test"),null,
                List.of("inventory:view","mrp_workspace:view","mrp_batch:list","mrp_inventory:allocate").stream().map(SimpleGrantedAuthority::new).toList());
        return mvc.perform(request.with(authentication(auth)));
    }
}
