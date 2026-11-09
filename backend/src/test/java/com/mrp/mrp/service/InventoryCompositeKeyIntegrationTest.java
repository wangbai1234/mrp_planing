package com.mrp.mrp.service;

import com.mrp.BaseIntegrationTest;
import com.mrp.mrp.domain.CategoryRoleDetail;
import com.mrp.mrp.engine.NetRequirementEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Actual MySQL normalization, frozen policies and engine input; all fixtures roll back. */
@Transactional
class InventoryCompositeKeyIntegrationTest extends BaseIntegrationTest {
    @Autowired InventoryNormalizationService service;
    @Autowired @Qualifier("primaryJdbcTemplate") JdbcTemplate jdbc;
    long snapshot, policyVersion, batch;
    String warehouse, factory, otherFactory;

    @BeforeEach void setup() {
        warehouse = "key-" + UUID.randomUUID().toString().substring(0, 8);
        factory = warehouse + "-A"; otherFactory = warehouse + "-B";
        snapshot = insert("INSERT INTO inventory_snapshot(snapshot_date,status) VALUES('2026-10-16','TEST')");
        batch = insert("INSERT INTO mrp_calc_batch(factory_code,status,version_no,rule_version) VALUES('ALL','DRAFT',1,'v4-rolling-business-weeks')");
        jdbc.update("INSERT INTO factory_warehouse_mapping(factory_code,warehouse_code) VALUES(?,?),(?,?)",
                factory, warehouse + "|X", otherFactory, warehouse);
        for (Object[] row : List.of(new Object[]{warehouse + "|X", "M", "GOOD", 100},
                new Object[]{warehouse + "|X", "M", "GOOD", 50},
                new Object[]{warehouse + "|X", "M", "DEFECTIVE", 500},
                new Object[]{warehouse, "X|M", "GOOD", 80})) {
            jdbc.update("INSERT INTO inventory_item(snapshot_id,inventory_category,material_id,supplier_name,warehouse_code,inventory_quality,shipping_available_qty) VALUES(?,'TEST',?,'TEST',?,?,?)",
                    snapshot, row[1], row[0], row[2], row[3]);
        }
        policyVersion = insert("INSERT INTO stock_policy_version(version_no,status) SELECT COALESCE(MAX(version_no),0)+1,'PUBLISHED' FROM stock_policy_version");
        jdbc.update("INSERT INTO stock_policy_detail(version_id,warehouse_code,material_code,safety_stock_qty,warning_stock_qty) VALUES(?,?,?,20,50),(?,?,?,5,50)",
                policyVersion, warehouse + "|X", "M", policyVersion, warehouse, "X|M");
    }

    @Test void normalizedInventoryAndFrozenSafetyReachNetRequirementsWithoutCrossFactoryConsumption() {
        var balances = service.normalizeSnapshot(snapshot, "ALL", null);
        assertEquals(3, balances.size());
        var defective = balances.stream().filter(row -> "DEFECTIVE".equals(row.getInventoryQuality())).findFirst().orElseThrow();
        assertEquals(0, defective.getAvailableQuantity().compareTo(new BigDecimal("500")));
        assertFalse(defective.getIncludedFlag());
        var all = service.buildCalculableInventoryMap(snapshot, policyVersion, "ALL");
        assertEquals(Set.of("M|GOOD", "X|M|GOOD"), all.keySet());
        assertEquals(0, all.get("M|GOOD").compareTo(new BigDecimal("130")));
        assertEquals(0, all.get("X|M|GOOD").compareTo(new BigDecimal("75")));
        for (String owner : List.of(factory, otherFactory)) {
            String material = owner.equals(factory) ? "M" : "X|M";
            var inventory = service.buildCalculableInventoryMap(snapshot, policyVersion, owner);
            assertEquals(Set.of(material + "|GOOD"), inventory.keySet());
            CategoryRoleDetail role = new CategoryRoleDetail(); role.setRole("RAW_MATERIAL"); role.setEnabled(true);
            var engine = new NetRequirementEngine(List.of(), inventory, Map.of(material, role), Map.of(),
                    List.of(), Set.of(material), owner, null, null, batch);
            var result = engine.explode(List.of(new NetRequirementEngine.RootDemand(material, new BigDecimal("200"), LocalDate.of(2026, 11, 1))));
            assertEquals(1, result.details.size());
            assertEquals(0, result.details.getFirst().getNetQty().compareTo(new BigDecimal(owner.equals(factory) ? "70" : "125")));
            assertTrue(result.issues.stream().noneMatch(issue -> "BLOCKING".equals(issue.getSeverity())));
        }
    }

    @Test void currentAndFrozenSafetyLookupAgreeAndPoolsReserveEachPairExactlyOnce() {
        service.normalizeSnapshot(snapshot, "ALL", null);
        assertEquals(service.buildCalculableInventoryMap(snapshot, policyVersion, "ALL"),
                service.buildCalculableInventoryMap(snapshot, null, "ALL"));
        var pools = service.createPoolsFromSnapshot(batch, snapshot, "ALL", policyVersion);
        assertEquals(2, pools.size());
        var own = pools.stream().filter(row -> factory.equals(row.getFactoryCode())).findFirst().orElseThrow();
        var other = pools.stream().filter(row -> otherFactory.equals(row.getFactoryCode())).findFirst().orElseThrow();
        assertEquals(warehouse + "|X", own.getWarehouseCode()); assertEquals("M", own.getMaterialCode());
        assertEquals(0, own.getReservedQty().compareTo(new BigDecimal("20")));
        assertEquals(0, other.getReservedQty().compareTo(new BigDecimal("5")));
        assertEquals(0, service.previewAllocation(own.getId(), "demand", "M", null, null, new BigDecimal("200"))
                .getAllocatedQty().compareTo(new BigDecimal("130")));
        assertEquals(2, service.getPoolsByBatch(batch).size());
    }

    private long insert(String sql) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
}
