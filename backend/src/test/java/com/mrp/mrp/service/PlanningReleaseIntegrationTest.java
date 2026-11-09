package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.common.security.CurrentUser;
import com.mrp.mrp.repository.MrpCalcBatchMapper;
import com.mrp.mrp.repository.MrpMaterialPolicyNoticeMapper;
import com.mrp.mrp.repository.MrpStockRiskMapper;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real mapped SQL and HTTP gates, isolated from retained local schedules. */
@AutoConfigureMockMvc
@Transactional
class PlanningReleaseIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired @Qualifier("primarySqlSessionTemplate") org.mybatis.spring.SqlSessionTemplate session;
    @Autowired MrpCalcBatchMapper batches;
    @Autowired MrpMaterialPolicyNoticeMapper policies;
    @Autowired MrpStockRiskMapper risks;
    @Autowired PlanningReleaseValidationService validation;
    long user, batch, run;

    @BeforeEach void fixture() {
        user = insert("INSERT INTO user(username,password_hash) VALUES('release-gate-test','unused')");
        jdbc.update("INSERT INTO user_factory_scope(user_id,factory_code) VALUES(?,'永惠')", user);
        batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('永惠','READY',1,'v1')");
        run = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')", batch);
        jdbc.update("UPDATE mrp_calc_batch SET active_run_id=? WHERE id=?", run, batch);
    }

    @Test void pendingPolicyAndStockBlockHttpEvenWithCapacityAcknowledgment() throws Exception {
        jdbc.update("INSERT INTO mrp_material_policy_notice(batch_id,run_id,factory_code,material_code,missing_type,effective_explosion_policy,sources_json) VALUES(?,?,'永惠','M1','BOTH','CONTINUE','[]'),(?,?,'永惠','M2','SUPPLY','CONTINUE','[]')", batch, run, batch, run);
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,risk_type,material_code) VALUES(?,?,'STOCK_POLICY_MISSING','M1')", batch, run);
        mvc.perform(post("/api/v1/mrp/batches/" + batch + "/publish").with(authentication(auth()))
                .contentType("application/json").content("{\"capacityFingerprint\":\"acknowledged\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MRP_ILLEGAL_STATE"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("默认下钻待确认 1 项"),
                        org.hamcrest.Matchers.containsString("库存风险待处理 1 项"))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM calc_task WHERE task_type='MRP_PUBLISH'", Integer.class));
        mvc.perform(post("/api/v1/mrp/batches/" + batch + "/transition").param("from", "READY").param("to", "PUBLISHED").with(authentication(auth())))
                .andExpect(status().isBadRequest());
        assertEquals("READY", batches.selectById(batch).getStatus());
        assertEquals(1, policies.confirmDefaultExplosion(run, user));
        assertEquals(1, risks.resolvePendingByRunId(batch, run, user, "BATCH_ZERO_THRESHOLDS"));
        assertDoesNotThrow(() -> validation.requirePublishable(batches.selectById(batch)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_material_policy_notice WHERE run_id=?", Integer.class, run));
    }

    @Test void missingDateIsVisibleAndBlocksHttpButConfiguredRestDayPasses() throws Exception {
        long forecast = insert("INSERT INTO forecast_version(version_no) VALUES(991)");
        jdbc.update("INSERT INTO forecast_detail(version_id,factory_code,material_id,plan_month,forecast_qty) VALUES(?,'永惠','M1','2026-10-01',100)", forecast);
        long calendar = insert("INSERT INTO production_calendar_version(version_no,factory_code,status) VALUES(991,'永惠','PUBLISHED')");
        jdbc.update("UPDATE mrp_calc_batch SET forecast_version_id=?,production_calendar_version_id=? WHERE id=?", forecast, calendar, batch);
        for (LocalDate date = LocalDate.of(2026, 9, 24); date.isBefore(LocalDate.of(2026, 11, 1)); date = date.plusDays(1)) {
            if (!date.equals(LocalDate.of(2026, 10, 1)))
                jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity) VALUES(?,'永惠',?,1,100)", calendar, date);
        }
        mvc.perform(get("/api/v1/mrp/batches/" + batch + "/calendar-check").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.missingDays[0].factoryCode").value("永惠"))
                .andExpect(jsonPath("$.data.missingDays[0].date").value("2026-10-01"));
        mvc.perform(post("/api/v1/mrp/batches/" + batch + "/publish").with(authentication(auth())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("永惠 2026-10-01")));
        jdbc.update("INSERT INTO production_calendar_day(version_id,factory_code,calendar_date,is_production_day,daily_capacity) VALUES(?,'永惠','2026-10-01',0,0)", calendar);
        session.clearCache();
        assertDoesNotThrow(() -> validation.requirePublishable(batches.selectById(batch)));
        jdbc.update("DELETE FROM user_factory_scope WHERE user_id=?", user);
        session.clearCache();
        mvc.perform(get("/api/v1/mrp/batches/" + batch + "/calendar-check").with(authentication(auth())))
                .andExpect(status().isForbidden());
    }

    @Test void copyingRiskRowsPreservesQuantitiesAndResolutionMetadata() {
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,risk_type,warehouse_code,material_code,available_qty,used_qty,remaining_qty,safety_stock_qty,warning_stock_qty,root_material_codes,created_at,resolved_at,resolved_by,resolution_code) VALUES(?,?,'WARNING_STOCK_BREACH','W1','M1',12.123456,2.000001,10.123455,15,20,'ROOT','2026-10-22 10:01:02.123','2026-10-22 10:02:03.456',?,'BATCH_ZERO_THRESHOLDS'),(?,?,'STOCK_POLICY_MISSING','W1','M2',0,0,0,0,0,'ROOT','2026-10-22 10:01:02.123',NULL,NULL,NULL)", batch, run, user, batch, run);
        long target = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(?,'ACTIVE')", batch);
        assertEquals(2, risks.copyRun(run, batch, target));
        String fields = "risk_type,warehouse_code,material_code,available_qty,used_qty,remaining_qty,safety_stock_qty,warning_stock_qty,root_material_codes,created_at,resolved_at,resolved_by,resolution_code";
        assertEquals(jdbc.queryForList("SELECT " + fields + " FROM mrp_stock_risk WHERE run_id=? ORDER BY material_code", run),
                jdbc.queryForList("SELECT " + fields + " FROM mrp_stock_risk WHERE run_id=? ORDER BY material_code", target));
        assertEquals(1, risks.countByBatchId(batch, target));
        assertEquals(1, risks.resolvePendingByRunId(batch, target, user, "BATCH_ZERO_THRESHOLDS"));
        assertEquals(0, risks.countByBatchId(batch, target));
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(new CurrentUser.UserPrincipal(user, "release-gate-test"), null,
                List.of(new SimpleGrantedAuthority("mrp_batch:publish"), new SimpleGrantedAuthority("mrp_batch:list")));
    }

    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
