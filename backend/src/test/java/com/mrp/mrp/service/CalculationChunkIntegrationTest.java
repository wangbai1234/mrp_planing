package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.engine.NetRequirementEngine;
import com.mrp.mrp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.mybatis.spring.SqlSessionTemplate;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL transaction proxies; a later chunk failure must undo all earlier writes. */
class CalculationChunkIntegrationTest extends BaseIntegrationTest {
    @MockBean MrpTaskWorkerService worker;
    @MockBean BomSnapshotService snapshots;
    @MockBean InventoryNormalizationService inventory;
    @MockBean CategoryRoleVersionService roles;
    @MockBean MaterialPolicyVersionService policies;
    @MockBean StockRiskService risks;
    @MockBean MaterialPolicyNoticeService notices;
    @Autowired MrpCalculationService calculation;
    @Autowired DailyPlanningService daily;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired @Qualifier("primarySqlSessionTemplate") SqlSessionTemplate sqlSession;
    @SpyBean MrpRequirementDetailMapper requirements;
    @SpyBean MrpTerminalDemandMapper terminals;
    @SpyBean MrpDailyPlanDetailMapper facts;
    @SpyBean MrpUnallocatedQuantityMapper pending;
    @SpyBean MrpDecisionIssueMapper issues;
    long batch, calendar;
    List<NetRequirementEngine.RootDemand> roots;
    static final BigDecimal QUANTITY = new BigDecimal("1.000001");

