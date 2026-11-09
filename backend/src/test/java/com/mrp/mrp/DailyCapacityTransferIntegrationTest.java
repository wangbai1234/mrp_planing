package com.mrp.mrp;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.domain.MrpDailyPlanOverride;
import com.mrp.mrp.repository.ConfigMasterMapper;
import com.mrp.mrp.service.BatchOrchestratorService;
import com.mrp.mrp.service.MrpExecutionService;
import com.mrp.mrp.service.DailyCapacityService;
import com.mrp.mrp.service.DailyPlanningService;
import com.mrp.mrp.service.ConfigSnapshotService;
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
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL and Spring transaction proxy; no surrounding test transaction masks rollback. */
class DailyCapacityTransferIntegrationTest extends BaseIntegrationTest {
    private static final LocalDate SOURCE = LocalDate.of(2026, 9, 30);
    private static final LocalDate TARGET = LocalDate.of(2026, 10, 1);
    @Autowired DailyPlanningService planning;
    @Autowired DailyCapacityService capacity;
    @Autowired MrpExecutionService execution;
    @Autowired BatchOrchestratorService batches;
    @Autowired ConfigMasterMapper calendars;
    @Autowired ConfigSnapshotService snapshots;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;

    @BeforeEach void authenticate() { login(); }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void octoberOutputWeeklySummaryIncludesLeadTimeSeptemberAndIgnoresLaterMasterChanges() {
        var f = machineFixture(false);
        var october = LocalDate.of(2026, 10, 1);
        var september = october.minusMonths(1);
        maintainLeadTimeWeeks(september, october);
        var snapshot = snapshots.snapshot(f.factory, snapshots.dailyPlanningBusinessMonths(List.of(october)), 9L);
        assertEquals(List.of(october, september), new ArrayList<>(snapshot.businessWeekVersionIds().keySet()));
        jdbc.update("DELETE FROM mrp_batch_business_week_scope WHERE batch_id=?", f.batch);
        snapshot.businessWeekVersionIds().forEach((month, version) -> jdbc.update(
                "INSERT INTO mrp_batch_business_week_scope(batch_id,business_month,business_week_version_id) VALUES(?,?,?)", f.batch, month, version));
        jdbc.update("DELETE FROM mrp_daily_plan_detail WHERE batch_id=?", f.batch);
        jdbc.update("DELETE FROM mrp_terminal_demand WHERE batch_id=? AND terminal_type<>'MACHINE_OUTPUT'", f.batch);
        jdbc.update("UPDATE mrp_terminal_demand SET quantity=1800 WHERE batch_id=?", f.batch);
        jdbc.update("DELETE FROM production_calendar_day WHERE version_id=?", f.calendar);
        for (int day = -7; day < october.lengthOfMonth(); day++) jdbc.update(
                "INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,?,1,1000,'台')", f.calendar, f.factory, october.plusDays(day));
        var distribution = planning.distribute(f.batch, f.run, f.calendar, snapshot.materialPolicyVersionId());
        assertEquals(31, distribution.details.size());
        assertTrue(distribution.unallocated.isEmpty());
        assertEquals(october.minusDays(7), distribution.details.getFirst().getScheduleDate());
        decimal("1800", distribution.details.stream().map(MrpDailyPlanDetail::getSystemQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        var frozenWeeks = planning.getWeeklySummary(f.batch);
        decimal("1800", frozenWeeks.stream().map(w -> w.totalQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertTrue(frozenWeeks.stream().anyMatch(w -> w.weekStart.isBefore(october) && w.totalQuantity.signum() > 0));
        var before = jdbc.queryForList("SELECT d.* FROM business_week_detail d JOIN mrp_batch_business_week_scope s ON s.business_week_version_id=d.version_id WHERE s.batch_id=? ORDER BY d.id", f.batch);
        jdbc.update("DELETE FROM business_week WHERE business_month IN (?,?)", september, october);
        jdbc.update("INSERT INTO business_week(business_month,week_code,start_date,end_date,sort_no) VALUES(?,'CHANGED',?,?,1)", october, october.plusDays(20), october.plusDays(30));
        var after = planning.getWeeklySummary(f.batch);
        assertEquals(frozenWeeks.stream().map(w -> w.weekKey + "|" + w.totalQuantity).toList(), after.stream().map(w -> w.weekKey + "|" + w.totalQuantity).toList());
        assertEquals(before, jdbc.queryForList("SELECT d.* FROM business_week_detail d JOIN mrp_batch_business_week_scope s ON s.business_week_version_id=d.version_id WHERE s.batch_id=? ORDER BY d.id", f.batch));
        jdbc.update("DELETE FROM business_week WHERE business_month IN (?,?)", september, october);
    }

    @Test void revisionAddsMaintainedLeadTimeMonthWithoutChangingHistoricalScopes() {
        var f = fixture(true);
        var october = LocalDate.of(2026, 10, 1);
        var september = october.minusMonths(1);
        maintainLeadTimeWeeks(september, october);
        long forecast = insert("INSERT INTO forecast_version(version_no,status) VALUES(1,'IMPORTED')");
        jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,plan_month,forecast_qty) VALUES(?,?,'ROOT',?,1800)", forecast, f.factory, october);
        jdbc.update("UPDATE mrp_calc_batch SET status='PUBLISHED',forecast_version_id=? WHERE id=?", forecast, f.batch);
        var sourceScopes = jdbc.queryForList("SELECT business_month,business_week_version_id FROM mrp_batch_business_week_scope WHERE batch_id=?", f.batch);
        var revision = batches.revise(f.batch);
        assertEquals("v3-whole-machine-capacity", revision.getRuleVersion());
        assertEquals(sourceScopes, jdbc.queryForList("SELECT business_month,business_week_version_id FROM mrp_batch_business_week_scope WHERE batch_id=?", f.batch));
        assertEquals(sourceScopes.getFirst().get("business_week_version_id"), jdbc.queryForObject(
                "SELECT business_week_version_id FROM mrp_batch_business_week_scope WHERE batch_id=? AND business_month=?", Long.class, revision.getId(), october));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_batch_business_week_scope WHERE batch_id=?", Integer.class, revision.getId()));
        long addedVersion = jdbc.queryForObject("SELECT business_week_version_id FROM mrp_batch_business_week_scope WHERE batch_id=? AND business_month=?", Long.class, revision.getId(), september);
        var before = jdbc.queryForList("SELECT * FROM business_week_detail WHERE version_id=?", addedVersion);
        jdbc.update("UPDATE business_week SET start_date=?,end_date=? WHERE business_month=?", september, september.plusDays(1), september);
        assertEquals(before, jdbc.queryForList("SELECT * FROM business_week_detail WHERE version_id=?", addedVersion));
        assertTrue(planning.getBusinessWeeks(revision.getId(), october.minusDays(7), october.plusDays(23)).stream()
                .anyMatch(w -> september.equals(w.getBusinessMonth()) && october.minusDays(7).equals(w.getStartDate())));
        jdbc.update("DELETE FROM business_week WHERE business_month IN (?,?)", september, october);
    }

    private void maintainLeadTimeWeeks(LocalDate september, LocalDate october) {
        jdbc.update("DELETE FROM business_week WHERE business_month IN (?,?)", september, october);
        jdbc.update("INSERT INTO business_week(business_month,week_code,start_date,end_date,sort_no) VALUES(?,'S4',?,?,1)", september, october.minusDays(7), october.minusDays(1));
        for (int week = 0; week < 5; week++) jdbc.update(
                "INSERT INTO business_week(business_month,week_code,start_date,end_date,sort_no) VALUES(?,?,?,?,?)", october, "O" + (week + 1), october.plusDays(week * 7L), october.plusDays(Math.min(week * 7L + 6, 30)), week + 1);
    }

    @Test void wholeMachineTransferIsIndependentAndPublicationAndRevisionPreserveBothDemandGroups() {
        var f = machineFixture(true);
        var before = jdbc.queryForList("SELECT system_quantity FROM mrp_daily_plan_detail WHERE batch_id=? AND terminal_type='PCBA' ORDER BY schedule_date",f.batch);
        var review = batches.capacityReview(f.batch); assertEquals("OVERLOAD",review.loads().getFirst().status());
        planning.transfer(f.batch,machineRequest(f,"1200","700","200",-1,-1));
        var outputs = planning.getDailyPlans(f.batch).stream().filter(p -> "MACHINE_OUTPUT".equals(p.getTerminalType())).toList();
        decimal("1000",outputs.stream().filter(p -> SOURCE.equals(p.getScheduleDate())).findFirst().orElseThrow().getEffectiveQuantity());
        decimal("900",outputs.stream().filter(p -> TARGET.equals(p.getScheduleDate())).findFirst().orElseThrow().getEffectiveQuantity());
        assertEquals(before,jdbc.queryForList("SELECT system_quantity FROM mrp_daily_plan_detail WHERE batch_id=? AND terminal_type='PCBA' ORDER BY schedule_date",f.batch));
        assertEquals(0,planning.getSourceMonthConservation(f.batch).mismatchCount);
        var weekly = planning.getWeeklySummary(f.batch); assertEquals(1,weekly.size()); decimal("1900",weekly.getFirst().totalQuantity);
        assertEquals("台",weekly.getFirst().materialUnit); assertEquals("MACHINE_OUTPUT",weekly.getFirst().view);
        decimal("4800",planning.getMonthlySummary(f.batch,"TERMINAL").getFirst().totalQuantity);
        execution.lockDay(f.batch,SOURCE);
        assertThrows(com.mrp.common.exception.ConcurrentChangeException.class,() -> batches.publish(f.batch,review.fingerprint()));
        var published = batches.publish(f.batch);
        assertEquals("v3-whole-machine-capacity",published.getRuleVersion());
        assertEquals(2,planning.getSourceMonthConservation(published.getId()).totalDemands);
        assertEquals(0,planning.getSourceMonthConservation(published.getId()).mismatchCount);
        decimal("1900",planning.getMonthlySummary(published.getId()).getFirst().totalQuantity);
        var revision = batches.revise(published.getId());
        assertEquals("v3-whole-machine-capacity",revision.getRuleVersion());
        assertTrue(execution.isDateLockedOrExecuted(revision.getId(),SOURCE));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?",Integer.class,revision.getId(),SOURCE));
    }

    @Test void wholeMachineTransferRejectsFractionalQuantityWithoutWritingOverrides() {
        var f = machineFixture(true);
        var error = assertThrows(IllegalArgumentException.class,() -> planning.transfer(f.batch,machineRequest(f,"1200","700","0.5",-1,-1)));
        assertTrue(error.getMessage().contains("整数台")); assertNoOverrides(f);
    }

    @Test void wholeMachineTransferToBlankDatePreservesIndependentDemandConservation() {
        var f = machineFixture(false);
        planning.transfer(f.batch,machineRequest(f,"1200","0","200",-1,-1));
        assertEquals(0,planning.getSourceMonthConservation(f.batch).mismatchCount);
        assertEquals(2,planning.getDailyPlans(f.batch).stream().filter(p -> "MACHINE_OUTPUT".equals(p.getTerminalType())).count());
        decimal("1200",planning.getMonthlySummary(f.batch).getFirst().totalQuantity);
    }

    @Test void transferChangesEffectiveValuesAndWarningsWithoutChangingSystemFacts() {
        var f = fixture(true);
        var before = jdbc.queryForList("SELECT * FROM mrp_daily_plan_detail WHERE batch_id=? ORDER BY schedule_date", f.batch);
        assertEquals("OVERLOAD", loads(f).get(0).status());
        planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run));
        decimal("1000", quantity(f, SOURCE)); decimal("900", quantity(f, TARGET));
        assertEquals(before, jdbc.queryForList("SELECT * FROM mrp_daily_plan_detail WHERE batch_id=? ORDER BY schedule_date", f.batch));
        assertEquals("NORMAL", loads(f).get(0).status()); assertEquals("NORMAL", loads(f).get(1).status());
        assertConserved(f, "1900");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='TRANSFER' AND resource_id=?", Integer.class, f.batch.toString()));
    }

    @Test void transferToBlankDayProjectsVirtualFactAndConservesAcrossMonthBoundary() {
        var f = fixture(false);
        planning.transfer(f.batch, request(f, "1200", "0", -1, -1, f.run));
        decimal("1000", quantity(f, SOURCE)); decimal("200", quantity(f, TARGET));
        var projected = planning.getDailyPlans(f.batch).stream().filter(p -> TARGET.equals(p.getScheduleDate())).findFirst().orElseThrow();
        assertNull(projected.getId()); decimal("0", projected.getSystemQuantity());
        assertEquals(LocalDate.of(2026, 10, 1), projected.getSourceMonth());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_daily_plan_detail WHERE batch_id=?", Integer.class, f.batch));
        assertConserved(f, "1200");
    }

    @Test void lockedAndExecutedSourceOrTargetRejectWithNoWrites() {
        for (String status : List.of("LOCKED", "EXECUTED")) for (LocalDate date : List.of(SOURCE, TARGET)) {
            var f = fixture(true);
            jdbc.update("INSERT INTO mrp_daily_execution(batch_id,row_key,schedule_date,execution_status) VALUES(?,?,?,?)", f.batch, "locked-row", date, status);
            var error = assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run)));
            assertTrue(error.getMessage().contains("锁定")); assertNoOverrides(f);
        }
    }

    @Test void staleRunOrQuantitiesRejectWithoutWrites() {
        var f = fixture(true);
        assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run + 1)));
        assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1199", "700", -1, -1, f.run)));
        assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1200", "699", -1, -1, f.run)));
        assertNoOverrides(f);
    }

    @Test void staleTargetRevisionRollsBackNewSourceInsert() {
        var f = fixture(true);
        assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1200", "700", -1, 0, f.run)));
        assertNoOverrides(f); decimal("1200", quantity(f, SOURCE)); decimal("700", quantity(f, TARGET));
    }

    @Test void staleTargetRevisionRollsBackExistingSourceUpdate() {
        var f = fixture(true);
        planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run));
        var before = jdbc.queryForList("SELECT * FROM mrp_daily_plan_override WHERE batch_id=? ORDER BY schedule_date", f.batch);
        assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1000", "900", 0, -1, f.run)));
        assertEquals(before, jdbc.queryForList("SELECT * FROM mrp_daily_plan_override WHERE batch_id=? ORDER BY schedule_date", f.batch));
        assertConserved(f, "1900");
    }

    @Test void sameExpectedValuesPermitOnlyOneConcurrentTransfer() throws Exception {
        var f = fixture(true); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> operation = () -> {
            login(); ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start barrier timed out");
                planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run)); return true;
            } catch (IllegalStateException stale) {
                assertTrue(stale.getMessage().contains("已变化") || stale.getMessage().contains("已被修改")); return false;
            } finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first = executor.submit(operation); var second = executor.submit(operation);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            decimal("1000", quantity(f, SOURCE)); decimal("900", quantity(f, TARGET)); assertConserved(f, "1900");
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void restTargetAndPublishedBatchReject() {
        var f = fixture(true);
        jdbc.update("UPDATE production_calendar_day SET is_production_day=0,daily_capacity=0 WHERE version_id=? AND calendar_date=?", f.calendar, TARGET);
        assertThrows(IllegalArgumentException.class, () -> planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run)));
        jdbc.update("UPDATE mrp_calc_batch SET status='PUBLISHED' WHERE id=?", f.batch);
        assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run)));
        assertNoOverrides(f);
    }

    @Test void lockingAfterTransferFreezesEffectiveQuantityAndRemainsIdempotent() {
        var f = fixture(true);
        planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run));
        execution.lockDay(f.batch, SOURCE); execution.lockDay(f.batch, SOURCE);
        decimal("1000", jdbc.queryForObject("SELECT planned_quantity FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?", BigDecimal.class, f.batch, SOURCE));
        decimal("1200", jdbc.queryForObject("SELECT system_quantity FROM mrp_daily_plan_detail WHERE batch_id=? AND schedule_date=?", BigDecimal.class, f.batch, SOURCE));
        assertThrows(IllegalStateException.class, () -> planning.transfer(f.batch, request(f, "1000", "900", 0, 0, f.run)));
    }

    @Test void relockingAfterUnlockAndTransferRefreshesSnapshotThroughPublicationAndRevision() {
        var f = fixture(true);
        execution.lockDay(f.batch, SOURCE);
        decimal("1200", frozenQuantity(f.batch, SOURCE));
        execution.unlockDay(f.batch, SOURCE);
        planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run));
        execution.lockDay(f.batch, SOURCE);
        decimal("1000", frozenQuantity(f.batch, SOURCE));
        assertFrozenThroughPublicationAndRevision(f, "LOCKED");
    }

    @Test void confirmingExecutionAfterUnlockAndTransferRefreshesSnapshotThroughPublicationAndRevision() {
        var f = fixture(true);
        execution.lockDay(f.batch, SOURCE);
        decimal("1200", frozenQuantity(f.batch, SOURCE));
        execution.unlockDay(f.batch, SOURCE);
        planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run));
        var confirmed = execution.confirmExecution(f.batch, "source-row", new BigDecimal("1000"), 9L, "调整后确认");
        decimal("1000", confirmed.getPlannedQuantity()); decimal("1000", confirmed.getActualQuantity());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_calc_exception WHERE batch_id=? AND error_code='EXECUTION_QUANTITY_MISMATCH'", Integer.class, f.batch));
        assertFrozenThroughPublicationAndRevision(f, "EXECUTED");
    }

    private BigDecimal frozenQuantity(Long batchId, LocalDate date) {
        return jdbc.queryForObject("SELECT planned_quantity FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?", BigDecimal.class, batchId, date);
    }

    private void assertFrozenThroughPublicationAndRevision(Fixture f, String expectedStatus) {
        var published = batches.publish(f.batch);
        decimal("1000", frozenQuantity(published.getId(), SOURCE));
        assertEquals(expectedStatus, jdbc.queryForObject("SELECT execution_status FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?", String.class, published.getId(), SOURCE));
        var revision = batches.revise(published.getId());
        decimal("1000", frozenQuantity(revision.getId(), SOURCE));
        assertEquals(expectedStatus, jdbc.queryForObject("SELECT execution_status FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?", String.class, revision.getId(), SOURCE));
        assertTrue(execution.isDateLockedOrExecuted(revision.getId(), SOURCE));
        decimal("1000", planning.getDailyPlans(published.getId()).stream().filter(p -> SOURCE.equals(p.getScheduleDate())).findFirst().orElseThrow().getSystemQuantity());
    }

    @Test void blankTargetCanBeLockedAndPublicationAndRevisionPreserveThatProtection() {
        var f = fixture(false);
        planning.transfer(f.batch, request(f, "1200", "0", -1, -1, f.run));
        execution.lockDay(f.batch, TARGET);
        decimal("200", jdbc.queryForObject("SELECT planned_quantity FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?", BigDecimal.class, f.batch, TARGET));
        String rowKey = jdbc.queryForObject("SELECT row_key FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?", String.class, f.batch, TARGET);
        var published = batches.publish(f.batch);
        var publishedTarget = planning.getDailyPlans(published.getId()).stream().filter(p -> TARGET.equals(p.getScheduleDate())).findFirst().orElseThrow();
        decimal("200", publishedTarget.getSystemQuantity()); assertEquals(rowKey, publishedTarget.getRowKey());
        assertTrue(execution.isDateLockedOrExecuted(published.getId(), TARGET));
        var revision = batches.revise(published.getId());
        assertTrue(execution.isDateLockedOrExecuted(revision.getId(), TARGET));
        decimal("200", jdbc.queryForObject("SELECT planned_quantity FROM mrp_daily_execution WHERE batch_id=? AND schedule_date=?", BigDecimal.class, revision.getId(), TARGET));
        assertConserved(new Fixture(published.getId(), published.getActiveRunId(), f.calendar, f.factory), "1200");
    }

    @Test void newDailyCapacityRuleRejectsSingleEndedOverrideAndRestore() {
        var f = fixture(true);
        var override = new MrpDailyPlanOverride(); override.setBatchId(f.batch); override.setFactoryCode(f.factory);
        override.setRootMaterialCode("ROOT"); override.setTerminalMaterialCode("LEAF"); override.setTerminalType("PCBA");
        override.setSupplyMode("SELF_MADE"); override.setPathGroupKey("PATH"); override.setScheduleDate(SOURCE);
        override.setManualQuantity(new BigDecimal("1000")); override.setReason("单端调整");
        assertThrows(IllegalStateException.class, () -> planning.saveOverride(override)); assertNoOverrides(f);
        planning.transfer(f.batch, request(f, "1200", "700", -1, -1, f.run));
        var sourceOverride = planning.getOverrides(f.batch).stream().filter(o -> SOURCE.equals(o.getScheduleDate())).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> planning.restoreAuto(sourceOverride.getId()));
        assertEquals(2, planning.getOverrides(f.batch).size()); assertConserved(f, "1900");
    }

    @Test void calendarSqlFiltersIncompleteLegacyRowsAndReturnsAuditNames() {
        var f = fixture(false); LocalDate first = LocalDate.of(2026, 10, 1), last = first.plusDays(6);
        long user = insert("INSERT INTO `user`(username,password_hash,display_name) VALUES(?,'unused','日历维护人')", f.factory);
        for (int i = 0; i < 6; i++) {
            Object quantity = i == 1 ? null : i == 2 || i == 5 ? "0" : "1000";
            String unit = i == 3 ? "   " : i == 4 || i == 5 ? null : "台";
            jdbc.update("INSERT INTO mrp_production_calendar(factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit,created_by,updated_by) VALUES(?,?,?,?,?,?,?)", f.factory, first.plusDays(i), i == 5 ? 0 : 1, quantity, unit, user, user);
        }
        var production = calendars.selectProductionCalendarPage(f.factory, first, last, null, "PRODUCTION", 0, 20);
        assertEquals(1, production.size()); assertEquals(first, production.getFirst().getCalendarDate());
        assertEquals("日历维护人", production.getFirst().getCreatedByName()); assertEquals("日历维护人", production.getFirst().getUpdatedByName());
        assertEquals(1, calendars.countProductionCalendarPage(f.factory, first, last, null, "PRODUCTION"));
        assertEquals(5, calendars.countProductionCalendarPage(f.factory, first, last, null, "UNCONFIGURED"));
        assertEquals(5, calendars.selectProductionCalendarPage(f.factory, first, last, null, "UNCONFIGURED", 0, 20).size());
        assertEquals(1, calendars.countProductionCalendarPage(f.factory, first, last, null, "REST"));
        var range = calendars.selectProductionCalendarRange(f.factory, first, last);
        assertEquals(6, range.size()); assertEquals("日历维护人", range.getFirst().getUpdatedByName());
    }

    private Fixture machineFixture(boolean populatedTarget) {
        var f = fixture(populatedTarget);
        jdbc.update("UPDATE mrp_calc_batch SET rule_version='v3-whole-machine-capacity' WHERE id=?",f.batch);
        jdbc.update("UPDATE production_calendar_day SET capacity_unit='台' WHERE version_id=?",f.calendar);
        jdbc.update("UPDATE mrp_daily_plan_detail SET terminal_material_code='ROOT',terminal_type='MACHINE_OUTPUT' WHERE batch_id=?",f.batch);
        jdbc.update("UPDATE mrp_terminal_demand SET terminal_material_code='ROOT',terminal_type='MACHINE_OUTPUT' WHERE batch_id=?",f.batch);
        for (var date : List.of(SOURCE,TARGET)) {
            jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,row_key) VALUES(?,?,?,'ROOT','LEAF','PCBA','SELF_MADE','TERMINAL_PATH','2026-10-01',?,?,2400,?)",f.run,f.batch,f.factory,date.plusDays(7),date,"terminal-"+date);
        }
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES(?,?,?,'ROOT','LEAF','PCBA','SELF_MADE','2026-10-01',4800)",f.run,f.batch,f.factory);
        return f;
    }
    private DailyPlanningService.TransferRequest machineRequest(Fixture f,String source,String target,String quantity,int sourceRevision,int targetRevision) {
        return new DailyPlanningService.TransferRequest(f.factory+"|ROOT|ROOT|MACHINE_OUTPUT|SELF_MADE|PATH",SOURCE,TARGET,new BigDecimal(quantity),"调整整机排产",f.run,new BigDecimal(source),new BigDecimal(target),sourceRevision,targetRevision);
    }
    private Fixture fixture(boolean populatedTarget) {
        String factory = "CAP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        int version = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM production_calendar_version", Integer.class);
        long calendar = insert("INSERT INTO production_calendar_version(version_no,factory_code,status) VALUES(?,?,'PUBLISHED')", version, factory);
        for (var date : List.of(SOURCE, TARGET)) jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,?,1,1000,'件')", calendar, factory, date);
        jdbc.update("INSERT INTO mrp_calendar_material_unit(calendar_version_id,material_code,unit) VALUES(?,'LEAF','件')", calendar);
        long historicalBom = insert("INSERT INTO bom_snapshot(source_version,status) VALUES('integration-fixture','READY')");
        long inventorySnapshot = insert("INSERT INTO inventory_snapshot(snapshot_date,status) VALUES('2026-10-01','TEST')");
        long batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,production_calendar_version_id,bom_snapshot_id,inventory_snapshot_id,rule_version) VALUES(?,'READY',1,?,?,?,'v2-daily-capacity')", factory, calendar, historicalBom, inventorySnapshot);
        long run = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')", batch);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?", run, batch);
        addFact(batch, run, factory, SOURCE, "1200", "source-row");
        if (populatedTarget) addFact(batch, run, factory, TARGET, "700", "target-row");
        jdbc.update("INSERT INTO mrp_terminal_demand(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,source_month,quantity) VALUES(?,?,?,'ROOT','LEAF','PCBA','SELF_MADE','2026-10-01',?)", run, batch, factory, populatedTarget ? "1900" : "1200");
        int weekVersion = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM business_week_version WHERE business_month='2026-10-01'", Integer.class);
        long weeks = insert("INSERT INTO business_week_version(version_no,business_month,status) VALUES(?,'2026-10-01','PUBLISHED')", weekVersion);
        jdbc.update("INSERT INTO business_week_detail(version_id,week_code,start_date,end_date,sort_no) VALUES(?,'W1','2026-09-28','2026-10-04',1)", weeks);
        jdbc.update("INSERT INTO mrp_batch_business_week_scope(batch_id,business_month,business_week_version_id) VALUES(?,'2026-10-01',?)", batch, weeks);
        return new Fixture(batch, run, calendar, factory);
    }
    private void addFact(long batch, long run, String factory, LocalDate date, String quantity, String rowKey) {
        jdbc.update("INSERT INTO mrp_daily_plan_detail(run_id,batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,source_month,source_date,schedule_date,system_quantity,row_key) VALUES(?,?,?,'ROOT','LEAF','PCBA','SELF_MADE','PATH','2026-10-01',?,?,?,?)", run, batch, factory, date.plusDays(7), date, quantity, rowKey);
    }
    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    private DailyPlanningService.TransferRequest request(Fixture f, String source, String target, int sourceRevision, int targetRevision, long run) {
        return new DailyPlanningService.TransferRequest(f.factory + "|ROOT|LEAF|PCBA|SELF_MADE|PATH", SOURCE, TARGET, new BigDecimal("200"), "调整超产能", run, new BigDecimal(source), new BigDecimal(target), sourceRevision, targetRevision);
    }
    private List<DailyCapacityService.DayLoad> loads(Fixture f) {
        var batch = new MrpCalcBatch(); batch.setFactoryCode(f.factory); batch.setProductionCalendarVersionId(f.calendar);
        return capacity.evaluate(batch, planning.getDailyPlans(f.batch), SOURCE, TARGET);
    }
    private BigDecimal quantity(Fixture f, LocalDate date) {
        return planning.getDailyPlans(f.batch).stream().filter(p -> date.equals(p.getScheduleDate())).map(MrpDailyPlanDetail::getEffectiveQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private void assertConserved(Fixture f, String expected) {
        decimal(expected, planning.getDailyPlans(f.batch).stream().map(MrpDailyPlanDetail::getEffectiveQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
        var weekly = planning.getWeeklySummary(f.batch); assertEquals(1, weekly.size()); decimal(expected, weekly.get(0).totalQuantity);
        var monthly = planning.getMonthlySummary(f.batch); assertEquals(1, monthly.size()); decimal(expected, monthly.get(0).totalQuantity);
        assertEquals(0, planning.getSourceMonthConservation(f.batch).mismatchCount);
    }
    private void assertNoOverrides(Fixture f) { assertTrue(planning.getOverrides(f.batch).isEmpty()); }
    private static void login() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(9L, "capacity-test"), null, List.of()));
    }
    private static void decimal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private record Fixture(Long batch, Long run, Long calendar, String factory) {}
}
