package com.mrp.mrp;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.service.PlanningFactoryAccessService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.sql.Statement;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full MVC security chain, actual factory SQL and real MySQL; no production data. */
@AutoConfigureMockMvc
class PlanningFactoryAccessIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlanningFactoryAccessService access;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    Long user, own, foreign, combined;
    @BeforeEach void setup() {
        user=insert("INSERT INTO user(username,password_hash) VALUES(?,'test-only')","scope-"+UUID.randomUUID());
        own=batch("爱培科"); foreign=batch("永惠"); combined=batch("ALL");
        // Frozen scope is authoritative even if a historical header has a different factory.
        jdbc.update("INSERT INTO mrp_batch_factory_scope(batch_id,factory_code,warehouse_code) VALUES(?,'爱培科','TEST-WH')",own);
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')",user);
    }
    @AfterEach void logout(){
        SecurityContextHolder.clearContext();
        for(Long id:List.of(own,foreign,combined)) {
            jdbc.update("DELETE FROM mrp_daily_plan_override WHERE batch_id=?",id);
            jdbc.update("DELETE FROM mrp_decision_issue WHERE batch_id=?",id);
            jdbc.update("DELETE FROM mrp_inventory_pool WHERE batch_id=?",id);
            jdbc.update("DELETE FROM mrp_calc_run WHERE batch_id=?",id);
            jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?",id);
        }
        var roles=jdbc.queryForList("SELECT role_id FROM user_role WHERE user_id=?",Long.class,user);
        jdbc.update("DELETE FROM user_role WHERE user_id=?",user);
        for(Long role:roles){jdbc.update("DELETE FROM role_data_scope WHERE role_id=?",role);jdbc.update("DELETE FROM role WHERE id=?",role);}
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?",user);
        jdbc.update("DELETE FROM user WHERE id=?",user);
    }
    private UsernamePasswordAuthenticationToken auth(){return new UsernamePasswordAuthenticationToken(
        new CurrentUser.UserPrincipal(user,"scoped"),null,List.of("mrp_batch:list","mrp_batch:create","mrp_batch:calculate","mrp_batch:precheck","mrp_batch:publish","mrp_plan:adjust","mrp_execution:confirm","mrp_inventory:allocate","mrp_decision:manage").stream().map(SimpleGrantedAuthority::new).toList());}
    private ResultActions call(MockHttpServletRequestBuilder request)throws Exception{return mvc.perform(request.with(authentication(auth())));}
    @Test void directGrantWithoutRoleWorksAndListsCountsAndCurrentExcludeForeignFactories()throws Exception {
        SecurityContextHolder.getContext().setAuthentication(auth());
        access.requireBatch(own);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->access.requireBatch(combined));
        assertEquals(1,access.countVisible(null,null));
        assertEquals(List.of(own),access.visible(null,null,false,0L,20).stream().map(b->b.getId()).toList());
        call(get("/api/v1/mrp/batches/page")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        call(get("/api/v1/mrp/batches/current")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(own));
        call(get("/api/v1/mrp/batches")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        call(get("/api/v1/mrp/batches/{id}",own)).andExpect(status().isOk());
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?",user);
        call(get("/api/v1/mrp/batches/page")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        call(get("/api/v1/mrp/batches/current")).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
    }
    @Test void allCombinedBatchReadsAndMutationsAreDeniedBeforeBusinessLogic()throws Exception {
        for(Long id:List.of(foreign,combined)) {
            for(String suffix:List.of("","/summary","/daily-plans","/plans/weekly","/plans/monthly","/plans/conservation","/execution","/requirements","/terminal-demands","/overrides","/unallocated","/unallocated/page","/capacity-review","/rolling-grid"))
                call(get("/api/v1/mrp/batches/"+id+suffix)).andExpect(status().isForbidden());
            call(get("/api/v1/mrp/plans/"+id+"/daily-grid")).andExpect(status().isForbidden());
            call(get("/api/v1/mrp/batches/"+id+"/capacity-loads").param("startDate","2026-10-01").param("endDate","2026-10-02")).andExpect(status().isForbidden());
            for(String suffix:List.of("cancel","precheck","calculate","publish","revise","transition","execution/lock","linked-transfers/preview","daily-transfers","overrides","distribute"))
                call(post("/api/v1/mrp/batches/"+id+"/"+suffix).contentType("application/json").content("{}")).andExpect(status().isForbidden());
            assertEquals("READY",jdbc.queryForObject("SELECT status FROM mrp_calc_batch WHERE id=?",String.class,id));
        }
        call(post("/api/v1/mrp/batches").param("factoryCode","ALL")).andExpect(status().isForbidden());
    }
    @Test void indirectPoolIssueOverrideAndForeignRunCannotBypassBatchScope()throws Exception {
        long pool=insert("INSERT INTO mrp_inventory_pool(batch_id,snapshot_id,factory_code,inventory_org,material_code) VALUES(?,1,'永惠','test','TEST')",foreign);
        long issue=insert("INSERT INTO mrp_decision_issue(batch_id,issue_key,issue_type,severity,scope_type,scope_key,status) VALUES(?,'scope','TEST','WARNING','BATCH','test','OPEN')",foreign);
        long override=insert("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,reason) VALUES(?,'永惠','ROOT','LEAF','PCBA','SELF_MADE','test','2026-10-01',1,'test')",foreign);
        long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')",foreign);
        call(get("/api/v1/mrp/inventory/pools/detail/"+pool)).andExpect(status().isForbidden());
        call(get("/api/v1/mrp/inventory/allocations/pool/"+pool)).andExpect(status().isForbidden());
        call(post("/api/v1/mrp/inventory/allocations/preview").param("poolId",Long.toString(pool))).andExpect(status().isMethodNotAllowed());
        call(post("/api/v1/mrp/inventory/allocations/commit").contentType("application/json").content("{\"poolId\":"+pool+",\"grossQty\":100}")).andExpect(status().isMethodNotAllowed());
        call(post("/api/v1/mrp/batches/issues/"+issue+"/resolve")).andExpect(status().isForbidden());
        call(post("/api/v1/mrp/overrides/"+override+"/restore-auto")).andExpect(status().isForbidden());
        call(get("/api/v1/mrp/inventory/stock-risks/"+own).param("runId",Long.toString(run))).andExpect(status().isForbidden());
        call(post("/api/v1/mrp/inventory/pools").param("batchId",own.toString()).param("snapshotId","1").param("factoryCode","永惠")).andExpect(status().isNotFound());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_inventory_allocation WHERE pool_id=?",Integer.class,pool));
        assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM mrp_decision_issue WHERE id=?",String.class,issue));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_override WHERE id=?",Integer.class,override));
    }
    @Test void activeRoleGrantWorksAndInactiveRoleNeverGrantsData()throws Exception {
        long role=insert("INSERT INTO role(code,name,status) VALUES(?, 'scope test','ACTIVE')","scope-"+UUID.randomUUID().toString().substring(0,8));
        jdbc.update("INSERT INTO user_role(user_id,role_id) VALUES(?,?)",user,role);
        jdbc.update("INSERT INTO role_data_scope(role_id,scope_type,scope_value) VALUES(?,'ALL',NULL)",role);
        call(get("/api/v1/mrp/batches/{id}",combined)).andExpect(status().isOk());
        jdbc.update("UPDATE role SET status='DISABLED' WHERE id=?",role);
        call(get("/api/v1/mrp/batches/{id}",combined)).andExpect(status().isForbidden());
        call(get("/api/v1/mrp/batches/{id}",own)).andExpect(status().isOk());
    }
    @Test void twoDirectFactoryGrantsAllowCombinedAndRunOwnershipIsStillRequired()throws Exception {
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠')",user);
        call(get("/api/v1/mrp/batches/{id}",combined)).andExpect(status().isOk());
        long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')",foreign);
        call(get("/api/v1/mrp/inventory/stock-risks/"+own).param("runId",Long.toString(run))).andExpect(status().isForbidden());
    }
    private Long batch(String factory){return insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES(?,'READY',1,'v4-rolling-business-weeks')",factory);}
    private Long insert(String sql,Object...args){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
}
