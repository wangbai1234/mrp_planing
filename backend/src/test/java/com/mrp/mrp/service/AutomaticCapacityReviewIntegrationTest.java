package com.mrp.mrp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpDailyPlanDetail;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpDailyPlanDetailMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Independent Testcontainers database; never touches retained runtime plans. */
class AutomaticCapacityReviewIntegrationTest extends BaseIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired MrpDailyPlanDetailMapper facts;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired BatchOrchestratorService orchestrator;
    @Autowired DailyPlanningService daily;
    @Autowired DailyCapacityService capacity;
    @Autowired ObjectMapper json;
    @SpyBean AutomaticCapacityReviewService automatic;

    @Test void v4BothFactoriesAllFactDatesZerosUnicodeAndScalesMatchEstablishedReview() throws Exception {
        var b = fixture(ProductionCapacityRules.VERSION);
        add(b, "爱培科", "整机:𠮷\n", "MACHINE_OUTPUT", "10.000000", DAY, "A");
        add(b, "爱培科", "整机2", "MACHINE_OUTPUT", "20", DAY, "B");
        add(b, "永惠", "整机3", "MACHINE_OUTPUT", "0", DAY.plusDays(1), "C");
        add(b, "永惠", "部件", "PCBA", "0.000001", DAY.minusDays(2), "D");
        add(b, "爱培科", "空日", "PCBA", "0", DAY.plusDays(3), "E");
        assertCompatible(b);
        var review = orchestrator.capacityReview(b.getId());
        assertEquals(12, review.loads().size());
        assertEquals("30.000000", review.loads().stream().filter(d -> "爱培科".equals(d.factoryCode())
                && DAY.equals(d.date())).findFirst().orElseThrow().load().toPlainString());
    }

    @Test void v3UsesSameFingerprintAndFrozenCapacitySemantics() throws Exception {
        var b = fixture(ProductionCapacityRules.WHOLE_MACHINE_V3);
        add(b, "爱培科", "ROOT", "MACHINE_OUTPUT", "100", DAY, "A");
        jdbc.update("UPDATE production_calendar_day SET capacity_unit='件' WHERE version_id=?", b.getProductionCalendarVersionId());
        assertCompatible(b);
        assertTrue(orchestrator.capacityReview(b.getId()).loads().stream().allMatch(d -> "UNASSESSED".equals(d.status())));
    }

    @Test void emptyAndNoActiveRunRemainEmptyWithStableFingerprint() throws Exception {
        var b = fixture(ProductionCapacityRules.VERSION);
        assertCompatible(b);
        assertTrue(orchestrator.capacityReview(b.getId()).loads().isEmpty());
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=NULL WHERE id=?", b.getId());
        b = batches.selectById(b.getId());
        assertNull(automatic.read(b));
        assertCompatible(b);
    }

    @Test void onlyTerminalFactsStillDefineCapacityDateRange() throws Exception {
        var b = fixture(ProductionCapacityRules.VERSION);
        add(b, "爱培科", "LEAF", "PCBA", "0", DAY.minusDays(2), "A");
        add(b, "永惠", "LEAF2", "RAW_MATERIAL", "0.999999", DAY.plusDays(3), "B");
        assertCompatible(b);
        assertEquals(12, orchestrator.capacityReview(b.getId()).loads().size());
        assertTrue(orchestrator.capacityReview(b.getId()).loads().stream().allMatch(d -> d.load().signum() == 0));
    }

    @Test void zeroOverrideAndVirtualDateTransferRetainEstablishedProjection() throws Exception {
        var b = fixture(ProductionCapacityRules.VERSION);
        add(b, "爱培科", "ROOT", "MACHINE_OUTPUT", "10", DAY, "A");
        for (var date : List.of(DAY, DAY.plusDays(2))) jdbc.update(
                "INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity) VALUES(?,'爱培科','ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','A',?,?)",
                b.getId(), date, DAY.equals(date) ? 0 : 10);
        assertNull(automatic.read(b));
        assertCompatible(b);
        assertEquals(2, daily.getDailyPlans(b.getId()).size());
    }

    @Test void historicalTerminalRulesRemainOnOriginalPath() throws Exception {
        var b = fixture("v2-daily-capacity");
        add(b, "爱培科", "LEAF", "PCBA", "1.234567", DAY, "A");
        assertNull(automatic.read(b));
        assertCompatible(b);
    }

    @Test void twentyThousandFactsMatchWholePlanWithoutRetainingDomainList() throws Exception {
        var b = fixture(ProductionCapacityRules.VERSION);
        var group = new ArrayList<MrpDailyPlanDetail>();
        for (int i = 0; i < 20000; i++) {
            var p = fact(b, i % 2 == 0 ? "爱培科" : "永惠", "ROOT" + i,
                    i % 5 == 0 ? "MACHINE_OUTPUT" : "PCBA", i % 5 == 0 ? "10" : "0.123456",
                    DAY.plusDays(i % 6 - 2), "PATH" + i);
            group.add(p);
            if (group.size() == 1000) { facts.insertBatch(group); group.clear(); }
        }
        assertCompatible(b);
        var projection = automatic.read(b);
        assertNotNull(projection);
        assertTrue(projection.machineLoads().size() <= 12);
    }

    @Test void concurrentRunAndOverrideCommitCannotMixSnapshotsWithinReview() throws Exception {
        var b = fixture(ProductionCapacityRules.VERSION);
        add(b, "爱培科", "ROOT", "MACHINE_OUTPUT", "10", DAY, "A");
        var expected = established(b);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            doAnswer(call -> {
                int isolation = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) Connection::getTransactionIsolation);
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ, isolation);
                worker.submit(() -> {
                    long run = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')", b.getId());
                    jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?", run, b.getId());
                    jdbc.update("INSERT INTO mrp_daily_plan_override(batch_id,factory_code,root_material_code,terminal_material_code,terminal_type,supply_mode,path_group_key,schedule_date,manual_quantity) VALUES(?,'爱培科','ROOT','ROOT','MACHINE_OUTPUT','SELF_MADE','A',?,0)", b.getId(), DAY);
                }).get(10, TimeUnit.SECONDS);
                return call.callRealMethod();
            }).when(automatic).read(any());
            assertEquals(expected, orchestrator.capacityReview(b.getId()));
        } finally {
            reset(automatic);
            worker.shutdownNow();
        }
        assertNotEquals(expected.fingerprint(), orchestrator.capacityReview(b.getId()).fingerprint());
    }

    private void assertCompatible(MrpCalcBatch b) throws Exception {
        var expected = established(b);
        assertEquals(expected, orchestrator.capacityReview(b.getId()));
        var projection = automatic.read(b);
        if (projection != null) assertEquals(BatchOrchestratorService.effectiveChecksum(daily.getDailyPlans(b.getId())), projection.checksum());
    }

    private BatchOrchestratorService.CapacityReview established(MrpCalcBatch b) throws Exception {
        var all = daily.getDailyPlans(b.getId());
        LocalDate start = all.stream().map(MrpDailyPlanDetail::getScheduleDate).min(LocalDate::compareTo).orElse(DAY);
        LocalDate end = all.stream().map(MrpDailyPlanDetail::getScheduleDate).max(LocalDate::compareTo).orElse(start);
        var loads = all.isEmpty() ? List.<DailyCapacityService.DayLoad>of() : capacity.evaluate(b, all, start, end);
        String input = b.getId() + "|" + b.getActiveRunId() + "|" + b.getRuleVersion() + "|" + b.getProductionCalendarVersionId()
                + "|" + BatchOrchestratorService.effectiveChecksum(all) + "|" + json.writeValueAsString(loads);
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        return new BatchOrchestratorService.CapacityReview(loads, fingerprint);
    }

    private MrpCalcBatch fixture(String rule) {
        int version = jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM production_calendar_version", Integer.class);
        long calendar = insert("INSERT INTO production_calendar_version(version_no,factory_code,status) VALUES(?,'ALL','PUBLISHED')", version);
        for (String factory : ProductionCapacityRules.FACTORIES) for (int i = -2; i <= 3; i++)
            jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity,capacity_unit) VALUES(?,?,?,1,25,'台')", calendar, factory, DAY.plusDays(i));
        long batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,production_calendar_version_id,rule_version) VALUES('ALL','READY',1,?,?)", calendar, rule);
        long run = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')", batch);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?", run, batch);
        return batches.selectById(batch);
    }

    private void add(MrpCalcBatch b, String factory, String root, String type, String quantity, LocalDate date, String path) {
        facts.insert(fact(b, factory, root, type, quantity, date, path));
    }

    private MrpDailyPlanDetail fact(MrpCalcBatch b, String factory, String root, String type, String quantity, LocalDate date, String path) {
        var p = new MrpDailyPlanDetail();
        p.setRunId(b.getActiveRunId()); p.setBatchId(b.getId()); p.setFactoryCode(factory);
        p.setRootMaterialCode(root); p.setTerminalMaterialCode(root); p.setTerminalType(type);
        p.setSupplyMode("SELF_MADE"); p.setPathGroupKey(path); p.setSourceMonth(DAY);
        p.setSourceDate(date); p.setScheduleDate(date); p.setSystemQuantity(new BigDecimal(quantity));
        p.setIsProductionDay(!date.isBefore(DAY)); p.setRevision(0); p.setRowKey(UUID.randomUUID().toString());
        return p;
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
}
