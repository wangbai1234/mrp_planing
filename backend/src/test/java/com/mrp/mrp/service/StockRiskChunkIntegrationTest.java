package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.MrpRequirementDetail;
import com.mrp.mrp.domain.MrpStockRisk;
import com.mrp.mrp.repository.MrpStockRiskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Actual MySQL mapped inserts and Spring transaction, including restoration after deletion. */
class StockRiskChunkIntegrationTest extends BaseIntegrationTest {
    @MockBean MrpTaskWorkerService worker;
    @MockBean InventoryNormalizationService inventory;
    @Autowired StockRiskService service;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    @Autowired @Qualifier("primarySqlSessionTemplate") SqlSessionTemplate session;
    @SpyBean MrpStockRiskMapper mapper;
    MrpCalcBatch batch;
    long run;
    List<MrpRequirementDetail> details;

    @BeforeEach void fixture() {
        reset(mapper);
        batch = new MrpCalcBatch();
        batch.setId(insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('爱培科','CALCULATING',1,'v1')"));
        run = insert("INSERT INTO mrp_calc_run(batch_id,status) VALUES(" + batch.getId() + ",'CALCULATING')");
        when(inventory.getPoolsByBatch(batch.getId())).thenReturn(List.of());
        details = new ArrayList<>();
        for (int i = 0; i < 1101; i++) {
            var detail = new MrpRequirementDetail();
            detail.setFactoryCode("爱培科");
            detail.setCurrentMaterialCode("RISK-" + i);
            detail.setRootMaterialCode("ROOT-" + i);
            detail.setInventoryUsedQty(BigDecimal.ZERO);
            details.add(detail);
        }
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM mrp_stock_risk WHERE batch_id=?", batch.getId());
        jdbc.update("DELETE FROM mrp_calc_run WHERE id=?", run);
        jdbc.update("DELETE FROM mrp_calc_batch WHERE id=?", batch.getId());
    }

    @Test void everyMissingPolicyAndSourceSurvivesChunkBoundary() {
        var sizes = observe(false);
        service.record(batch, run, details);
        assertEquals(List.of(1000, 101), sizes);
        var saved = mapper.selectByBatchId(batch.getId(), run);
        assertEquals(1101, saved.size());
        assertEquals(1101, saved.stream().map(MrpStockRisk::getMaterialCode).distinct().count());
        for (var risk : saved) {
            assertEquals("STOCK_POLICY_MISSING", risk.getRiskType());
            assertEquals("ROOT-" + risk.getMaterialCode().substring(5), risk.getRootMaterialCodes());
        }
    }

    @Test void laterChunkFailureRestoresPreviouslyStoredRisksAndRetrySucceeds() {
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,risk_type,material_code,root_material_codes,remaining_qty) VALUES(?,?,'WARNING_STOCK_BREACH','ORIGINAL','SOURCE',1.000001)", batch.getId(), run);
        var before = jdbc.queryForList("SELECT * FROM mrp_stock_risk WHERE run_id=?", run);
        var sizes = observe(true);
        assertThrows(IllegalStateException.class, () -> service.record(batch, run, details));
        assertEquals(List.of(1000, 101), sizes);
        assertEquals(before, jdbc.queryForList("SELECT * FROM mrp_stock_risk WHERE run_id=?", run));
        reset(mapper);
        sizes = observe(false);
        service.record(batch, run, details);
        assertEquals(List.of(1000, 101), sizes);
        assertEquals(1101, mapper.selectByBatchId(batch.getId(), run).size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_stock_risk WHERE run_id=? AND material_code='ORIGINAL'", Integer.class, run));
    }

    @Test void splitStageReplacesRiskWhenComponentHasSameCodeAsMachineRoot() {
        batch.setRuleVersion(ProductionCapacityRules.VERSION);
        jdbc.update("INSERT INTO mrp_requirement_detail(run_id,batch_id,factory_code,root_material_code,source_month,current_material_code,level_no,path_hash,gross_qty,inventory_before_qty,inventory_used_qty,inventory_after_qty,net_qty,terminal_flag) "
                + "VALUES(?,?,'爱培科','ROOT','2026-10-01','ROOT',0,'ROOT',10,10,2,8,8,0)", run, batch.getId());
        jdbc.update("INSERT INTO mrp_stock_risk(batch_id,run_id,risk_type,material_code,root_material_codes) "
                + "VALUES(?,?,'STOCK_POLICY_MISSING','ROOT','ROOT')", batch.getId(), run);
        var component = new MrpRequirementDetail();
        component.setFactoryCode("爱培科");
        component.setRootMaterialCode("ROOT");
        component.setCurrentMaterialCode("ROOT");
        component.setLevelNo(2);
        component.setInventoryUsedQty(BigDecimal.ZERO);
        service.record(batch, run, List.of(component));
        assertEquals(1, mapper.selectByBatchId(batch.getId(), run).size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mrp_stock_risk WHERE run_id=?", Integer.class, run));
        jdbc.update("DELETE FROM mrp_requirement_detail WHERE run_id=?", run);
    }

    private List<Integer> observe(boolean failSecond) {
        var sizes = new ArrayList<Integer>();
        doAnswer(call -> {
            List<?> rows = call.getArgument(0);
            sizes.add(rows.size());
            assertTrue(rows.size() <= 1000, "Unbounded risk INSERT: " + rows.size());
            if (failSecond && sizes.size() == 2) throw new IllegalStateException("injected later risk chunk failure");
            return session.insert(MrpStockRiskMapper.class.getName() + ".insertBatch", Map.of("list", rows));
        }).when(mapper).insertBatch(anyList());
        return sizes;
    }

    private long insert(String sql) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
