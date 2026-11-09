package com.mrp.mrp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.task.repository.CalcTaskMapper;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Dedicated Testcontainers MySQL data: real engine, mapped SQL, HTTP gates and both workers. */
@AutoConfigureMockMvc
class TwoWorkbenchRealEngineFlowIntegrationTest extends BaseIntegrationTest {
    private static final String FACTORY = "爱培科";
    private static final LocalDate MONTH = LocalDate.of(2026, 10, 1);
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired CalcTaskMapper tasks;
    @Autowired MrpTaskWorkerService worker;
    @Autowired MrpLifecycleWorkerService lifecycleWorker;
    @SpyBean DailyPlanningService daily;
    @Autowired BomSnapshotService bom;

    @Test void confirmedMachineRunGeneratesShortageAndPublishesIndependentSnapshot() throws Exception {
        String token = UUID.randomUUID().toString().substring(0, 8);
        String machine = "E2E-M-" + token, component = "E2E-C-" + token;
        String warehouse = "E2E-W-" + token, category = "E2E-CAT-" + token;
        long user = insert("INSERT INTO user(username,password_hash) VALUES(?, 'unused')", "e2e-planner-" + token);
        long materialUser = insert("INSERT INTO user(username,password_hash) VALUES(?, 'unused')", "e2e-material-" + token);
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,?),(?,?)", user, FACTORY, materialUser, FACTORY);
        long categoryId = insert("INSERT INTO material_category(code,name) VALUES(?,?)", category, category);
        for (String code : List.of(machine, component))
            jdbc.update("INSERT INTO material(material_code,material_name,material_category_id) VALUES(?,?,?)", code, code, categoryId);
        jdbc.update("INSERT INTO bom_detail(parent_code,child_code,parent_qty,child_qty,version,org_name) VALUES(?,?,1,2,'1',?)",
                machine, component, com.mrp.bom.service.BomService.PLANNING_ORGANIZATION);
        String bomChecksum = bom.loadCurrentBom().checksum();
        long forecast = insert("INSERT INTO forecast_version(version_no,status) VALUES(1,'IMPORTED')");
        jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,plan_month,forecast_qty) VALUES(?,?,?,?,10)",
                forecast, FACTORY, machine, MONTH);
        long snapshot = insert("INSERT INTO inventory_snapshot(snapshot_date,status,warehouse_code) VALUES(?,'IMPORTED',?)", MONTH, warehouse);
        long roleVersion = insert("INSERT INTO mrp_category_role_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'PUBLISHED' FROM mrp_category_role_version");
        jdbc.update("INSERT INTO mrp_category_role_detail(version_id,category_code,role,enabled) VALUES(?,?,'FINISHED_GOOD',1)", roleVersion, category);
        long policyVersion = insert("INSERT INTO mrp_material_policy_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'PUBLISHED' FROM mrp_material_policy_version");
        for (String code : List.of(machine, component))
            jdbc.update("INSERT INTO mrp_material_policy_detail(version_id,material_code,supply_mode,explosion_policy) VALUES(?,?,'PURCHASED','EXPLODE')", policyVersion, code);
        long mappingVersion = insert("INSERT INTO factory_warehouse_mapping_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'PUBLISHED' FROM factory_warehouse_mapping_version");
        jdbc.update("INSERT INTO factory_warehouse_mapping_detail(version_id,factory_code,warehouse_code,enabled,is_good_warehouse) VALUES(?,?,?,?,1)",
                mappingVersion, FACTORY, warehouse, 1);
        long stockVersion = insert("INSERT INTO stock_policy_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'PUBLISHED' FROM stock_policy_version");
        for (String code : List.of(machine, component))
            jdbc.update("INSERT INTO stock_policy_detail(version_id,warehouse_code,material_code,safety_stock_qty,warning_stock_qty) VALUES(?,?,?,0,0)",
                    stockVersion, warehouse, code);
        long calendar = insert("INSERT INTO production_calendar_version(version_no,factory_code,status) SELECT COALESCE(MAX(version_no),0)+1,?,'PUBLISHED' FROM production_calendar_version", FACTORY);
        for (LocalDate date = LocalDate.of(2026, 9, 1); !date.isAfter(LocalDate.of(2026, 10, 31)); date = date.plusDays(1))
            jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,?,1,100,'台')",
                    calendar, FACTORY, date);
        for (LocalDate month : List.of(MONTH.minusMonths(1), MONTH)) {
            long weekVersion = insert("INSERT INTO business_week_version(version_no,business_month,status) SELECT COALESCE(MAX(version_no),0)+1,?,'PUBLISHED' FROM business_week_version WHERE business_month=?", month, month);
            for (int w = 0; w < 4; w++) {
                LocalDate start = month.plusDays(w * 7L);
                LocalDate end = w == 3 ? month.plusMonths(1).minusDays(1) : start.plusDays(6);
                jdbc.update("INSERT INTO business_week_detail(version_id,week_code,start_date,end_date,sort_no) VALUES(?,?,?,?,?)",
                        weekVersion, "W" + (w + 1), start, end, w + 1);
            }
        }
        long currentWeekVersion = jdbc.queryForObject("SELECT id FROM business_week_version WHERE business_month=? ORDER BY id DESC LIMIT 1",Long.class,MONTH);
        long batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,forecast_version_id,inventory_snapshot_id,bom_source_checksum,category_role_version_id,material_policy_version_id,production_calendar_version_id,factory_warehouse_mapping_version_id,stock_policy_version_id,input_checksum,created_by) VALUES(?,'DRAFT',1,?,?,?,?,?,?,?,?,?,?,?)",
                FACTORY, ProductionCapacityRules.VERSION, forecast, snapshot, bomChecksum, roleVersion, policyVersion, calendar, mappingVersion, stockVersion, "e2e-" + token, user);
        jdbc.update("UPDATE mrp_calc_batch SET business_week_version_id=? WHERE id=?",currentWeekVersion,batch);
        jdbc.update("INSERT INTO mrp_batch_factory_scope(batch_id,factory_code,warehouse_code,production_calendar_version_id,inventory_snapshot_id) VALUES(?,?,?,?,?)",
                batch, FACTORY, warehouse, calendar, snapshot);
        for (LocalDate month : List.of(MONTH.minusMonths(1), MONTH))
            jdbc.update("INSERT INTO mrp_batch_business_week_scope(batch_id,business_month,business_week_version_id) SELECT ?,business_month,id FROM business_week_version WHERE business_month=? ORDER BY id DESC LIMIT 1", batch, month);
        for (String code : List.of(machine, component)) {
            int stock = code.equals(machine) ? 2 : 1;
            jdbc.update("INSERT INTO mrp_inventory_balance(inventory_snapshot_id,factory_code,inventory_org,warehouse_scope,material_code,inventory_quality,available_quantity,included_flag) VALUES(?,?,?,?,?,'GOOD',?,1)", snapshot, FACTORY, warehouse, warehouse, code, stock);
            jdbc.update("INSERT INTO mrp_inventory_pool(batch_id,snapshot_id,factory_code,warehouse_code,inventory_org,material_code,available_qty,reserved_qty,allocated_qty) VALUES(?,?,?,?,?,?,?,0,0)", batch, snapshot, FACTORY, warehouse, warehouse, code, stock);
        }
        var planner = auth(user, "mrp_batch:precheck", "mrp_batch:calculate", "mrp_schedule:confirm", "mrp_workspace:view");
        var controller = auth(materialUser, "mrp_shortage:manage", "mrp_shortage:confirm", "mrp_shortage:view", "mrp_workspace:view", "mrp_batch:publish");
        mvc.perform(post("/api/v1/mrp/batches/{id}/precheck",batch).with(authentication(planner))).andExpect(status().isOk());
        assertEquals("READY_TO_CALCULATE", batches.selectById(batch).getStatus());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected machine distribution failure");
        }).when(daily).distribute(eq(batch),anyLong(),anyLong(),any(),any());
        try {
            long failedTask = json.readTree(mvc.perform(post("/api/v1/mrp/batches/{id}/calculate",batch)
                    .with(authentication(planner))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
            worker.pollAndExecute();
            assertEquals("FAILED", tasks.selectById(failedTask).getStatus());
            assertTrue(tasks.selectById(failedTask).getErrorMessage().contains("injected machine distribution failure"));
            assertEquals("READY_TO_CALCULATE", batches.selectById(batch).getStatus());
            assertNull(batches.selectById(batch).getActiveRunId());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_terminal_demand WHERE batch_id=?", Integer.class, batch));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id=?", Integer.class, batch));
        } finally {
            reset(daily);
        }
        mvc.perform(post("/api/v1/mrp/batches/{id}/calculate",batch).with(authentication(planner))).andExpect(status().isOk());
        worker.pollAndExecute();
        var scheduled = batches.selectById(batch);
        assertEquals("SCHEDULE_READY", scheduled.getStatus(), failure(batch));
        long run = scheduled.getActiveRunId();
        assertNotNull(scheduled.getActiveRunId());
        assertDecimal("8", sum("mrp_terminal_demand", "quantity", run, "terminal_type='MACHINE_OUTPUT'"));
        assertEquals(0, count(run, "mrp_terminal_demand", "terminal_type<>'MACHINE_OUTPUT'"));
        assertDecimal("8", sum("mrp_daily_plan_detail", "system_quantity", run, "terminal_type='MACHINE_OUTPUT'"));
        assertEquals(0, daily.getSourceMonthConservation(batch,run).mismatchCount);
        mvc.perform(post("/api/v1/mrp/batches/{id}/material-analysis",batch).with(authentication(controller))).andExpect(status().isBadRequest());
        String schedule = review(batch,planner).path("scheduleFingerprint").asText();
        mvc.perform(post("/api/v1/mrp/batches/{id}/schedule-confirmation",batch).with(authentication(planner))
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("fingerprint",schedule))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.schedule").value("CONFIRMED"));
        mvc.perform(post("/api/v1/mrp/batches/{id}/schedule-confirmation",batch).with(authentication(planner))
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("fingerprint",schedule))))
                .andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_workbench_review WHERE batch_id=? AND review_type='SCHEDULE'",Integer.class,batch));
        mvc.perform(post("/api/v1/mrp/batches/{id}/material-analysis",batch).with(authentication(planner))).andExpect(status().isForbidden());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected material distribution failure");
        }).when(daily).distributeMaterial(eq(batch),eq(run),anyLong(),any(),any());
        try {
            long failedTask = json.readTree(mvc.perform(post("/api/v1/mrp/batches/{id}/material-analysis",batch)
                    .with(authentication(controller))).andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();
            worker.pollAndExecute();
            assertEquals("FAILED", tasks.selectById(failedTask).getStatus());
            assertTrue(tasks.selectById(failedTask).getErrorMessage().contains("injected material distribution failure"));
            assertEquals("SCHEDULE_READY", batches.selectById(batch).getStatus());
            assertEquals(run, batches.selectById(batch).getActiveRunId());
            assertDecimal("8", sum("mrp_terminal_demand", "quantity", run, "terminal_type='MACHINE_OUTPUT'"));
            assertEquals(0, count(run, "mrp_terminal_demand", "terminal_type<>'MACHINE_OUTPUT'"));
            assertEquals(0, count(run, "mrp_daily_plan_detail", "terminal_type<>'MACHINE_OUTPUT'"));
        } finally {
            reset(daily);
        }
        mvc.perform(post("/api/v1/mrp/batches/{id}/material-analysis",batch).with(authentication(controller))).andExpect(status().isAccepted());
        worker.pollAndExecute();
        var analyzed = batches.selectById(batch);
        assertEquals("MATERIAL_REVIEW", analyzed.getStatus(), failure(batch));
        assertEquals(run, analyzed.getActiveRunId());
        assertDecimal("8", sum("mrp_terminal_demand", "quantity", run, "terminal_type='MACHINE_OUTPUT'"));
        assertDecimal("15", sum("mrp_terminal_demand", "quantity", run, "terminal_material_code='"+component+"'"));
        assertDecimal("15", sum("mrp_daily_plan_detail", "system_quantity", run, "terminal_material_code='"+component+"'"));
        assertEquals(0, daily.getSourceMonthConservation(batch,run).mismatchCount);
        assertEquals(0, count(run,"mrp_stock_risk","resolved_at IS NULL"));
        mvc.perform(post("/api/v1/mrp/batches/{id}/publish",batch).with(authentication(controller))).andExpect(status().isBadRequest());
        String material = review(batch,controller).path("materialFingerprint").asText();
        mvc.perform(post("/api/v1/mrp/batches/{id}/material-confirmation",batch).with(authentication(controller))
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("fingerprint",material))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.material").value("CONFIRMED"));
        mvc.perform(post("/api/v1/mrp/batches/{id}/material-confirmation",batch).with(authentication(controller))
                .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("fingerprint",material))))
                .andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_workbench_review WHERE batch_id=? AND review_type='MATERIAL'",Integer.class,batch));
        assertEquals("READY", batches.selectById(batch).getStatus());
        mvc.perform(post("/api/v1/mrp/batches/{id}/publish",batch).with(authentication(controller))
                .contentType("application/json").content("{}"))
                .andExpect(status().isAccepted());
        lifecycleWorker.poll();
        var publishedTask = tasks.selectLatestLifecycleForBatch(batch,MrpLifecycleTaskService.PUBLISH,"mrp-publish-");
        assertEquals("SUCCEEDED",publishedTask.getStatus(),publishedTask.getErrorMessage());
        long publishedId = publishedTask.getResultResourceId();
        var published = batches.selectById(publishedId);
        assertEquals("PUBLISHED", published.getStatus());
        assertEquals("SUPERSEDED", batches.selectById(batch).getStatus());
        assertNotEquals(run,published.getActiveRunId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_batch WHERE input_checksum=? AND status='PUBLISHED'",
                Integer.class, "e2e-" + token));
        assertDecimal("8", sum("mrp_terminal_demand","quantity",published.getActiveRunId(),"terminal_type='MACHINE_OUTPUT'"));
        assertDecimal("15", sum("mrp_terminal_demand","quantity",published.getActiveRunId(),"terminal_material_code='"+component+"'"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_workbench_review WHERE batch_id=? AND run_id=?",Integer.class,publishedId,published.getActiveRunId()));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(DISTINCT operator_id) FROM mrp_workbench_review WHERE batch_id=?",Integer.class,publishedId));
        assertEquals(0,daily.getSourceMonthConservation(publishedId,published.getActiveRunId()).mismatchCount);

        // A later calendar revision creates a working copy; the published facts and inputs stay readable.
        jdbc.update("INSERT INTO mrp_production_calendar(factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,1,120,'台')",
                FACTORY, MONTH);
        for (LocalDate month : List.of(MONTH.minusMonths(1), MONTH))
            jdbc.update("INSERT INTO business_week(business_month,week_code,start_date,end_date,sort_no) VALUES(?,'W1',?,?,1)",
                    month, month, month.plusMonths(1).minusDays(1));
        String publishedInput = published.getInputChecksum();
        String publishedResult = published.getResultChecksum();
        long publishedRun = published.getActiveRunId();
        mvc.perform(post("/api/v1/mrp/batches/{id}/revise",publishedId).with(authentication(controller)))
                .andExpect(status().isAccepted());
        lifecycleWorker.poll();
        var revisionTask = tasks.selectLatestLifecycleForBatch(publishedId,MrpLifecycleTaskService.REVISION,"mrp-revision-");
        assertEquals("SUCCEEDED",revisionTask.getStatus(),revisionTask.getErrorMessage());
        var revision = batches.selectById(revisionTask.getResultResourceId());
        assertEquals("DRAFT",revision.getStatus());
        assertEquals(publishedId,revision.getRevisionOfBatchId());
        assertNotEquals(published.getProductionCalendarVersionId(),revision.getProductionCalendarVersionId());
        assertEquals(published.getForecastVersionId(),revision.getForecastVersionId());
        assertEquals(published.getInventorySnapshotId(),revision.getInventorySnapshotId());
        assertEquals(published.getFactoryWarehouseMappingVersionId(),revision.getFactoryWarehouseMappingVersionId());
        assertEquals(published.getBomSourceChecksum(),revision.getBomSourceChecksum());
        var unchanged = batches.selectById(publishedId);
        assertEquals("PUBLISHED",unchanged.getStatus());
        assertEquals(publishedInput,unchanged.getInputChecksum());
        assertEquals(publishedResult,unchanged.getResultChecksum());
        assertEquals(publishedRun,unchanged.getActiveRunId());
        assertDecimal("8",sum("mrp_terminal_demand","quantity",publishedRun,"terminal_type='MACHINE_OUTPUT'"));
    }

    private UsernamePasswordAuthenticationToken auth(long user,String... permissions) {
        return new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(user,"e2e"),null,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }
    private JsonNode review(long batch,UsernamePasswordAuthenticationToken authentication) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/mrp/batches/{id}/reviews",batch).with(authentication(authentication)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private int count(long run,String table,String filter) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE run_id=? AND "+filter,Integer.class,run);
    }
    private BigDecimal sum(String table,String column,long run,String filter) {
        return jdbc.queryForObject("SELECT COALESCE(SUM("+column+"),0) FROM "+table+" WHERE run_id=? AND "+filter,BigDecimal.class,run);
    }
    private void assertDecimal(String expected,BigDecimal actual) {assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    private String failure(long batch) {
        return jdbc.queryForList("SELECT status,error_message FROM calc_task WHERE input_payload LIKE ? ORDER BY id DESC LIMIT 2","%\"batchId\":"+batch+"%").toString();
    }
    private long insert(String sql,Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            return statement;
        },keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
