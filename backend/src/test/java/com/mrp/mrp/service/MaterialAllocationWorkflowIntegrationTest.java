package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.MrpCalcRun;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDailyPlanDetailMapper;
import com.mrp.task.domain.CalcTask;
import com.mrp.task.repository.CalcTaskMapper;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real SQL for allocation, review, task and stage handoff; calculation engine is a fixture. */
@Transactional
class MaterialAllocationWorkflowIntegrationTest extends BaseIntegrationTest {
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired MrpDailyPlanDetailMapper dailyFacts;
    @Autowired MrpWorkbenchReviewService reviews;
    @Autowired InventoryNormalizationService inventory;
    @Autowired BatchOrchestratorService orchestrator;
    @Autowired MrpTaskWorkerService worker;
    @Autowired CalcTaskMapper tasks;
    @MockBean MrpCalculationService calculations;
    @MockBean DailyPlanningService daily;
    @MockBean BomSnapshotService bom;
    @MockBean CategoryRoleVersionService roles;
    @MockBean MaterialPolicyVersionService policies;

    @Test void allocationChangeInvalidatesReviewAndWorkerRecalculatesSameRun() {
        long user=insert("INSERT INTO user(username,password_hash) VALUES('material-flow-test','unused')");
        long forecast=insert("INSERT INTO forecast_version(version_no) VALUES(987654)");
        long mapping=insert("INSERT INTO factory_warehouse_mapping_version(version_no,status) VALUES(987654,'PUBLISHED')");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(user,"material-flow-test"),null,List.of()));
        try {
            jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'爱培科')",user);
            jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,plan_month,forecast_qty) VALUES(?,'爱培科','ROOT','2026-10-01',10)",forecast);
            jdbc.update("INSERT INTO factory_warehouse_mapping_detail(version_id,factory_code,warehouse_code,is_good_warehouse,enabled) VALUES(?,'爱培科','W-FLOW',1,1)",mapping);
            long batch=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,forecast_version_id,factory_warehouse_mapping_version_id,production_calendar_version_id,input_checksum) VALUES('爱培科','READY',1,'v5-shifted-business-weeks',?,?,1,'flow-checksum')",forecast,mapping);
            long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'COMPLETED')",batch);
            jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",run,batch);
            jdbc.update("INSERT INTO mrp_batch_factory_scope(batch_id,factory_code,warehouse_code) VALUES(?,'爱培科','W-FLOW')",batch);
            long pool=insert("INSERT INTO mrp_inventory_pool(batch_id,snapshot_id,factory_code,warehouse_code,inventory_org,material_code,available_qty,reserved_qty,allocated_qty) VALUES(?,1,'爱培科','W-FLOW','W-FLOW','PART',10,0,5)",batch);
            long allocation=insert("INSERT INTO mrp_inventory_allocation(pool_id,demand_key,root_material_code,source_month,path_hash,gross_qty,allocated_qty,allocation_type) VALUES(?,'ROOT|PART|2026-10-01','ROOT','2026-10-01','ROOT:PART',10,5,'AUTO_PRIORITY')",pool);
            jdbc.update("INSERT INTO mrp_requirement_detail(run_id,batch_id,factory_code,root_material_code,source_month,current_material_code,level_no,path_hash,gross_qty,inventory_before_qty,inventory_used_qty,inventory_after_qty,net_qty,terminal_flag) VALUES(?,?,'爱培科','ROOT','2026-10-01','ROOT',0,'ROOT',10,0,0,0,10,0),(?,?,'爱培科','ROOT','2026-10-01','PART',1,'ROOT:PART',10,10,5,5,5,1)",run,batch,run,batch);
            jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES(?,?,'爱培科','ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','2026-10-01',10),(?,?,'爱培科','ROOT','PART','RAW_MATERIAL','PURCHASED','2026-10-01',5)",run,batch,run,batch);
            jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,is_production_day,revision,row_key) VALUES(?,?,'爱培科','ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','ROOT','2026-10-01','2026-10-10','2026-10-09',10,1,0,'flow-machine'),(?,?,'爱培科','ROOT','PART','RAW_MATERIAL','PURCHASED','ROOT:PART','2026-10-01','2026-10-10','2026-10-09',5,1,0,'flow-old')",run,batch,run,batch);
            long machineId=jdbc.queryForObject("SELECT id FROM mrp_daily_plan_detail WHERE run_id=? AND terminal_type='MACHINE_OUTPUT'",Long.class,run);
            when(daily.getDailyPlans(batch)).thenAnswer(call->dailyFacts.selectByRunId(run));
            reviews.confirm(batch,"SCHEDULE",reviews.status(batch).scheduleFingerprint());
            reviews.confirm(batch,"MATERIAL",reviews.status(batch).materialFingerprint());
            assertEquals("CONFIRMED",reviews.status(batch).material());
            inventory.updateAllocation(allocation,new BigDecimal("3"),0,"物控调整库存占用");
            assertEquals("MATERIAL_RECALC_REQUIRED",batches.selectById(batch).getStatus());
            assertEquals("CONFIRMED",reviews.status(batch).schedule());
            assertEquals("NOT_ANALYZED",reviews.status(batch).material());
            assertThrows(IllegalStateException.class,()->reviews.requireConfirmed(batches.selectById(batch)));

            var task=orchestrator.submitMaterialAnalysis(batch,user);
            assertEquals("CALCULATING",batches.selectById(batch).getStatus());
            assertTrue(task.getInputPayload().contains("materialRecalculation"));
            when(bom.edgesForBatch(any())).thenReturn(List.of());
            when(roles.getMaterialRoleMap(any(),any())).thenReturn(Map.of());
            when(roles.getExistingMaterialCodes(any())).thenReturn(java.util.Set.of());
            when(policies.getDetailMap(any())).thenReturn(Map.of());
            when(calculations.executeCalculation(eq(batch),any(),any(),anyList(),any(),eq(true),eq(run)))
                    .thenAnswer(call->{
                        jdbc.update("INSERT INTO mrp_requirement_detail(run_id,batch_id,factory_code,root_material_code,source_month,current_material_code,level_no,path_hash,gross_qty,inventory_before_qty,inventory_used_qty,inventory_after_qty,net_qty,terminal_flag) VALUES(?,?,'爱培科','ROOT','2026-10-01','PART',1,'ROOT:PART',10,10,3,7,7,1)",run,batch);
                        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES(?,?,'爱培科','ROOT','PART','RAW_MATERIAL','PURCHASED','2026-10-01',7)",run,batch);
                        var result=new MrpCalcRun();result.setId(run);result.setBatchId(batch);return result;
                    });
            doAnswer(call->{
                jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,is_production_day,revision,row_key) VALUES(?,?,'爱培科','ROOT','PART','RAW_MATERIAL','PURCHASED','ROOT:PART','2026-10-01','2026-10-10','2026-10-09',7,1,0,'flow-new')",run,batch);
                return null;
            }).when(daily).distributeMaterial(eq(batch),eq(run),eq(1L),any(),any());
            when(daily.getSourceMonthConservation(batch,run)).thenReturn(new DailyPlanningService.ConservationCheckResult(1,0,List.of()));
            worker.pollAndExecute();
            assertEquals(CalcTask.STATUS_SUCCEEDED,tasks.selectById(task.getId()).getStatus());
            assertEquals("MATERIAL_REVIEW",batches.selectById(batch).getStatus());
            assertEquals(run,batches.selectById(batch).getActiveRunId());
            assertEquals(machineId,jdbc.queryForObject("SELECT id FROM mrp_daily_plan_detail WHERE run_id=? AND terminal_type='MACHINE_OUTPUT'",Long.class,run));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE run_id=? AND row_key='flow-old'",Integer.class,run));
            assertEquals("CONFIRMED",reviews.status(batch).schedule());
            assertNotEquals("CONFIRMED",reviews.status(batch).material());
            assertThrows(IllegalStateException.class,()->reviews.requireConfirmed(batches.selectById(batch)));
            reviews.confirm(batch,"MATERIAL",reviews.status(batch).materialFingerprint());
            assertEquals("READY",batches.selectById(batch).getStatus());
            assertDoesNotThrow(()->reviews.requireConfirmed(batches.selectById(batch)));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_inventory_allocation WHERE id=? AND allocation_type='MANUAL_OVERRIDE' AND allocated_qty=3",Integer.class,allocation));
        } finally {SecurityContextHolder.clearContext();}
    }

    private long insert(String sql,Object... values) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
