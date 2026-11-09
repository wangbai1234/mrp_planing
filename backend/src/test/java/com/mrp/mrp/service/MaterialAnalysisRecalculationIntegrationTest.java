package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.domain.MrpCalcRun;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.engine.CalculationProgressListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL transaction: a failed material rebuild must not erase the active run. */
class MaterialAnalysisRecalculationIntegrationTest extends BaseIntegrationTest {
    @Autowired MaterialAnalysisStageService stage;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean MrpTaskWorkerService worker;
    @MockBean MrpCalculationService calculations;
    @MockBean DailyPlanningService daily;
    @MockBean MrpWorkbenchReviewService reviews;
    @MockBean InventoryNormalizationService inventory;
    @MockBean BomSnapshotService bom;
    @MockBean CategoryRoleVersionService roles;
    @MockBean MaterialPolicyVersionService policies;


    @Test void successfulRecalculationReplacesOnlyMaterialFactsInSameRun() {
        long batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,production_calendar_version_id) "
                + "VALUES('爱培科','CALCULATING',1,'v5-shifted-business-weeks',1)");
        long run = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(" + batch + ",'COMPLETED')");
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",run,batch);
        jdbc.update("INSERT INTO mrp_requirement_detail(run_id,batch_id,factory_code,root_material_code,source_month,current_material_code,level_no,path_hash,gross_qty,inventory_before_qty,inventory_used_qty,inventory_after_qty,net_qty,terminal_flag) "
                + "VALUES(?,?,'爱培科','ROOT','2026-10-01','ROOT',0,'ROOT',10,0,0,0,10,0),"
                + "(?,?,'爱培科','ROOT','2026-10-01','OLD',1,'ROOT:OLD',10,0,0,0,10,1)",run,batch,run,batch);
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) "
                + "VALUES(?,?,'爱培科','ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','2026-10-01',10),"
                + "(?,?,'爱培科','ROOT','OLD','RAW_MATERIAL','SELF_MADE','2026-10-01',10)",run,batch,run,batch);
        jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,is_production_day,revision,row_key) "
                + "VALUES(?,?,'爱培科','ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','ROOT','2026-10-01','2026-10-10','2026-10-09',10,1,0,'machine-root'),"
                + "(?,?,'爱培科','ROOT','OLD','RAW_MATERIAL','SELF_MADE','ROOT:OLD','2026-10-01','2026-10-10','2026-10-09',10,1,0,'old-material')",run,batch,run,batch);
        long machineDailyId = jdbc.queryForObject("SELECT id FROM mrp_daily_plan_detail WHERE run_id=? AND terminal_type='MACHINE_OUTPUT'",Long.class,run);
        when(reviews.matchesSchedule(eq(batch),eq(run),eq("frozen"))).thenReturn(true);
        when(inventory.getPoolsByBatch(batch)).thenReturn(List.of());
        when(bom.edgesForBatch(any())).thenReturn(List.of());
        when(roles.getMaterialRoleMap(any(),any())).thenReturn(Map.of());
        when(roles.getExistingMaterialCodes(any())).thenReturn(java.util.Set.of());
        when(policies.getDetailMap(any())).thenReturn(Map.of());
        when(calculations.executeCalculation(eq(batch),any(),any(),anyList(),any(),eq(true),eq(run)))
                .thenAnswer(call -> {
                    jdbc.update("INSERT INTO mrp_requirement_detail(run_id,batch_id,factory_code,root_material_code,source_month,current_material_code,level_no,path_hash,gross_qty,inventory_before_qty,inventory_used_qty,inventory_after_qty,net_qty,terminal_flag) "
                            + "VALUES(?,?,'爱培科','ROOT','2026-10-01','NEW',1,'ROOT:NEW',10,0,0,0,10,1)",run,batch);
                    jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) "
                            + "VALUES(?,?,'爱培科','ROOT','NEW','RAW_MATERIAL','SELF_MADE','2026-10-01',10)",run,batch);
                    MrpCalcRun result = new MrpCalcRun(); result.setId(run); result.setBatchId(batch);
                    return result;
                });
        doAnswer(call -> {
            jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,is_production_day,revision,row_key) "
                    + "VALUES(?,?,'爱培科','ROOT','NEW','RAW_MATERIAL','SELF_MADE','ROOT:NEW','2026-10-01','2026-10-10','2026-10-09',10,1,0,'new-material')",run,batch);
            return null;
        }).when(daily).distributeMaterial(eq(batch),eq(run),eq(1L),any(),any());
        when(daily.getSourceMonthConservation(batch,run))
                .thenReturn(new DailyPlanningService.ConservationCheckResult(1,0,List.of()));
        try {
            MrpCalcRun result=stage.execute(batch,run,"frozen",null,null,List.of(),CalculationProgressListener.NONE);
            assertEquals(run,result.getId());
            assertEquals("MATERIAL_REVIEW",batches.selectById(batch).getStatus());
            assertEquals(run,batches.selectById(batch).getActiveRunId());
            assertEquals(machineDailyId,jdbc.queryForObject("SELECT id FROM mrp_daily_plan_detail WHERE run_id=? AND terminal_type='MACHINE_OUTPUT'",Long.class,run));
            for (String table:List.of("mrp_requirement_detail","mrp_terminal_demand","mrp_daily_plan_detail")) {
                String code="mrp_requirement_detail".equals(table)?"current_material_code":"terminal_material_code";
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE run_id=? AND "+code+"='OLD'",Integer.class,run));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE run_id=? AND "+code+"='NEW'",Integer.class,run));
            }
        } finally {
            jdbc.update("DELETE FROM mrp_daily_plan_detail WHERE run_id=?",run);
            jdbc.update("DELETE FROM mrp_terminal_demand WHERE run_id=?",run);
            jdbc.update("DELETE FROM mrp_requirement_detail WHERE run_id=?",run);
            jdbc.update("DELETE FROM mrp_calc_run WHERE id=?",run);
            jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?",batch);
        }
    }

    @Test void calculationFailureRestoresPriorMaterialFactsAndMachineFacts() {
        long batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) "
                + "VALUES('爱培科','CALCULATING',1,'v5-shifted-business-weeks')");
        long run = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(" + batch + ",'COMPLETED')");
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",run,batch);
        jdbc.update("INSERT INTO mrp_requirement_detail(run_id,batch_id,factory_code,root_material_code,source_month,current_material_code,level_no,path_hash,gross_qty,inventory_before_qty,inventory_used_qty,inventory_after_qty,net_qty,terminal_flag) "
                + "VALUES(?,?,'爱培科','ROOT','2026-10-01','ROOT',0,'ROOT',10,10,2,8,8,0),"
                + "(?,?,'爱培科','ROOT','2026-10-01','COMP',1,'ROOT:COMP',8,0,0,0,8,1)",run,batch,run,batch);
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) "
                + "VALUES(?,?,'爱培科','ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','2026-10-01',8),"
                + "(?,?,'爱培科','ROOT','COMP','RAW_MATERIAL','SELF_MADE','2026-10-01',8)",run,batch,run,batch);
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,risk_type,material_code,root_material_codes) "
                + "VALUES(?,?,'STOCK_POLICY_MISSING','COMP','ROOT')",batch,run);
        when(reviews.matchesSchedule(eq(batch),eq(run),eq("frozen"))).thenReturn(true);
        when(inventory.getPoolsByBatch(batch)).thenReturn(List.of());
        when(bom.edgesForBatch(any())).thenReturn(List.of());
        when(roles.getMaterialRoleMap(any(),any())).thenReturn(Map.of());
        when(roles.getExistingMaterialCodes(any())).thenReturn(java.util.Set.of());
        when(policies.getDetailMap(any())).thenReturn(Map.of());
        when(calculations.executeCalculation(eq(batch),any(),any(),anyList(),any(),eq(true),eq(run)))
                .thenThrow(new IllegalStateException("injected material failure"));

        try {
            assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(tx -> stage.execute(batch,run,"frozen",null,null,
                            List.of(), CalculationProgressListener.NONE)));
            assertEquals(2, count("mrp_requirement_detail",run));
            assertEquals(2, count("mrp_terminal_demand",run));
            assertEquals(1, count("mrp_stock_risk",run));
            assertEquals("CALCULATING",batches.selectById(batch).getStatus());
            assertEquals(run,batches.selectById(batch).getActiveRunId());
        } finally {
            jdbc.update("DELETE FROM mrp_stock_risk WHERE run_id=?",run);
            jdbc.update("DELETE FROM mrp_terminal_demand WHERE run_id=?",run);
            jdbc.update("DELETE FROM mrp_requirement_detail WHERE run_id=?",run);
            jdbc.update("DELETE FROM mrp_calc_run WHERE id=?",run);
            jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?",batch);
        }
    }

    private int count(String table,long run) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id=?",Integer.class,run);
    }

    private long insert(String sql) {
        var keys=new GeneratedKeyHolder();
        jdbc.update(connection->connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS),keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