    @BeforeEach void fixture() {
        reset(requirements, terminals, facts, pending, issues);
        calendar = insert("INSERT INTO production_calendar_version(version_no,factory_code,status) SELECT COALESCE(MAX(version_no),0)+1,'爱培科','PUBLISHED' FROM production_calendar_version");
        batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version,production_calendar_version_id,inventory_snapshot_id) VALUES('爱培科','CALCULATING',1,'v1',"+calendar+",1)");
        jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,allocation_weight) VALUES(?,'爱培科','2026-09-24',1,1),(?,'爱培科','2026-10-01',1,1)",calendar,calendar);
        roots = new ArrayList<>();
        var roleMap = new LinkedHashMap<String,CategoryRoleDetail>();
        for(int i=0;i<1101;i++) {
            String material="CHUNK-"+i;
            roots.add(new NetRequirementEngine.RootDemand(material,QUANTITY,LocalDate.of(2026,10,1)));
            var role=new CategoryRoleDetail();role.setRole("RAW_MATERIAL");role.setEnabled(true);roleMap.put(material,role);
        }
        when(snapshots.getSnapshot(1L)).thenReturn(new BomSnapshot());
        when(snapshots.getSnapshotDetails(1L)).thenReturn(List.of());
        when(inventory.buildCalculableInventoryMap(anyLong(),isNull(),eq("爱培科"))).thenReturn(Map.of());
        when(roles.getMaterialRoleMap(isNull(),anySet())).thenReturn(roleMap);
        when(policies.getDetailMap(isNull())).thenReturn(Map.of());
        // Keep this input mapper real elsewhere; this test supplies frozen zero-stock records.
        jdbc.batchUpdate("INSERT INTO mrp_inventory_balance(inventory_snapshot_id,factory_code,inventory_org,warehouse_scope,material_code,inventory_quality,available_quantity,included_flag) VALUES(1,'爱培科','CHUNK',?,?,'GOOD',0,1)",
                roots.stream().map(r->new Object[]{"CHUNK-"+batch,r.materialCode}).toList());
    }

    @AfterEach void cleanup() {
        for(String table:List.of("mrp_daily_plan_detail","mrp_unallocated_quantity","mrp_requirement_detail","mrp_terminal_demand","mrp_decision_issue","mrp_calc_run"))
            jdbc.update("DELETE FROM "+table+" WHERE batch_id=?",batch);
        jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?",batch);
        jdbc.update("DELETE FROM production_calendar_day WHERE version_id=?",calendar);
        jdbc.update("DELETE FROM production_calendar_version WHERE id=?",calendar);
        jdbc.update("DELETE FROM mrp_inventory_balance WHERE inventory_org='CHUNK' AND warehouse_scope=?","CHUNK-"+batch);
    }

    @Test void calculationAndDailyWritesPreserveEveryRowAndSixDecimalQuantityAcrossChunks() {
        var sizes=new ArrayList<Integer>();
        observe(requirements,sizes,false);observe(terminals,sizes,false);
        observe(facts,sizes,false);
        var run=calculate();
        var result=daily.distribute(batch,run.getId(),calendar,null);
        assertEquals(1101,result.details.size());assertTrue(result.unallocated.isEmpty());
        assertEquals(List.of(1000,101,1000,101,1000,101),sizes);
        for(String table:List.of("mrp_requirement_detail","mrp_terminal_demand","mrp_daily_plan_detail"))
            assertEquals(1101,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE batch_id=?",Integer.class,batch));
        assertEquals(0,new BigDecimal("1101.001101").compareTo(jdbc.queryForObject("SELECT SUM(system_quantity) FROM mrp_daily_plan_detail WHERE batch_id=?",BigDecimal.class,batch)));
        assertEquals(0,daily.getSourceMonthConservation(batch,run.getId()).mismatchCount);
    }

    @Test void lateRequirementWriteFailureRollsBackCandidateRunAndFirstChunk() {
        var sizes=new ArrayList<Integer>();observe(requirements,sizes,true);
        assertThrows(IllegalStateException.class,this::calculate);
        assertEquals(List.of(1000,101),sizes);assertEmptyCalculation();
    }

    @Test void lateTerminalWriteFailureRollsBackRequirementsAndCandidateRun() {
        var sizes=new ArrayList<Integer>();observe(terminals,sizes,true);
        assertThrows(IllegalStateException.class,this::calculate);
        assertEquals(List.of(1000,101),sizes);assertEmptyCalculation();
    }

    @Test void lateDailyWriteFailureRollsBackEarlierDailyChunksAndKeepsCalculatedInput() {
        var run=calculate();var sizes=new ArrayList<Integer>();observe(facts,sizes,true);
        assertThrows(IllegalStateException.class,()->daily.distribute(batch,run.getId(),calendar,null));
        assertEquals(List.of(1000,101),sizes);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id=?",Integer.class,batch));
        assertEquals(1101,terminals.selectByRunId(run.getId()).size());
        assertEquals("WAITING_DAILY_DISTRIBUTION",jdbc.queryForObject("SELECT status FROM mrp_calc_batch WHERE id=?",String.class,batch));
    }

    @Test void unallocatedWritesAreBoundedAndLateFailureRollsBackAllPendingRows() {
        var run=calculate();jdbc.update("UPDATE production_calendar_day SET is_production_day=0 WHERE version_id=?",calendar);
        var sizes=new ArrayList<Integer>();observe(pending,sizes,true);
        assertThrows(IllegalStateException.class,()->daily.distribute(batch,run.getId(),calendar,null));
        assertEquals(List.of(1000,101),sizes);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_unallocated_quantity WHERE batch_id=?",Integer.class,batch));
        reset(pending);sizes.clear();observe(pending,sizes,false);
        var result=daily.distribute(batch,run.getId(),calendar,null);
        assertEquals(1101,result.unallocated.size());assertTrue(result.details.isEmpty());
        assertEquals(List.of(1000,101),sizes);
        assertEquals(1101,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_unallocated_quantity WHERE batch_id=?",Integer.class,batch));
    }

    @Test void issueChunksKeepExistingResolvedDecisionsAndEveryNewIssue() {
        jdbc.update("DELETE FROM mrp_inventory_balance WHERE inventory_org='CHUNK' AND warehouse_scope=?","CHUNK-"+batch);
        jdbc.update("INSERT INTO mrp_decision_issue(batch_id,issue_key,issue_type,severity,scope_type,scope_key,status,revision) VALUES(?,'CHUNK-0|CHUNK-0|INVENTORY_MISSING','INVENTORY_MISSING','BLOCKING','MATERIAL','CHUNK-0','RESOLVED',4)",batch);
        var sizes=new ArrayList<Integer>();observe(issues,sizes,false);
        var run=calculate();
        assertEquals(List.of(1000,100),sizes);
        assertEquals(1101,issues.selectByBatchId(batch).size());
        var existing=issues.selectByBatchIdAndKey(batch,"CHUNK-0|CHUNK-0|INVENTORY_MISSING");
        assertEquals("RESOLVED",existing.getStatus());assertEquals(4,existing.getRevision());
        assertEquals("COMPLETED_WITH_ISSUES",run.getStatus());
        assertEquals("WAITING_DECISION",jdbc.queryForObject("SELECT status FROM mrp_calc_batch WHERE id=?",String.class,batch));
    }

    @Test void lateIssueWriteFailureRollsBackRequirementsRunAndAllNewIssues() {
        jdbc.update("DELETE FROM mrp_inventory_balance WHERE inventory_org='CHUNK' AND warehouse_scope=?","CHUNK-"+batch);
        var sizes=new ArrayList<Integer>();observe(issues,sizes,true);
        assertThrows(IllegalStateException.class,this::calculate);
        assertEquals(List.of(1000,101),sizes);assertEmptyCalculation();
        assertTrue(issues.selectByBatchId(batch).isEmpty());
    }

    private MrpCalcRun calculate() {return calculation.executeCalculation(batch,1L,1L,roots);}
    private void assertEmptyCalculation() {
        for(String table:List.of("mrp_requirement_detail","mrp_terminal_demand","mrp_calc_run"))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE batch_id=?",Integer.class,batch));
        assertEquals("CALCULATING",jdbc.queryForObject("SELECT status FROM mrp_calc_batch WHERE id=?",String.class,batch));
    }
    private void observe(Object mapper,List<Integer> sizes,boolean failSecond) {
        var answer=(org.mockito.stubbing.Answer<Integer>) call->{
            List<?> rows=call.getArgument(0);sizes.add(rows.size());
            assertTrue(rows.size()<=1000,"One SQL must not contain the whole result: "+rows.size());
            if(failSecond && sizes.size()==2)throw new IllegalStateException("injected later chunk failure");
            // MyBatis mapper methods are abstract; execute their actual mapped XML in
            // the same Spring-bound session rather than invoking an abstract spy method.
            Class<?> mapperType = mapper instanceof MrpRequirementDetailMapper ? MrpRequirementDetailMapper.class
                    : mapper instanceof MrpTerminalDemandMapper ? MrpTerminalDemandMapper.class
                    : mapper instanceof MrpDailyPlanDetailMapper ? MrpDailyPlanDetailMapper.class
                    : mapper instanceof MrpUnallocatedQuantityMapper ? MrpUnallocatedQuantityMapper.class
                    : MrpDecisionIssueMapper.class;
            return sqlSession.insert(mapperType.getName()+".insertBatch",Map.of("list",rows));
        };
        if(mapper instanceof MrpRequirementDetailMapper m)doAnswer(answer).when(m).insertBatch(anyList());
        else if(mapper instanceof MrpTerminalDemandMapper m)doAnswer(answer).when(m).insertBatch(anyList());
        else if(mapper instanceof MrpDailyPlanDetailMapper m)doAnswer(answer).when(m).insertBatch(anyList());
        else if(mapper instanceof MrpUnallocatedQuantityMapper m)doAnswer(answer).when(m).insertBatch(anyList());
        else if(mapper instanceof MrpDecisionIssueMapper m)doAnswer(answer).when(m).insertBatch(anyList());
    }
    private long insert(String sql) {
        var keys=new GeneratedKeyHolder();jdbc.update(c->c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS),keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
