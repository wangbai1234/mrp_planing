package com.mrp.mrp.service;

import com.mrp.inventory.domain.InventoryItem;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.repository.InventoryItemMapper;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.mrp.domain.MrpInventoryBalance;
import com.mrp.mrp.domain.MrpInventoryPool;
import com.mrp.mrp.domain.MrpInventoryAllocation;
import com.mrp.mrp.domain.MrpCalcBatch;
import com.mrp.mrp.domain.StockPolicyDetail;
import com.mrp.mrp.repository.MrpInventoryAllocationMapper;
import com.mrp.mrp.repository.MrpInventoryBalanceMapper;
import com.mrp.mrp.repository.MrpInventoryPoolMapper;
import com.mrp.mrp.repository.Phase2ScopeConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryNormalizationServiceTest {

    @Mock private InventoryItemMapper inventoryItemMapper;
    @Mock private InventoryMapper inventoryMapper;
    @Mock private MrpInventoryBalanceMapper balanceMapper;
    @Mock private MrpInventoryPoolMapper poolMapper;
    @Mock private MrpInventoryAllocationMapper allocationMapper;
    @Mock private Phase2ScopeConfigMapper scopeConfigMapper;
    @Mock private com.mrp.mrp.repository.MrpCalcBatchMapper batchMapper;
    @Mock private com.mrp.audit.service.AuditService audit;

    private InventoryNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new InventoryNormalizationService(inventoryItemMapper, inventoryMapper, balanceMapper,
                poolMapper, allocationMapper, scopeConfigMapper,
                org.mockito.Mockito.mock(com.mrp.mrp.repository.InventorySnapshotGroupMapper.class), batchMapper,
                org.mockito.Mockito.mock(com.mrp.mrp.repository.MrpWorkbenchReviewMapper.class), audit);
    }

    @Test
    void calculableInventoryReservesSafetyButDoesNotReserveWarning() {
        when(balanceMapper.selectGoodBySnapshotId(11L)).thenReturn(List.of(balance("YH", "M001", "15")));

        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(policy("YH", "M001", "5", "50")));
        Map<String, BigDecimal> result = service.buildCalculableInventoryMap(11L, 7L);
        assertEquals(new BigDecimal("10"), result.get("M001|GOOD"));
    }

    @Test
    void poolKeepsWarningAsRiskThresholdAndSafetyAsReservedQuantity() {
        when(balanceMapper.selectAggregatedBySnapshotId(11L)).thenReturn(List.of(balance("YH", "M001", "100")));
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(policy("YH", "M001", "20", "60")));

        List<MrpInventoryPool> pools = service.createPoolsFromSnapshot(21L, 11L, "F001", 7L);

        assertEquals(1, pools.size());
        assertEquals(new BigDecimal("20"), pools.getFirst().getReservedQty());
        assertEquals(new BigDecimal("60"), pools.getFirst().getWarningQty());
        assertEquals(new BigDecimal("100"), pools.getFirst().getAvailableQty());
        ArgumentCaptor<MrpInventoryPool> captor = ArgumentCaptor.forClass(MrpInventoryPool.class);
        verify(poolMapper).insert(captor.capture());
        assertEquals("YH", captor.getValue().getWarehouseCode());
    }

    @Test
    void poolsUseOnlyFrozenWarehousesAndReserveSafetySeparately() {
        MrpInventoryBalance first = balance("WH-A", "M001", "40");
        first.setFactoryCode("F001");
        MrpInventoryBalance second = balance("WH-B", "M001", "30");
        second.setFactoryCode("F001");
        MrpInventoryBalance unrelated = balance("WH-C", "M001", "100");
        unrelated.setFactoryCode("F001");
        when(balanceMapper.selectAggregatedBySnapshotId(11L)).thenReturn(List.of(first, second, unrelated));
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(
                policy("WH-A", "M001", "5", "10"), policy("WH-B", "M001", "10", "15")));

        List<MrpInventoryPool> pools = service.createPoolsFromSnapshot(21L, 11L, "F001", 7L,
                java.util.Set.of("WH-A", "WH-B"));

        assertEquals(2, pools.size());
        assertEquals(0, pools.stream().filter(p -> "WH-A".equals(p.getWarehouseCode()))
                .findFirst().orElseThrow().getReservedQty().compareTo(new BigDecimal("5")));
        assertEquals(0, pools.stream().filter(p -> "WH-B".equals(p.getWarehouseCode()))
                .findFirst().orElseThrow().getReservedQty().compareTo(new BigDecimal("10")));
        assertEquals(0, pools.stream().map(p -> p.getAvailableQty().subtract(p.getReservedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(new BigDecimal("55")));
    }

    @Test
    void normalizeAggregatesEachWarehouseAndMapsItsFactoryIndependently() {
        InventorySnapshot snapshot = new InventorySnapshot();
        snapshot.setId(11L);
        when(inventoryMapper.selectSnapshotById(11L)).thenReturn(snapshot);
        when(scopeConfigMapper.selectPublishedFactoryByWarehouse("YH")).thenReturn("F001");
        when(scopeConfigMapper.selectPublishedFactoryByWarehouse("APK")).thenReturn("F002");
        when(inventoryItemMapper.selectBySnapshotId(11L)).thenReturn(List.of(
                item("YH", "M001", "GOOD", "10"),
                item("YH", "M001", "GOOD", "5"),
                item("APK", "M001", "GOOD", "30")));

        List<MrpInventoryBalance> result = service.normalizeSnapshot(11L, "F001", "YH");

        assertEquals(2, result.size());
        MrpInventoryBalance yonghui = result.stream()
                .filter(row -> "YH".equals(row.getWarehouseScope())).findFirst().orElseThrow();
        MrpInventoryBalance apk = result.stream()
                .filter(row -> "APK".equals(row.getWarehouseScope())).findFirst().orElseThrow();
        assertEquals("F001", yonghui.getFactoryCode());
        assertEquals(new BigDecimal("15"), yonghui.getAvailableQuantity());
        assertEquals("F002", apk.getFactoryCode());
        assertEquals(new BigDecimal("30"), apk.getAvailableQuantity());
    }

    @Test
    void materialWithoutPolicyReservesNothingAndWarnsAtZero() {
        when(balanceMapper.selectAggregatedBySnapshotId(11L)).thenReturn(List.of(balance("YH", "M001", "100")));
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of());

        List<MrpInventoryPool> pools = service.createPoolsFromSnapshot(21L, 11L, "F001", 7L);

        assertEquals(BigDecimal.ZERO, pools.getFirst().getReservedQty());
        assertEquals(BigDecimal.ZERO, pools.getFirst().getWarningQty());
    }

    @Test
    void calculableInventoryOnlyIncludesRequestedFactoryWarehouse() {
        MrpInventoryBalance yonghui = balance("YH", "M001", "10");
        yonghui.setFactoryCode("F001");
        MrpInventoryBalance apk = balance("APK", "M001", "30");
        apk.setFactoryCode("F002");
        when(balanceMapper.selectGoodBySnapshotId(11L)).thenReturn(List.of(yonghui, apk));

        Map<String, BigDecimal> result = service.buildCalculableInventoryMap(11L, null, "F001");

        assertEquals(new BigDecimal("10"), result.get("M001|GOOD"));
    }

    @Test void safetyHigherThanGoodInventoryLeavesZeroAvailable() {
        when(balanceMapper.selectGoodBySnapshotId(11L)).thenReturn(List.of(balance("YH", "M001", "15")));
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(policy("YH", "M001", "20", "50")));
        assertEquals(BigDecimal.ZERO, service.buildCalculableInventoryMap(11L, 7L).get("M001|GOOD"));
    }

    @Test void allocationPreviewCannotConsumeSafetyFloor() {
        MrpInventoryPool pool = new MrpInventoryPool(); pool.setAvailableQty(new BigDecimal("100"));
        pool.setReservedQty(new BigDecimal("30")); pool.setAllocatedQty(new BigDecimal("20"));
        when(poolMapper.selectById(1L)).thenReturn(pool);
        assertEquals(new BigDecimal("50"), service.previewAllocation(1L, "D", "R", null, null, new BigDecimal("100")).getAllocatedQty());
    }

    @Test void normalizationPreservesDelimitedWarehouseAndMaterialWithoutMergingDifferentTuples() {
        InventorySnapshot snapshot = new InventorySnapshot(); snapshot.setId(11L);
        when(inventoryMapper.selectSnapshotById(11L)).thenReturn(snapshot);
        when(scopeConfigMapper.selectPublishedFactoryByWarehouse("W|X")).thenReturn("F001");
        when(scopeConfigMapper.selectPublishedFactoryByWarehouse("W")).thenReturn("X|F001");
        when(inventoryItemMapper.selectBySnapshotId(11L)).thenReturn(List.of(
                item("W|X", "M|Z", "GOOD", "10"), item("W|X", "M|Z", "GOOD", "5"),
                item("W", "M|Z", "GOOD", "30"), item("W|X", "M|Z", "DEFECTIVE", "7")));
        var result = service.normalizeSnapshot(11L, "ALL", null);
        assertEquals(3, result.size());
        var good = result.stream().filter(row -> "W|X".equals(row.getWarehouseScope())
                && "GOOD".equals(row.getInventoryQuality())).findFirst().orElseThrow();
        assertEquals("F001", good.getFactoryCode()); assertEquals("M|Z", good.getMaterialCode());
        assertEquals(new BigDecimal("15"), good.getAvailableQuantity());
        var other = result.stream().filter(row -> "W".equals(row.getWarehouseScope())).findFirst().orElseThrow();
        assertEquals("X|F001", other.getFactoryCode()); assertEquals(new BigDecimal("30"), other.getAvailableQuantity());
        var defective = result.stream().filter(row -> "DEFECTIVE".equals(row.getInventoryQuality())).findFirst().orElseThrow();
        assertEquals("M|Z", defective.getMaterialCode()); assertEquals(false, defective.getIncludedFlag());
    }

    @Test void frozenSafetyFloorsRemainDistinctForWarehouseMaterialPairsWithSameConcatenation() {
        when(balanceMapper.selectGoodBySnapshotId(11L)).thenReturn(List.of(
                balance("W|X", "M", "100"), balance("W|X", "M", "50"), balance("W", "X|M", "80")));
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(
                policy("W|X", "M", "20", "50"), policy("W", "X|M", "5", "50")));
        var result = service.buildCalculableInventoryMap(11L, 7L);
        assertEquals(Map.of("M|GOOD", new BigDecimal("130"), "X|M|GOOD", new BigDecimal("75")), result);
    }

    @Test void publishedSafetyLookupReceivesCompleteWarehouseAndMaterialCodes() {
        when(balanceMapper.selectGoodBySnapshotId(11L)).thenReturn(List.of(balance("W|X", "M|Z", "100")));
        when(scopeConfigMapper.selectPublishedStockPolicy("W|X", "M|Z"))
                .thenReturn(policy("W|X", "M|Z", "20", "50"));
        assertEquals(Map.of("M|Z|GOOD", new BigDecimal("80")), service.buildCalculableInventoryMap(11L, null));
        verify(scopeConfigMapper).selectPublishedStockPolicy("W|X", "M|Z");
    }

    @Test void inventoryPoolsKeepSeparateFrozenSafetyFloorsWhenCodesContainDelimiters() {
        when(balanceMapper.selectAggregatedBySnapshotId(11L)).thenReturn(List.of(
                balance("W|X", "M", "100"), balance("W", "X|M", "80")));
        when(scopeConfigMapper.selectStockPolicyDetails(7L)).thenReturn(List.of(
                policy("W|X", "M", "20", "50"), policy("W", "X|M", "5", "50")));
        var pools = service.createPoolsFromSnapshot(21L, 11L, "ALL", 7L);
        assertEquals(2, pools.size());
        assertEquals(new BigDecimal("20"), pools.get(0).getReservedQty());
        assertEquals(new BigDecimal("5"), pools.get(1).getReservedQty());
    }

    @Test void resettingManualAllocationRestoresAutomaticStateAndRequestsReanalysis() {
        MrpInventoryAllocation allocation = new MrpInventoryAllocation();
        allocation.setId(7L); allocation.setPoolId(3L); allocation.setAllocatedQty(new BigDecimal("400"));
        allocation.setAllocationType("MANUAL_OVERRIDE"); allocation.setManualConfirmed(true);
        allocation.setAdjustmentReason("业务例外"); allocation.setRevision(1);
        MrpInventoryPool pool = new MrpInventoryPool(); pool.setId(3L); pool.setBatchId(117L);
        pool.setAllocatedQty(new BigDecimal("450"));
        MrpCalcBatch batch = new MrpCalcBatch(); batch.setId(117L); batch.setStatus("WAITING_DECISION");
        when(allocationMapper.selectById(7L)).thenReturn(allocation);
        when(poolMapper.selectById(3L)).thenReturn(pool);
        when(batchMapper.selectForUpdate(117L)).thenReturn(batch);
        when(allocationMapper.update(allocation)).thenReturn(1);
        when(poolMapper.update(pool)).thenReturn(1);

        service.resetAllocation(7L);

        assertEquals(BigDecimal.ZERO, allocation.getAllocatedQty());
        assertEquals("AUTO_PRIORITY", allocation.getAllocationType());
        assertEquals(null, allocation.getAdjustmentReason());
        assertEquals(false, allocation.getManualConfirmed());
        assertEquals(2, allocation.getRevision());
        assertEquals(new BigDecimal("50"), pool.getAllocatedQty());
        verify(batchMapper).updateStatusForce(117L, "REANALYZING");
    }

    @Test void resetRejectsCalculatedBatchWithoutChangingPool() {
        MrpInventoryAllocation allocation = new MrpInventoryAllocation();
        allocation.setPoolId(3L); allocation.setAllocatedQty(new BigDecimal("400"));
        MrpInventoryPool pool = new MrpInventoryPool(); pool.setBatchId(117L);
        MrpCalcBatch batch = new MrpCalcBatch(); batch.setStatus("READY");
        when(allocationMapper.selectById(7L)).thenReturn(allocation);
        when(poolMapper.selectById(3L)).thenReturn(pool);
        when(batchMapper.selectForUpdate(117L)).thenReturn(batch);
        assertThrows(IllegalStateException.class, () -> service.resetAllocation(7L));
        verify(poolMapper, never()).update(pool);
    }

    private static InventoryItem item(String warehouse, String material, String quality, String quantity) {
        return new InventoryItem(null, 11L, null, "原材料", material, quality,
                warehouse, warehouse, null, null, "供应商", null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(quantity), null, null, null, BigDecimal.ZERO, null, null, null);
    }

    private static MrpInventoryBalance balance(String warehouse, String material, String quantity) {
        MrpInventoryBalance balance = new MrpInventoryBalance();
        balance.setWarehouseScope(warehouse);
        balance.setInventoryOrg(warehouse);
        balance.setMaterialCode(material);
        balance.setAvailableQuantity(new BigDecimal(quantity));
        balance.setInventoryQuality("GOOD");
        balance.setIncludedFlag(true);
        return balance;
    }

    private static StockPolicyDetail policy(String warehouse, String material, String safety, String warning) {
        StockPolicyDetail detail = new StockPolicyDetail();
        detail.setWarehouseCode(warehouse);
        detail.setMaterialCode(material);
        detail.setSafetyStockQty(new BigDecimal(safety));
        detail.setWarningStockQty(new BigDecimal(warning));
        return detail;
    }
}
