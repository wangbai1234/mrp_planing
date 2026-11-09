package com.mrp.mrp;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.service.DailyPlanningService;
import com.mrp.mrp.service.LinkedDailyTransferService;
import com.mrp.mrp.service.RollingPlanningQueryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL and transaction proxies; test methods do not wrap commits in a test transaction. */
class RollingPlanningIntegrationTest extends BaseIntegrationTest {
    private static final LocalDate SOURCE=LocalDate.of(2026,9,30), TARGET=LocalDate.of(2026,10,1);
    @Autowired LinkedDailyTransferService linked;
    @Autowired RollingPlanningQueryService query;
    @Autowired com.mrp.mrp.repository.RollingPlanningMapper rollingMapper;
    @Autowired DailyPlanningService daily;
    @Autowired com.mrp.mrp.controller.MrpDailyPlanningController planningController;
    @Autowired com.mrp.mrp.service.DailyCapacityService capacity;
    @Autowired com.mrp.mrp.service.BatchOrchestratorService orchestrator;
    @Autowired com.mrp.mrp.repository.MrpCalcBatchMapper batches;
    @Autowired com.mrp.mrp.repository.MrpDailyExecutionMapper executionMapper;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired org.apache.ibatis.session.SqlSessionFactory sqlSessions;

    @BeforeEach void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(1L,"admin"),null,List.of(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("mrp_workspace:view"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("mrp_shortage:view"))));
    }
    @AfterEach void logout(){SecurityContextHolder.clearContext();}

    @Test void executionPageIncludesStoredAndVirtualMachineDaysWithSavedStatus() {
        var fixture = fixture("0");
        LocalDate virtualDate = SOURCE.plusDays(2);
        jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','Machine',?,7,0)",
                fixture.batch, fixture.factory, virtualDate);
        String rowKey = jdbc.queryForObject("SELECT row_key FROM mrp_daily_plan_detail WHERE run_id=? AND terminal_type='MACHINE_OUTPUT' LIMIT 1",
                String.class, fixture.run);
        jdbc.update("INSERT INTO mrp_daily_execution(batch_id,row_key,schedule_date,execution_status,planned_quantity,actual_quantity,revision) VALUES(?,?,'2026-09-30','EXECUTED',100,98,0)",
                fixture.batch, rowKey);

        assertEquals(2, executionMapper.countPage(fixture.batch, fixture.run, "ROOT", null));
        var first = executionMapper.selectPage(fixture.batch, fixture.run, "ROOT", null, 0, 1);
        assertEquals(virtualDate, first.get(0).getScheduleDate());
        assertEquals("PLANNED", first.get(0).getExecutionStatus());
        assertEquals(0, first.get(0).getPlannedQuantity().compareTo(new BigDecimal("7")));
        assertEquals(daily.getDailyPlans(fixture.batch).stream()
                .filter(p -> "MACHINE_OUTPUT".equals(p.getTerminalType()) && virtualDate.equals(p.getScheduleDate()))
                .findFirst().orElseThrow().getRowKey(), first.get(0).getRowKey());
        var second = executionMapper.selectPage(fixture.batch, fixture.run, "ROOT", null, 1, 1);
        assertEquals("EXECUTED", second.get(0).getExecutionStatus());
        assertEquals(0, second.get(0).getActualQuantity().compareTo(new BigDecimal("98")));
        assertEquals(1, executionMapper.countPage(fixture.batch, fixture.run, fixture.factory.substring(0, 1), virtualDate));
    }

    @Test void halfUnitSuggestionRoundsToWholeUnitAndRejectsManualDecimalBeforeWriting() {
        var f=fixture("167");
        var preview=linked.preview(f.batch,input(f));
        assertEquals("84",preview.lines().stream().filter(l->l.terminalType().equals("PCBA")).findFirst().orElseThrow().suggestedQuantity());
        var amounts=preview.lines().stream().map(l->new LinkedDailyTransferService.Amount(l.rowKey(),new BigDecimal(l.terminalType().equals("PCBA")?"83.5":l.suggestedQuantity()))).toList();
        assertThrows(IllegalArgumentException.class,()->linked.commit(f.batch,commit(f,preview,amounts)));
        assertEquals(0,overrideCount(f));
    }

    @Test void atomicWholeUnitMovePreservesOriginalFractionalBomAndCreatesSqlVirtualTargets() {
        var f=fixture("167.500001");
        var preview=linked.preview(f.batch,input(f));
        linked.commit(f.batch,commit(f,preview,amounts(preview)));
        assertEquals(4,overrideCount(f));
        decimal("50",quantity(f,"MACHINE_OUTPUT",SOURCE)); decimal("50",quantity(f,"MACHINE_OUTPUT",TARGET));
        decimal("83.500001",quantity(f,"PCBA",SOURCE)); decimal("84",quantity(f,"PCBA",TARGET));
        assertEquals(0,daily.getSourceMonthConservation(f.batch).mismatchCount);
        var target=query.details(f.batch,1,20,f.factory,"ROOT","LEAF",null,TARGET,TARGET);
        assertEquals(1,target.total());
        assertEquals(TARGET,target.items().getFirst().sourceDate());
        assertEquals("84",target.items().getFirst().effectiveQuantity());
        assertEquals(0,target.items().getFirst().revision());
        var grid=query.grid(f.batch,1,20,f.factory,"LEAF");
        assertEquals(1,grid.rows.size());
        assertEquals("84",grid.rows.getFirst().dayQuantities.get(TARGET.toString()));
        decimal("50",grid.capacityLoads.stream().filter(l->l.factoryCode().equals(f.factory)&&l.date().equals(SOURCE)).findFirst().orElseThrow().load());
        decimal("50",grid.capacityLoads.stream().filter(l->l.factoryCode().equals(f.factory)&&l.date().equals(TARGET)).findFirst().orElseThrow().load());
        decimal("167.500001",new BigDecimal(grid.rows.getFirst().rowTotal));
        decimal("167.500001",daily.getMonthlySummary(f.batch,"TERMINAL").stream().map(r->r.totalQuantity).reduce(BigDecimal.ZERO,BigDecimal::add));
    }

    @Test void forgedSubsetAndStalePreviewNeverPartiallyWrite() {
        var f=fixture("167"); var preview=linked.preview(f.batch,input(f));
        assertThrows(IllegalArgumentException.class,()->linked.commit(f.batch,commit(f,preview,List.of(amounts(preview).getFirst()))));
        assertEquals(0,overrideCount(f));
        linked.commit(f.batch,commit(f,preview,amounts(preview)));
        var before=jdbc.queryForList("SELECT * FROM mrp_daily_plan_override WHERE batch_id=? ORDER BY id",f.batch);
        assertThrows(IllegalStateException.class,()->linked.commit(f.batch,commit(f,preview,amounts(preview))));
        assertEquals(before,jdbc.queryForList("SELECT * FROM mrp_daily_plan_override WHERE batch_id=? ORDER BY id",f.batch));
    }

    @Test void lockRunChangeAndPublishedStatusAreRecheckedAfterPreview() {
        for(String condition:List.of("LOCKED","RUN_CHANGED","PUBLISHED")) {
            var f=fixture("167"); var preview=linked.preview(f.batch,input(f));
            if(condition.equals("LOCKED")) jdbc.update("INSERT INTO mrp_daily_execution(batch_id,row_key,schedule_date,execution_status) VALUES(?,'review-lock',?,'LOCKED')",f.batch,TARGET);
            else if(condition.equals("RUN_CHANGED")) jdbc.update("UPDATE mrp_calc_batch SET active_run_id=NULL WHERE id=?",f.batch);
            else jdbc.update("UPDATE mrp_calc_batch SET status='PUBLISHED' WHERE id=?",f.batch);
            assertThrows(IllegalStateException.class,()->linked.commit(f.batch,commit(f,preview,amounts(preview))));
            assertEquals(0,overrideCount(f));
        }
    }

    @Test void completeSixMonthAxisAndGlobalLoadAreIndependentOfMaterialFilterAndSqlPagination() {
        var f=fixture("167");
        // Source months remain separate rows so the business owner can trace each root month.
        fact(f,f.factory,"LEAF","PCBA","November",LocalDate.of(2026,11,1),SOURCE,"3");
        fact(f,f.factory,"OTHER","PCBA","Other",LocalDate.of(2026,10,1),SOURCE,"5");
        fact(f,f.other,"ROOT","MACHINE_OUTPUT","OtherMachine",LocalDate.of(2026,10,1),SOURCE,"7");
        var full=query.grid(f.batch,1,1,null,null);
        var filtered=query.grid(f.batch,1,1,f.factory,"LEAF");
        assertEquals(LocalDate.of(2026,9,22),filtered.calendarDays.getFirst());
        assertEquals(LocalDate.of(2027,3,31),filtered.calendarDays.getLast());
        assertEquals(3,full.totalRows); assertEquals(2,filtered.totalRows);
        assertEquals("167",filtered.rows.getFirst().dayQuantities.get(SOURCE.toString()));
        assertEquals(full.capacityLoads,filtered.capacityLoads);
        assertEquals(2,filtered.capacityLoads.stream().map(l->l.factoryCode()).distinct().count());
        decimal("100",filtered.capacityLoads.stream().filter(l->l.factoryCode().equals(f.factory)&&l.date().equals(SOURCE)).findFirst().orElseThrow().load());
        var details=query.details(f.batch,1,1,f.factory,"ROOT","LEAF",null,SOURCE,SOURCE);
        assertEquals(2,details.total()); assertEquals(1,details.items().size());
        assertTrue(filtered.weekGroups.stream().anyMatch(w->w.weekStart.equals(LocalDate.of(2026,9,22))));
    }

    @Test void sourceMonthDetailFilterDoesNotMixSameMachineAndDay() {
        var f=fixture("167");
        var november=LocalDate.of(2026,11,1);
        fact(f,f.factory,"ROOT","MACHINE_OUTPUT","NovemberOutput",november,SOURCE,"4");
        var october=query.details(f.batch,1,20,f.factory,"ROOT","ROOT","MACHINE_OUTPUT",
                LocalDate.of(2026,10,1),SOURCE,SOURCE);
        var later=query.details(f.batch,1,20,f.factory,"ROOT","ROOT","MACHINE_OUTPUT",
                november,SOURCE,SOURCE);
        assertEquals(1,october.total());
        assertEquals(1,later.total());
        assertEquals(LocalDate.of(2026,10,1),october.items().getFirst().sourceMonth());
        assertEquals(november,later.items().getFirst().sourceMonth());
        assertEquals(2,query.details(f.batch,1,20,f.factory,"ROOT","ROOT","MACHINE_OUTPUT",SOURCE,SOURCE).total());
    }

    @Test void v5MachineGridGroupsSourceMonthsAfterServerPagination() {
        var f=fixture("167");
        jdbc.update("UPDATE mrp_calc_batch SET rule_version='v5-shifted-business-weeks' WHERE id=?",f.batch);
        fact(f,f.factory,"ROOT","MACHINE_OUTPUT","NovemberOutput",LocalDate.of(2026,11,1),SOURCE,"4");
        var result=query.grid(f.batch,1,1,f.factory,"ROOT","MACHINE_OUTPUT",false);
        assertEquals(1,result.totalRows);
        assertEquals(1,result.rows.size());
        assertNull(result.rows.getFirst().sourceMonth);
        assertEquals("104",result.rows.getFirst().dayQuantities.get(SOURCE.toString()));
        assertEquals(2,query.details(f.batch,1,20,f.factory,"ROOT","ROOT","MACHINE_OUTPUT",SOURCE,SOURCE).total());
    }

    @Test void singleFactoryUserCannotReadCombinedFactoryBatch() {
        var f=fixture("167");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(9L,"single-factory"),null,List.of()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->query.grid(f.batch,1,20,null,null));
    }

    @Test void detailPagesMatchFullProjectionAcrossStoredAndVirtualFactsWithoutOmissions() {
        var f=fixture("167.500001");
        for(int i=0;i<36;i++) {
            String path="Page"+String.format("%02d",i);
            String factory=i%2==0?f.factory:f.other;
            String terminal=i%3==0?"OTHER":"LEAF";
            LocalDate date=i%2==0?SOURCE:TARGET;
            fact(f,factory,terminal,"PCBA",path,LocalDate.of(2026,10,1).plusMonths(i%2),date,"3.000001");
            if(i%3==0) {
                jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision,reason) VALUES(?,?,'ROOT',?,'PCBA','SELF_MADE',?,?,?,2,'分页验证')",
                        f.batch,factory,terminal,path,date,i%2==0?"0":"-0.000001");
            }
            // Targets fall before and after stored facts, so the two branches must be merged before paging.
            if(i%4==0) jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision,reason) VALUES(?,?,'ROOT',?,'PCBA','SELF_MADE',?,?,1.000001,3,'虚拟日期分页验证')",
                    f.batch,factory,terminal,path,i%8==0?SOURCE.minusDays(1):TARGET.plusDays(1));
        }
        // Same existing sort keys, distinct supply/type: pagination must still have a deterministic order.
        fact(f,f.factory,"LEAF","RAW_MATERIAL","Leaf",LocalDate.of(2026,10,1),SOURCE,"0.000001");
        for(String rule:List.of("v4-rolling-business-weeks","v3-whole-machine-capacity")) {
            jdbc.update("UPDATE mrp_calc_batch SET rule_version=? WHERE id=?",rule,f.batch);
            for(String factory:Arrays.asList(null,f.factory,f.other)) {
                assertDetailPages(f,factory,null,null,null);
                assertDetailPages(f,factory,"ROOT","LEAF","PCBA");
                assertDetailPages(f,factory,"ROOT",null,"MACHINE_OUTPUT");
            }
        }
    }

    @Test void automaticDetailPagesKeepExactOrderAndSwitchAfterOverridesAreAddedAndRemoved() {
        var f=fixture("167.500001");
        for(int i=0;i<36;i++) fact(f,i%2==0?f.factory:f.other,"LEAF",i%3==0?"RAW_MATERIAL":"PCBA",
                "Auto"+String.format("%02d",i),LocalDate.of(2026,10,1).plusMonths(i%2),
                i%2==0?SOURCE:TARGET,i%7==0?"0":i%11==0?"-0.000001":"3.000001");
        for(String factory:Arrays.asList(null,f.factory,f.other)) {
            assertDetailPages(f,factory,null,null,null);
            assertDetailPages(f,factory,"ROOT","LEAF","PCBA");
            assertDetailPages(f,factory,"ROOT",null,"MACHINE_OUTPUT");
        }
        var before=query.details(f.batch,1,100,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(1));
        var preview=linked.preview(f.batch,input(f));
        linked.commit(f.batch,commit(f,preview,amounts(preview)));
        assertDetailPages(f,null,null,null,null);
        jdbc.update("DELETE FROM mrp_daily_plan_override WHERE batch_id=?",f.batch);
        assertEquals(before,query.details(f.batch,1,100,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(1)));
        assertDetailPages(f,null,null,null,null);
    }

    @Test void automaticDetailReadUsesOneSnapshotWhileAnotherTransactionAddsAnOverride() {
        var f=fixture("167.500001");
        var before=query.details(f.batch,1,100,null,null,null,null,SOURCE,TARGET);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status->{
            assertEquals(before,query.details(f.batch,1,100,null,null,null,null,SOURCE,TARGET));
            java.util.concurrent.CompletableFuture.runAsync(()->jdbc.update(
                    "INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT','LEAF','PCBA','SELF_MADE','Leaf',?,0,0)",
                    f.batch,f.factory,SOURCE)).join();
            assertEquals(before,query.details(f.batch,1,100,null,null,null,null,SOURCE,TARGET));
        });
        assertEquals(0,query.details(f.batch,1,100,null,null,null,null,SOURCE,TARGET).total());
    }

    @Test void sparseOverrideWindowMatchesEveryProjectedFieldAcrossOffsetsAndHistoricalRules() {
        var f=fixture("167.500001");
        for(int i=0;i<64;i++) {
            String path="Window"+String.format("%02d",i);
            LocalDate date=SOURCE.plusDays(i%2);
            fact(f,i%2==0?f.factory:f.other,"LEAF","PCBA",path,
                    LocalDate.of(2026,10,1).plusMonths(i%3),date,i%7==0?"0":"3.000001");
            if(i<6) {
                // Multiple dates in one source-month group. Both distribution engines
                // encode source month in pathGroupKey, so a group cannot span months.
                fact(f,i%2==0?f.factory:f.other,"LEAF","PCBA",path,
                        LocalDate.of(2026,10,1).plusMonths(i%3),date.plusDays(1),"2.000001");
                jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT','LEAF','PCBA','SELF_MADE',?,?,1.000001,3)",
                        f.batch,i%2==0?f.factory:f.other,path,SOURCE.plusDays(i%3-1));
            }
        }
        jdbc.update("UPDATE mrp_daily_plan_detail SET is_production_day=FALSE WHERE batch_id=? AND path_group_key='Window10'",f.batch);
        jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT','LEAF','PCBA','SELF_MADE','Leaf',?,0,4)",f.batch,f.factory,SOURCE);
        for(String rule:List.of("v4-rolling-business-weeks","v3-whole-machine-capacity")) {
            jdbc.update("UPDATE mrp_calc_batch SET rule_version=? WHERE id=?",rule,f.batch);
            boolean rolling=rule.startsWith("v4");
            long virtual=rollingMapper.countVirtualDetails(f.run,f.batch,null,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(2));
            assertTrue(virtual>0 && virtual<=6);
            for(int size:List.of(1,2,7,20)) {
                for(long offset:List.of(0L,1L,virtual-1,virtual,virtual+1,30L,60L,80L,214748364700L)) {
                    var original=rollingMapper.selectDetails(f.run,f.batch,null,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(2),rolling,true,offset,size,null);
                    var window=rollingMapper.selectDetails(f.run,f.batch,null,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(2),rolling,true,offset,size,virtual);
                    org.assertj.core.api.Assertions.assertThat(window).usingRecursiveComparison()
                            .withComparatorForType(BigDecimal::compareTo,BigDecimal.class).isEqualTo(original);
                }
            }
            assertDetailPages(f,null,null,null,null);
        }
    }

    @Test void denseVirtualDatesKeepEveryFieldAndPageAcrossHistoricalRulesAndTypeFilters() {
        var f=fixture("167.500001");
        for(int i=0;i<72;i++) {
            String path="Dense"+String.format("%02d",i);
            String factory=i%2==0?f.factory:f.other;
            String type=i%4==0?"MACHINE_OUTPUT":"PCBA";
            String terminal=type.equals("MACHINE_OUTPUT")?"ROOT":"LEAF";
            LocalDate date=SOURCE.plusDays(i%2);
            fact(f,factory,terminal,type,path,LocalDate.of(2026,10,1).plusMonths(i%3),date,i%11==0?"0":"3.000001");
            LocalDate target=switch(i%3) {case 0->SOURCE.minusDays(1);case 1->TARGET.plusDays(1);default->SOURCE.plusDays(1-i%2);};
            jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT',?,?,'SELF_MADE',?,?,?,3)",
                    f.batch,factory,terminal,type,path,target,i<70?new BigDecimal("1.000001"):new BigDecimal(i==70?"0":"-0.000001"));
        }
        jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT','LEAF','PCBA','SELF_MADE','Leaf',?,0,4)",f.batch,f.factory,SOURCE);
        for(String rule:List.of("v4-rolling-business-weeks","v3-whole-machine-capacity")) {
            jdbc.update("UPDATE mrp_calc_batch SET rule_version=? WHERE id=?",rule,f.batch);
            boolean rolling=rule.startsWith("v4");
            for(String type:new String[]{null,"PCBA","MACHINE_OUTPUT"}) {
                long virtual=rollingMapper.countVirtualDetails(f.run,f.batch,null,null,null,type,null,SOURCE.minusDays(1),TARGET.plusDays(1));
                assertTrue(virtual>7,"Exercise more virtual dates than a service page");
                var fullOriginal=unsortedOriginalDetails(f,type,rolling);
                for(int size:List.of(1,7,20,100)) for(long offset:List.of(0L,1L,virtual-1,virtual,virtual+1,80L,150L,214748364700L)) {
                    var original=fullOriginal.stream().skip(offset).limit(size).toList();
                    var window=rollingMapper.selectDetails(f.run,f.batch,null,null,null,type,null,SOURCE.minusDays(1),TARGET.plusDays(1),rolling,true,offset,size,virtual);
                    org.assertj.core.api.Assertions.assertThat(window).usingRecursiveComparison()
                            .withComparatorForType(BigDecimal::compareTo,BigDecimal.class).isEqualTo(original);
                }
                assertDetailPages(f,null,null,null,type,true);
            }
        }
    }

    @Test void binaryWeightsPreserveNativeUnicodePadSpaceOrderIncludingMaximumWidth() {
        assertEquals("utf8mb4_unicode_ci",jdbc.queryForObject("SELECT COLLATION_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mrp_daily_plan_detail' AND COLUMN_NAME='path_group_key'",String.class));
        assertEquals("PAD SPACE",jdbc.queryForObject("SELECT PAD_ATTRIBUTE FROM information_schema.COLLATIONS WHERE COLLATION_NAME='utf8mb4_unicode_ci'",String.class));
        var values=List.of("", " ", "a", "A", "a ", "á", "a\u0301", "ae", "Æ", "ß", "ss", "中", "文", "😀", "😃", "a\t", "a\u0000", "\u0344", "\u0344 ", "中".repeat(255)+"a", "中".repeat(255)+"b", "ß".repeat(255)+"a", "ß".repeat(255)+"b", "æ".repeat(255)+"a", "æ".repeat(255)+"b", "ﷺ".repeat(256), "ﬃ".repeat(255)+"a", "ﬃ".repeat(255)+"b");
        String weightLength="GREATEST(OCTET_LENGTH(WEIGHT_STRING(l)),OCTET_LENGTH(WEIGHT_STRING(r)))";
        String weights="STRCMP(RPAD(WEIGHT_STRING(l),"+weightLength+",WEIGHT_STRING(_utf8mb4' ' COLLATE utf8mb4_unicode_ci)),RPAD(WEIGHT_STRING(r),"+weightLength+",WEIGHT_STRING(_utf8mb4' ' COLLATE utf8mb4_unicode_ci)))";
        for(var left:values) for(var right:values) {
            var result=jdbc.queryForMap("SELECT STRCMP(l,r) native_order,"+weights+" weight_order FROM (SELECT CONVERT(? USING utf8mb4) COLLATE utf8mb4_unicode_ci l,CONVERT(? USING utf8mb4) COLLATE utf8mb4_unicode_ci r) weights",left,right);
            assertEquals(Integer.signum(((Number)result.get("native_order")).intValue()),Integer.signum(((Number)result.get("weight_order")).intValue()),left+" / "+right);
        }
    }

    @Test void denseExpandedWeightsKeepTrailingDifferencesBeyondMaxSortLength() {
        var f=fixture("167.500001");
        jdbc.update("DELETE FROM mrp_daily_plan_detail WHERE run_id=?",f.run);
        for(var expansion:List.of("ﷺ","ß","æ","ﬃ")) for(int i=19;i>=0;i--) {
            String path=expansion.repeat(252)+String.format("%04d",i);
            jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,row_key) VALUES(?,?,'F','ROOT','LEAF','PCBA','SELF_MADE',?,?,?,?,3.000001,?)",
                    f.run,f.batch,path,LocalDate.of(2026,10,1),SOURCE,SOURCE,UUID.randomUUID().toString());
        }
        assertTrue(jdbc.queryForObject("SELECT MAX(OCTET_LENGTH(WEIGHT_STRING(path_group_key))) FROM mrp_daily_plan_detail WHERE run_id=?",Integer.class,f.run)>jdbc.queryForObject("SELECT @@max_sort_length",Integer.class));
        jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) SELECT batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,DATE_ADD(schedule_date,INTERVAL 2 DAY),1.000001,3 FROM mrp_daily_plan_detail WHERE run_id=?",f.run);
        var paths=jdbc.queryForList("SELECT path_group_key FROM mrp_daily_plan_detail FORCE INDEX (idx_mdpd_run_detail_order) WHERE run_id=? ORDER BY schedule_date,source_month,factory_code,root_material_code,terminal_material_code,path_group_key,terminal_type,supply_mode",String.class,f.run);
        for(boolean rolling:List.of(true,false)) {
            var legacy=unsortedOriginalDetails(f,null,rolling);
            var expected=new ArrayList<MrpDailyPlanDetail>();
            for(var date:List.of(SOURCE,SOURCE.plusDays(2))) for(var path:paths)
                expected.add(legacy.stream().filter(row->row.getScheduleDate().equals(date)&&row.getPathGroupKey().equals(path)).findFirst().orElseThrow());
            assertEquals(160,expected.size());
            for(int size:List.of(1,7,20,100)) for(long offset:List.of(0L,1L,19L,39L,59L,79L,80L,81L,150L,160L,214748364700L)) {
                var actual=rollingMapper.selectDetails(f.run,f.batch,null,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(1),rolling,true,offset,size,80L);
                org.assertj.core.api.Assertions.assertThat(actual).usingRecursiveComparison()
                        .withComparatorForType(BigDecimal::compareTo,BigDecimal.class).isEqualTo(expected.stream().skip(offset).limit(size).toList());
            }
        }
    }

    @Test void denseMaximumWidthMultibyteKeysKeepNativeIndexedOrderAndCompletePayload() {
        var f=fixture("167.500001");
        jdbc.update("DELETE FROM mrp_daily_plan_detail WHERE run_id=?",f.run);
        for(int i=0;i<80;i++) {
            String factory=(i%2==0?"厂":"工").repeat(31)+(char)('a'+i%5);
            String root=(i%3==0?"á":"A").repeat(63)+(i%4==0?" ":"中");
            String terminal="料".repeat(63)+(char)('a'+i%7);
            String path="路".repeat(252)+String.format("%04d",i);
            String type="类".repeat(31)+"型",supply="供".repeat(31)+"应";
            jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,row_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    f.run,f.batch,factory,root,terminal,type,supply,path,LocalDate.of(2026,10,1).plusMonths(i%3),SOURCE,SOURCE,new BigDecimal("3.000001"),UUID.randomUUID().toString());
        }
        jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) SELECT batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,DATE_ADD(schedule_date,INTERVAL 2 DAY),1.000001,3 FROM mrp_daily_plan_detail WHERE run_id=?",f.run);
        var paths=jdbc.queryForList("SELECT path_group_key FROM mrp_daily_plan_detail FORCE INDEX (idx_mdpd_run_detail_order) WHERE run_id=? ORDER BY schedule_date,source_month,factory_code,root_material_code,terminal_material_code,path_group_key,terminal_type,supply_mode",String.class,f.run);
        for(boolean rolling:List.of(true,false)) {
            var legacy=unsortedOriginalDetails(f,null,rolling);
            var expected=new ArrayList<MrpDailyPlanDetail>();
            for(var date:List.of(SOURCE,SOURCE.plusDays(2))) for(var path:paths)
                expected.add(legacy.stream().filter(row->row.getScheduleDate().equals(date)&&row.getPathGroupKey().equals(path)).findFirst().orElseThrow());
            assertEquals(160,expected.size());
            long virtual=rollingMapper.countVirtualDetails(f.run,f.batch,null,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(1));
            assertEquals(80,virtual);
            for(int size:List.of(1,7,20,100)) for(long offset:List.of(0L,1L,79L,80L,81L,150L,160L,214748364700L)) {
                var actual=rollingMapper.selectDetails(f.run,f.batch,null,null,null,null,null,SOURCE.minusDays(1),TARGET.plusDays(1),rolling,true,offset,size,virtual);
                org.assertj.core.api.Assertions.assertThat(actual).usingRecursiveComparison()
                        .withComparatorForType(BigDecimal::compareTo,BigDecimal.class).isEqualTo(expected.stream().skip(offset).limit(size).toList());
            }
        }
    }

    @Test void sparseWindowCountAndPayloadUseOneSnapshotWhileVirtualRowsAreAdded() {
        var f=fixture("167.500001");
        jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT','LEAF','PCBA','SELF_MADE','Leaf',?,1,2)",f.batch,f.factory,SOURCE.minusDays(1));
        var before=query.details(f.batch,1,20,null,null,null,null,SOURCE.minusDays(1),TARGET);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status->{
            assertEquals(before,query.details(f.batch,1,20,null,null,null,null,SOURCE.minusDays(1),TARGET));
            java.util.concurrent.CompletableFuture.runAsync(()->jdbc.update(
                    "INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity,revision) VALUES(?,?,'ROOT','LEAF','PCBA','SELF_MADE','Leaf',?,2,3)",f.batch,f.factory,TARGET)).join();
            assertEquals(before,query.details(f.batch,1,20,null,null,null,null,SOURCE.minusDays(1),TARGET));
        });
        assertEquals(before.total()+1,query.details(f.batch,1,20,null,null,null,null,SOURCE.minusDays(1),TARGET).total());
    }

    private void assertDetailPages(Fixture f,String factory,String root,String terminal,String type) {
        assertDetailPages(f,factory,root,terminal,type,false);
    }
    private void assertDetailPages(Fixture f,String factory,String root,String terminal,String type,boolean dense) {
        var order=Comparator.comparing(MrpDailyPlanDetail::getScheduleDate)
                .thenComparing(MrpDailyPlanDetail::getSourceMonth).thenComparing(MrpDailyPlanDetail::getFactoryCode)
                .thenComparing(MrpDailyPlanDetail::getRootMaterialCode).thenComparing(MrpDailyPlanDetail::getTerminalMaterialCode)
                .thenComparing(MrpDailyPlanDetail::getPathGroupKey).thenComparing(MrpDailyPlanDetail::getTerminalType)
                .thenComparing(MrpDailyPlanDetail::getSupplyMode).thenComparing(MrpDailyPlanDetail::getSourceDate)
                .thenComparing(MrpDailyPlanDetail::getRowKey);
        var expected=daily.getDailyPlans(f.batch).stream().filter(p->p.getEffectiveQuantity().signum()>0)
                .filter(p->factory==null||factory.equals(p.getFactoryCode()))
                .filter(p->root==null||root.equals(p.getRootMaterialCode()))
                .filter(p->terminal==null||terminal.equals(p.getTerminalMaterialCode()))
                .filter(p->type==null?!"MACHINE_OUTPUT".equals(p.getTerminalType()):type.equals(p.getTerminalType()))
                .sorted(order).map(p->p.getRowKey()+"|"+p.getSourceDate()+"|"+RollingPlanningQueryService.text(p.getEffectiveQuantity())).toList();
        List<String> actual=new ArrayList<>();
        var fullOriginal=dense?unsortedOriginalDetails(f,type,RollingPlanningQueryService.rolling(batches.selectById(f.batch))):List.<MrpDailyPlanDetail>of();
        for(int page=1;page<=expected.size()/7+2;page++) {
            var result=query.details(f.batch,page,7,factory,root,terminal,type,SOURCE.minusDays(1),TARGET.plusDays(1));
            assertEquals(expected.size(),result.total());
            var original=dense?fullOriginal.stream().skip((long)(page-1)*7).limit(7).toList():rollingMapper.selectDetails(f.run,f.batch,factory,root,terminal,type,null,SOURCE.minusDays(1),TARGET.plusDays(1),
                    RollingPlanningQueryService.rolling(batches.selectById(f.batch)),rollingMapper.hasOverrides(f.batch),(long)(page-1)*7,7,null);
            var units=capacity.units(batches.selectById(f.batch).getProductionCalendarVersionId());
            var originalCells=original.stream().map(p->new RollingPlanningQueryService.CellFact(DailyPlanningService.gridRowKey(p),
                    p.getRowKey(),p.getFactoryCode(),p.getRootMaterialCode(),p.getTerminalMaterialCode(),p.getTerminalType(),
                    p.getSupplyMode(),p.getPathGroupKey(),p.getSourceMonth(),p.getSourceDate(),p.getScheduleDate(),
                    RollingPlanningQueryService.text(p.getSystemQuantity()),RollingPlanningQueryService.text(p.getEffectiveQuantity()),
                    p.getRevision(),"MACHINE_OUTPUT".equals(p.getTerminalType())?"台":units.get(p.getTerminalMaterialCode()),null)).toList();
            assertEquals(originalCells,result.items());
            actual.addAll(result.items().stream().map(p->p.factRowKey()+"|"+p.sourceDate()+"|"+p.effectiveQuantity()).toList());
            assertEquals(result,query.details(f.batch,page,7,factory,root,terminal,type,SOURCE.minusDays(1),TARGET.plusDays(1)));
        }
        assertEquals(expected,actual);
        assertEquals(actual.size(),new HashSet<>(actual).size());
    }

    /** Keep the complete legacy projection as oracle without its failing wide filesort.
     * MySQL's default sort buffer can reject even this small dense fixture. No server
     * settings or data/field assertions are relaxed: read every projected field and
     * apply the declared eight-column total order independently in Java. */
    private List<MrpDailyPlanDetail> unsortedOriginalDetails(Fixture f,String type,boolean rolling) {
        var params=new HashMap<String,Object>();
        params.put("runId",f.run);params.put("batchId",f.batch);
        params.put("factoryCode",null);params.put("rootMaterialCode",null);params.put("terminalMaterialCode",null);
        params.put("terminalType",type);params.put("startDate",SOURCE.minusDays(1));params.put("endDate",TARGET.plusDays(1));
        params.put("rolling",rolling);params.put("hasOverrides",true);params.put("virtualCount",null);
        params.put("offset",0L);params.put("pageSize",100);
        var config=sqlSessions.getConfiguration();
        var statement=config.getMappedStatement("com.mrp.mrp.repository.RollingPlanningMapper.selectDetails");
        var bound=statement.getBoundSql(params);
        String sql=bound.getSql().substring(0,bound.getSql().lastIndexOf("ORDER BY"));
        var projection=new org.apache.ibatis.mapping.BoundSql(config,sql,
                bound.getParameterMappings().subList(0,bound.getParameterMappings().size()-2),params);
        bound.getAdditionalParameters().forEach(projection::setAdditionalParameter);
        var values=jdbc.query(connection->{
            var prepared=connection.prepareStatement(sql);
            new org.apache.ibatis.scripting.defaults.DefaultParameterHandler(statement,params,projection).setParameters(prepared);
            return prepared;
        },new org.springframework.jdbc.core.BeanPropertyRowMapper<>(MrpDailyPlanDetail.class));
        values.sort(Comparator.comparing(MrpDailyPlanDetail::getScheduleDate).thenComparing(MrpDailyPlanDetail::getSourceMonth)
                .thenComparing(MrpDailyPlanDetail::getFactoryCode).thenComparing(MrpDailyPlanDetail::getRootMaterialCode)
                .thenComparing(MrpDailyPlanDetail::getTerminalMaterialCode).thenComparing(MrpDailyPlanDetail::getPathGroupKey)
                .thenComparing(MrpDailyPlanDetail::getTerminalType).thenComparing(MrpDailyPlanDetail::getSupplyMode));
        return values;
    }

    @Test void aggregateSummaryCountsHandlesEmptyRunAndMatchesStoredFacts() {
        var f=fixture("167.500001");
        var counts=batches.selectSummaryCounts(f.batch,f.run);
        assertEquals(2,counts.dailyPlanCount());
        assertEquals(0,counts.totalIssues()); assertEquals(0,counts.openIssues());
        assertEquals(0,counts.poolCount()); decimal("0",counts.totalAvailable()); decimal("0",counts.totalAllocated());
        assertEquals(0,batches.selectSummaryCounts(f.batch,null).dailyPlanCount());
        jdbc.update("INSERT INTO mrp_decision_issue(batch_id,issue_key,issue_type,severity,scope_type,scope_key,status) VALUES(?,'summary-test','TEST','WARNING','BATCH','test','OPEN')",f.batch);
        assertEquals(1,batches.selectSummaryCounts(f.batch,f.run).openIssues());
    }

    @Test void zeroSourceOverrideIsExcludedAndVirtualMachineTargetHasExactLoad() {
        var f=fixture("167");
        var input=new LinkedDailyTransferService.Input(f.factory+"|ROOT|ROOT|MACHINE_OUTPUT|SELF_MADE|Machine",SOURCE,TARGET,new BigDecimal("100"),f.run);
        var preview=linked.preview(f.batch,input);
        linked.commit(f.batch,new LinkedDailyTransferService.Commit(input.rowKey(),SOURCE,TARGET,input.quantity(),f.run,preview.fingerprint(),"全量移动",amounts(preview)));
        assertEquals(0,query.details(f.batch,1,20,f.factory,"ROOT",null,"MACHINE_OUTPUT",SOURCE,SOURCE).total());
        assertEquals(1,query.details(f.batch,1,20,f.factory,"ROOT",null,"MACHINE_OUTPUT",TARGET,TARGET).total());
        var grid=query.grid(f.batch,1,20,null,"does-not-exist");
        decimal("0",grid.capacityLoads.stream().filter(l->l.factoryCode().equals(f.factory)&&l.date().equals(SOURCE)).findFirst().orElseThrow().load());
        decimal("100",grid.capacityLoads.stream().filter(l->l.factoryCode().equals(f.factory)&&l.date().equals(TARGET)).findFirst().orElseThrow().load());
    }

    @Test void staleFullTransferReturnsConflictBeforeRemainingQuantityValidation() {
        var f=fixture("167");
        var input=new LinkedDailyTransferService.Input(f.factory+"|ROOT|ROOT|MACHINE_OUTPUT|SELF_MADE|Machine",SOURCE,TARGET,new BigDecimal("100"),f.run);
        var preview=linked.preview(f.batch,input);
        var commit=new LinkedDailyTransferService.Commit(input.rowKey(),SOURCE,TARGET,input.quantity(),f.run,
                preview.fingerprint(),"并发旧预览",amounts(preview));
        linked.commit(f.batch,commit);
        assertEquals(4,overrideCount(f));
        assertThrows(com.mrp.common.exception.ConcurrentChangeException.class,()->linked.commit(f.batch,commit));
        assertEquals(4,overrideCount(f));
    }

    @Test void capacityEndpointAggregateMatchesFullFactsBeforeAndAfterWholeMachineTransfer() {
        var f=fixture("167.500001");
        assertCapacity(f);
        var preview=linked.preview(f.batch,input(f));
        linked.commit(f.batch,commit(f,preview,amounts(preview)));
        assertCapacity(f);
        jdbc.update("UPDATE mrp_calc_batch SET rule_version='v3-whole-machine-capacity' WHERE id=?",f.batch);
        assertCapacity(f);
        jdbc.update("UPDATE mrp_calc_batch SET rule_version='v2-daily-capacity' WHERE id=?",f.batch);
        assertCapacity(f);
    }
    private void assertCapacity(Fixture f) {
        assertEquals(capacity.evaluate(batches.selectById(f.batch),daily.getDailyPlans(f.batch),SOURCE,TARGET),
                planningController.capacityLoads(f.batch,SOURCE,TARGET).getBody().data());
    }

    @Test void conservationAggregationMatchesCompleteProjectionWithTransfersPendingAndMismatches() {
        var f=fixture("167.500001");
        assertConservationProjection(f,0);
        var input=new LinkedDailyTransferService.Input(f.factory+"|ROOT|ROOT|MACHINE_OUTPUT|SELF_MADE|Machine",SOURCE,TARGET,new BigDecimal("100"),f.run);
        var preview=linked.preview(f.batch,input);
        linked.commit(f.batch,new LinkedDailyTransferService.Commit(input.rowKey(),SOURCE,TARGET,input.quantity(),f.run,preview.fingerprint(),"守恒聚合验证",amounts(preview)));
        assertConservationProjection(f,0);
        // A different factory/month must remain a separate demand group.
        fact(f,f.other,"LEAF","PCBA","November",LocalDate.of(2026,11,1),TARGET,"3.000001");
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES(?,?,?,'ROOT','LEAF','PCBA','SELF_MADE','2026-11-01',5.000001)",f.run,f.batch,f.other);
        jdbc.update("INSERT INTO mrp_unallocated_quantity(run_id,batch_id,demand_key,source_date,attempted_schedule_date,quantity,reason_code,status) VALUES(?,?,?,'2026-11-01','2026-11-01',2,'TEST','PENDING')",f.run,f.batch,f.other+"|ROOT|LEAF|PCBA|SELF_MADE|2026-11");
        assertConservationProjection(f,0);
        jdbc.update("UPDATE mrp_unallocated_quantity SET status='RESOLVED' WHERE run_id=?",f.run);
        assertConservationProjection(f,1);
        // Corrupt a transfer to prove aggregation detects the same exact mismatch.
        jdbc.update("UPDATE mrp_daily_plan_override SET manual_quantity=manual_quantity+0.000001 WHERE batch_id=? AND terminal_type='PCBA' AND schedule_date=?",f.batch,TARGET);
        assertConservationProjection(f,2);
        long candidate=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'CALCULATING')",f.batch);
        assertEquals(2,daily.getSourceMonthConservation(f.batch,f.run).mismatchCount);
        assertEquals(0,daily.getSourceMonthConservation(f.batch,candidate).totalDemands);
    }

    @Test void conservationRejectsOrphanDailyAndPendingGroupsBeforePublication() {
        var f=fixture("167.500001");
        completePublicationCalendar(f);
        fact(f,f.factory,"ORPHAN","PCBA","Orphan",LocalDate.of(2026,10,1),SOURCE,"0.000001");
        assertConservationProjection(f,1);
        var mismatch=daily.getSourceMonthConservation(f.batch).mismatches.get(0);
        decimal("0",mismatch.demandQuantity);
        decimal("0.000001",mismatch.planQuantity);
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_batch",Integer.class);
        var error=assertThrows(IllegalStateException.class,()->orchestrator.publish(f.batch,null));
        assertTrue(error.getMessage().contains("conservation"));
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_batch",Integer.class));
        assertEquals("READY",batches.selectById(f.batch).getStatus());
        jdbc.update("UPDATE mrp_daily_plan_detail SET system_quantity=0 WHERE run_id=? AND terminal_material_code='ORPHAN'",f.run);
        assertConservationProjection(f,0);
        String key=f.other+"|ROOT|ORPHAN|PCBA|SELF_MADE|2026-11";
        jdbc.update("INSERT INTO mrp_unallocated_quantity(run_id,batch_id,demand_key,source_date,attempted_schedule_date,quantity,reason_code,status) VALUES(?,?,?,'2026-11-01','2026-11-01',0.000001,'TEST','PENDING')",f.run,f.batch,key);
        assertConservationProjection(f,1);
        jdbc.update("UPDATE mrp_unallocated_quantity SET status='RESOLVED' WHERE run_id=?",f.run);
        assertConservationProjection(f,0);
        long candidate=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'CALCULATING')",f.batch);
        var candidateFixture=new Fixture(f.batch,candidate,f.factory,f.other);
        fact(candidateFixture,f.other,"ORPHAN","PCBA","Candidate",LocalDate.of(2026,11,1),TARGET,"2");
        assertEquals(0,daily.getSourceMonthConservation(f.batch,f.run).mismatchCount);
        var candidateCheck=daily.getSourceMonthConservation(f.batch,candidate);
        assertEquals(0,candidateCheck.totalDemands);
        assertEquals(1,candidateCheck.mismatchCount);
        decimal("2",candidateCheck.mismatches.get(0).planQuantity);
    }

    private void assertConservationProjection(Fixture f,int mismatches) {
        var expected=daily.getSourceMonthConservation(f.batch,f.run,daily.getDailyPlans(f.batch));
        var actual=daily.getSourceMonthConservation(f.batch,f.run);
        assertEquals(expected.totalDemands,actual.totalDemands);
        assertEquals(mismatches,actual.mismatchCount);
        assertEquals(expected.mismatchCount,actual.mismatchCount);
        for(int i=0;i<expected.mismatches.size();i++) {
            var left=expected.mismatches.get(i);
            var right=actual.mismatches.get(i);
            assertEquals(left.demandKey,right.demandKey);
            assertEquals(0,left.demandQuantity.compareTo(right.demandQuantity));
            assertEquals(0,left.planQuantity.compareTo(right.planQuantity));
        }
    }

    @Test void directDistributionRejectsCompletedVersionsForeignInputsAndRepeatedResults() {
        var f=fixture("167.500001");
        Long calendar=batches.selectById(f.batch).getProductionCalendarVersionId();
        var before=factValues(daily.getDailyPlans(f.batch));
        for(String status:List.of("READY","PUBLISHED","SUPERSEDED","CANCELLED","CALCULATING")) {
            jdbc.update("UPDATE mrp_calc_batch SET status=? WHERE id=?",status,f.batch);
            assertThrows(IllegalStateException.class,()->planningController.distribute(f.batch,f.run,calendar,null));
            assertEquals(before,factValues(daily.getDailyPlans(f.batch)));
        }
        jdbc.update("UPDATE mrp_calc_batch SET status='WAITING_DAILY_DISTRIBUTION' WHERE id=?",f.batch);
        assertThrows(IllegalArgumentException.class,()->planningController.distribute(f.batch,f.run,calendar+1000,null));
        assertThrows(IllegalArgumentException.class,()->planningController.distribute(f.batch,f.run,calendar,1000L));
        assertThrows(IllegalStateException.class,()->planningController.distribute(f.batch,f.run+1000,calendar,null));
        assertThrows(IllegalStateException.class,()->planningController.distribute(f.batch,f.run,calendar,null));
        assertEquals(before,factValues(daily.getDailyPlans(f.batch)));

        jdbc.update("DELETE FROM mrp_daily_plan_detail WHERE run_id=?",f.run);
        jdbc.update("INSERT INTO mrp_unallocated_quantity(run_id,batch_id,demand_key,source_date,attempted_schedule_date,quantity,reason_code,status) VALUES(?,?,'test','2026-10-01','2026-10-01',1,'TEST','PENDING')",f.run,f.batch);
        assertThrows(IllegalStateException.class,()->planningController.distribute(f.batch,f.run,calendar,null));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_unallocated_quantity WHERE run_id=?",Integer.class,f.run));
        jdbc.update("DELETE FROM mrp_unallocated_quantity WHERE run_id=?",f.run);

        // Distribution requires every calendar day in each target business week,
        // including non-production dates. The linked-transfer fixture has only two days.
        for(LocalDate date=LocalDate.of(2026,9,22);!date.isAfter(LocalDate.of(2026,10,14));date=date.plusDays(1)) {
            if(date.equals(SOURCE)||date.equals(TARGET)) continue;
            jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,?,1,60,'台')",calendar,f.factory,date);
        }
        var result=planningController.distribute(f.batch,f.run,calendar,null).getBody().data();
        assertFalse(result.details.isEmpty());
        assertTrue(result.unallocated.isEmpty());
        var allocated=factValues(daily.getDailyPlans(f.batch));
        assertThrows(IllegalStateException.class,()->planningController.distribute(f.batch,f.run,calendar,null));
        assertEquals(allocated,factValues(daily.getDailyPlans(f.batch)));
        assertEquals(0,daily.getSourceMonthConservation(f.batch).mismatchCount);
        assertEquals("READY",orchestrator.completeDistribution(f.batch).getStatus());
        assertEquals(allocated,factValues(daily.getDailyPlans(f.batch)));
        assertThrows(IllegalStateException.class,()->orchestrator.completeDistribution(f.batch));
        assertEquals(allocated,factValues(daily.getDailyPlans(f.batch)));
    }

    @Test void publicationFreezesEffectiveFractionalAndZeroFactsAfterLinkedMove() {
        var f=fixture("167.500001");
        completePublicationCalendar(f);
        String stale=orchestrator.capacityReview(f.batch).fingerprint();
        var in=new LinkedDailyTransferService.Input(f.factory+"|ROOT|ROOT|MACHINE_OUTPUT|SELF_MADE|Machine",SOURCE,TARGET,new BigDecimal("100"),f.run);
        var preview=linked.preview(f.batch,in);
        linked.commit(f.batch,new LinkedDailyTransferService.Commit(in.rowKey(),SOURCE,TARGET,in.quantity(),f.run,preview.fingerprint(),"发布守恒验证",amounts(preview)));
        var before=factValues(daily.getDailyPlans(f.batch));
        assertThrows(com.mrp.common.exception.ConcurrentChangeException.class,()->orchestrator.publish(f.batch,stale));
        assertEquals("READY",batches.selectById(f.batch).getStatus());
        var published=orchestrator.publish(f.batch,orchestrator.capacityReview(f.batch).fingerprint());
        assertNotEquals(f.run,published.getActiveRunId());
        assertEquals(before,factValues(daily.getDailyPlans(published.getId())));
        assertEquals(4,before.size()); // zero source rows and virtual target rows survive publication
        assertEquals(0,daily.getSourceMonthConservation(published.getId()).mismatchCount);
        assertEquals("SUPERSEDED",batches.selectById(f.batch).getStatus());
        assertEquals(published.getId(),batches.selectById(f.batch).getSupersededByBatchId());
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_batch_business_week_scope WHERE batch_id=?",Integer.class,published.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_override WHERE batch_id=?",Integer.class,published.getId()));
        assertNotNull(published.getResultChecksum());
        jdbc.update("UPDATE mrp_daily_plan_override SET manual_quantity=0 WHERE batch_id=?",f.batch);
        assertEquals(before,factValues(daily.getDailyPlans(published.getId())));
    }

    @Test void publicationWritesAcrossThousandRowBoundaryAndRollsBackEveryChunkOnLateFailure() {
        var f=fixture("167.500001");
        completePublicationCalendar(f);
        List<Object[]> rows=new ArrayList<>();
        for(int i=0;i<1100;i++) rows.add(new Object[]{f.run,f.batch,f.factory,"Chunk"+i,UUID.randomUUID().toString()});
        jdbc.batchUpdate("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,is_production_day,row_key) VALUES(?,?,?,'ROOT','LEAF','PCBA','SELF_MADE',?,'2026-10-01','2026-09-30','2026-09-30',1,1,?)",rows);
        jdbc.update("UPDATE mrp_terminal_demand SET quantity=quantity+1100 WHERE run_id=? AND terminal_type='PCBA'",f.run);
        var before=factValues(daily.getDailyPlans(f.batch));
        int batchesBefore=jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_batch",Integer.class);
        int runsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_run",Integer.class);
        int factsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_detail",Integer.class);
        // ROOT sorts after LEAF: this failure occurs after a complete 1000-row chunk was inserted.
        String machineKey=jdbc.queryForObject("SELECT row_key FROM mrp_daily_plan_detail WHERE run_id=? AND terminal_type='MACHINE_OUTPUT'",String.class,f.run);
        jdbc.execute("ALTER TABLE mrp_daily_plan_detail ADD CONSTRAINT test_publish_late_failure CHECK(row_key <> '"+machineKey+"' OR batch_id="+f.batch+")");
        String fingerprint=orchestrator.capacityReview(f.batch).fingerprint();
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,()->orchestrator.publish(f.batch,fingerprint));
        } finally { jdbc.execute("ALTER TABLE mrp_daily_plan_detail DROP CHECK test_publish_late_failure"); }
        assertEquals(batchesBefore,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_batch",Integer.class));
        assertEquals(runsBefore,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_run",Integer.class));
        assertEquals(factsBefore,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_detail",Integer.class));
        assertEquals("READY",batches.selectById(f.batch).getStatus());
        assertNull(batches.selectById(f.batch).getSupersededByBatchId());
        var published=orchestrator.publish(f.batch,fingerprint);
        assertEquals(1102,daily.getDailyPlans(published.getId()).size());
        assertEquals(before,factValues(daily.getDailyPlans(published.getId())));
        assertEquals(0,daily.getSourceMonthConservation(published.getId()).mismatchCount);
    }

    private List<String> factValues(List<MrpDailyPlanDetail> facts) {
        return facts.stream().map(p->Arrays.asList(p.getFactoryCode(),p.getRootMaterialCode(),p.getTerminalMaterialCode(),
                p.getTerminalType(),p.getSupplyMode(),p.getPathGroupKey(),p.getSourceMonth(),p.getSourceDate(),
                p.getScheduleDate(),p.getIsProductionDay(),p.getRowKey(),p.getEffectiveQuantity().stripTrailingZeros().toPlainString()).toString()).sorted().toList();
    }

    private void completePublicationCalendar(Fixture f) {
        Long calendar=batches.selectById(f.batch).getProductionCalendarVersionId();
        // Publication checks all six forecast months, including their preceding weeks.
        // Keep the minimal two-day fixture for transfer and distribution rejection tests.
        for(LocalDate date=LocalDate.of(2026,9,22);date.isBefore(LocalDate.of(2027,4,1));date=date.plusDays(1)) {
            if(date.equals(SOURCE)||date.equals(TARGET)) continue;
            jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,?,1,60,'台')",calendar,f.factory,date);
        }
    }

    private Fixture fixture(String terminal) {
        String factory="爱培科",other="永惠";
        int next=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM production_calendar_version",Integer.class);
        long calendar=insert("INSERT INTO production_calendar_version(version_no,factory_code,status) VALUES(?,'ALL','PUBLISHED')",next);
        for(String plant:List.of(factory,other)) for(LocalDate date:List.of(SOURCE,TARGET)) jdbc.update(
                "INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,?,1,60,'台')",calendar,plant,date);
        jdbc.update("INSERT INTO mrp_calendar_material_unit(calendar_version_id,material_code,unit) VALUES(?,'LEAF','件')",calendar);
        long forecast=insert("INSERT INTO forecast_version(version_no,status) VALUES(1,'IMPORTED')");
        for(int m=0;m<6;m++) jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,plan_month,forecast_qty) VALUES(?,?,'ROOT',?,100)",forecast,factory,LocalDate.of(2026,10,1).plusMonths(m));
        long historicalBom=insert("INSERT INTO bom_snapshot(source_version,status) VALUES('integration-fixture','READY')");
        long batch=insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,production_calendar_version_id,forecast_version_id,bom_snapshot_id,rule_version) VALUES('ALL','READY',1,?,?,?,'v4-rolling-business-weeks')",calendar,forecast,historicalBom);
        long run=insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')",batch);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?",run,batch);
        var f=new Fixture(batch,run,factory,other);
        for(int m=-1;m<6;m++) {
            LocalDate month=LocalDate.of(2026,10,1).plusMonths(m);
            int version=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM business_week_version WHERE business_month=?",Integer.class,month);
            long weeks=insert("INSERT INTO business_week_version(version_no,business_month,status) VALUES(?,?,'PUBLISHED')",version,month);
            for(int w=0;w<4;w++) jdbc.update("INSERT INTO business_week_detail(version_id,week_code,start_date,end_date,sort_no) VALUES(?,?,?,?,?)",weeks,"W"+(w+1),month.plusDays(w*7),w==3?month.plusMonths(1).minusDays(1):month.plusDays(w*7+6),w+1);
            jdbc.update("INSERT INTO mrp_batch_business_week_scope(batch_id,business_month,business_week_version_id) VALUES(?,?,?)",batch,month,weeks);
        }
        fact(f,factory,"ROOT","MACHINE_OUTPUT","Machine",LocalDate.of(2026,10,1),SOURCE,"100");
        fact(f,factory,"LEAF","PCBA","Leaf",LocalDate.of(2026,10,1),SOURCE,terminal);
        for(String type:List.of("MACHINE_OUTPUT","PCBA")) jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES(?,?,?,'ROOT',?,?,'SELF_MADE','2026-10-01',?)",run,batch,factory,type.equals("PCBA")?"LEAF":"ROOT",type,type.equals("PCBA")?terminal:"100");
        return f;
    }
    private void fact(Fixture f,String factory,String terminal,String type,String path,LocalDate month,LocalDate date,String qty) {
        jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,row_key) VALUES(?,?,?,'ROOT',?,?,'SELF_MADE',?,?,?,?,?,?)",f.run,f.batch,factory,terminal,type,path,month,date,date,qty,UUID.randomUUID().toString());
    }
    private LinkedDailyTransferService.Input input(Fixture f){return new LinkedDailyTransferService.Input(f.factory+"|ROOT|ROOT|MACHINE_OUTPUT|SELF_MADE|Machine",SOURCE,TARGET,new BigDecimal("50"),f.run);}
    private List<LinkedDailyTransferService.Amount> amounts(LinkedDailyTransferService.Preview preview){return preview.lines().stream().map(l->new LinkedDailyTransferService.Amount(l.rowKey(),new BigDecimal(l.suggestedQuantity()))).toList();}
    private LinkedDailyTransferService.Commit commit(Fixture f,LinkedDailyTransferService.Preview p,List<LinkedDailyTransferService.Amount> lines){var in=input(f);return new LinkedDailyTransferService.Commit(in.rowKey(),in.sourceDate(),in.targetDate(),in.quantity(),in.expectedRunId(),p.fingerprint(),"整数联动验证",lines);}
    private int overrideCount(Fixture f){return jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_override WHERE batch_id=?",Integer.class,f.batch);}
    private BigDecimal quantity(Fixture f,String type,LocalDate date){return daily.getDailyPlans(f.batch).stream().filter(p->p.getTerminalType().equals(type)&&p.getScheduleDate().equals(date)).map(MrpDailyPlanDetail::getEffectiveQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private long insert(String sql,Object... args){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
    private static void decimal(String expected,BigDecimal actual){assertEquals(0,new BigDecimal(expected).compareTo(actual));}
    private record Fixture(Long batch,Long run,String factory,String other){}
}
