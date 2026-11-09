package com.mrp.mrp.controller;

import com.mrp.capacity.repository.CapacityMapper;
import com.mrp.forecast.domain.ForecastDetail;
import com.mrp.forecast.domain.ForecastVersion;
import com.mrp.forecast.repository.ForecastMapper;
import com.mrp.inventory.domain.InventorySnapshot;
import com.mrp.inventory.repository.InventoryItemMapper;
import com.mrp.inventory.repository.InventoryMapper;
import com.mrp.mrp.domain.*;
import com.mrp.mrp.repository.*;
import com.mrp.mrp.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MrpBatchBusinessWeekScopeTest {
    @Test void creationLocksMaintainedLeadTimeMonthAlongsideRequiredForecastMonth() {
        LocalDate october = LocalDate.of(2026, 10, 1), september = october.minusMonths(1);
        var batches = mock(MrpCalcBatchMapper.class);
        var forecasts = mock(ForecastMapper.class);
        var inventory = mock(InventoryMapper.class);
        var bom = mock(BomSnapshotService.class);
        var configuration = mock(MrpConfigurationService.class);
        var snapshots = spy(new ConfigSnapshotService(mock(ConfigMasterMapper.class), mock(Phase2ScopeConfigMapper.class), mock(DailyCapacityMapper.class)));
        var mapping = new FactoryWarehouseMapping(); mapping.setWarehouseCode("WH"); mapping.setEnabled(true); mapping.setQualityConfigured(true);
        doReturn(mapping).when(snapshots).mappingFor("爱培科");
        doReturn(true).when(snapshots).hasStockPolicy();
        doReturn(true).when(snapshots).hasCalendar("爱培科");
        doReturn(List.of(september, october)).when(snapshots).businessMonths();
        var weekVersions = new LinkedHashMap<LocalDate, Long>(); weekVersions.put(october, 20L); weekVersions.put(september, 19L);
        doReturn(new ConfigSnapshotService.Snapshot(10L, 11L, 12L, 13L, weekVersions)).when(snapshots)
                .snapshot(eq("爱培科"), eq(List.of(september, october)), any());
        when(forecasts.selectVersionById(1L)).thenReturn(new ForecastVersion(1L, 1, null, null, "IMPORTED", null, null));
        when(forecasts.selectDetailsByVersionId(1L)).thenReturn(List.of(new ForecastDetail(1L, 1L, "爱培科", "ROOT", null, null, null, null, null, null, null, october, new BigDecimal("1800"))));
        var stock = new InventorySnapshot(); stock.setStatus("IMPORTED"); stock.setWarehouseCode("WH");
        when(inventory.selectSnapshotById(2L)).thenReturn(stock);
        when(bom.loadCurrentBom()).thenReturn(new BomSnapshotService.CurrentBom(List.of(), "current-bom"));
        var roles = new CategoryRoleVersion(); roles.setStatus("PUBLISHED");
        when(configuration.getCategoryRoleVersion(4L)).thenReturn(roles);
        doAnswer(invocation -> { ((MrpCalcBatch) invocation.getArgument(0)).setId(30L); return 1; }).when(batches).insert(any());
        var configMaster = mock(ConfigMasterMapper.class);
        when(configMaster.selectFactoryWarehouseMappingsByFactory("爱培科")).thenReturn(List.of(mapping));
        var controller = new MrpBatchController(batches, mock(BatchOrchestratorService.class), forecasts, inventory,
                mock(InventoryItemMapper.class), bom, configuration, snapshots, mock(CapacityMapper.class), mock(InventoryNormalizationService.class), mock(com.mrp.mrp.service.PlanningFactoryAccessService.class),
                configuredFactories(), configMaster);
        var response = controller.createBatch("爱培科", 1L, 2L, null, 4L, null, null, null);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().success());
        assertEquals(20L, response.getBody().data().getBusinessWeekVersionId());
        verify(batches).insertBusinessWeekScope(30L, october, 20L);
        verify(batches).insertBusinessWeekScope(30L, september, 19L);
    }
    @ParameterizedTest
    @ValueSource(strings = {"ALL", "爱培科", "永惠"})
    void oneCombinedImportFreezesOnlySelectedFactoryScopesAndLatestConfiguration(String factory) {
        LocalDate month = LocalDate.of(2026, 10, 1), previous = month.minusMonths(1);
        var batches = mock(MrpCalcBatchMapper.class); var forecasts = mock(ForecastMapper.class);
        var inventories = mock(InventoryMapper.class); var bom = mock(BomSnapshotService.class);
        var configuration = mock(MrpConfigurationService.class); var normalization = mock(InventoryNormalizationService.class);
        var snapshots = spy(new ConfigSnapshotService(mock(ConfigMasterMapper.class), mock(Phase2ScopeConfigMapper.class), mock(DailyCapacityMapper.class)));
        var apk = new FactoryWarehouseMapping(); apk.setWarehouseCode("APK"); apk.setEnabled(true); apk.setQualityConfigured(true);
        var yh = new FactoryWarehouseMapping(); yh.setWarehouseCode("YH"); yh.setEnabled(true); yh.setQualityConfigured(true);
        doReturn(apk).when(snapshots).mappingFor("爱培科"); doReturn(yh).when(snapshots).mappingFor("永惠");
        doReturn(true).when(snapshots).hasStockPolicy(); doReturn(true).when(snapshots).hasCalendar(anyString());
        doReturn(List.of(previous, month)).when(snapshots).businessMonths();
        var weeks = new LinkedHashMap<LocalDate, Long>(); weeks.put(previous, 19L); weeks.put(month, 20L);
        doReturn(new ConfigSnapshotService.Snapshot(10L, 11L, 12L, 13L, weeks)).when(snapshots)
                .snapshot(eq(factory), eq(List.of(previous, month)), any());
        when(forecasts.selectVersionById(1L)).thenReturn(new ForecastVersion(1L, 1, null, null, "IMPORTED", null, null));
        when(forecasts.selectDetailsByVersionId(1L)).thenReturn(List.of(
                new ForecastDetail(1L, 1L, "爱培科", "A", null, null, null, null, null, null, null, month, BigDecimal.TEN),
                new ForecastDetail(2L, 1L, "永惠", "A", null, null, null, null, null, null, null, month, BigDecimal.TEN)));
        var combined = new InventorySnapshot(); combined.setStatus("IMPORTED");
        when(inventories.selectSnapshotById(2L)).thenReturn(combined);
        var items = mock(InventoryItemMapper.class);
        when(items.countBySnapshotIdAndWarehouseCode(2L, "APK")).thenReturn(10);
        when(items.countBySnapshotIdAndWarehouseCode(2L, "YH")).thenReturn(20);
        when(items.selectWarehouseCodesBySnapshotId(2L)).thenReturn(List.of("APK", "YH"));
        when(bom.loadCurrentBom()).thenReturn(new BomSnapshotService.CurrentBom(List.of(), "current-bom"));
        var roles = new CategoryRoleVersion(); roles.setId(4L); roles.setStatus("PUBLISHED");
        var newerDraft = new CategoryRoleVersion(); newerDraft.setId(5L); newerDraft.setStatus("DRAFT");
        when(configuration.getCategoryRoleVersions()).thenReturn(List.of(roles, newerDraft));
        when(configuration.getCategoryRoleVersion(4L)).thenReturn(roles);
        doAnswer(invocation -> { ((MrpCalcBatch) invocation.getArgument(0)).setId(30L); return 1; }).when(batches).insert(any());
        var configMaster = mock(ConfigMasterMapper.class);
        when(configMaster.selectFactoryWarehouseMappingsByFactory("爱培科")).thenReturn(List.of(apk));
        when(configMaster.selectFactoryWarehouseMappingsByFactory("永惠")).thenReturn(List.of(yh));
        var controller = new MrpBatchController(batches, mock(BatchOrchestratorService.class), forecasts, inventories,
                items, bom, configuration, snapshots, mock(CapacityMapper.class), normalization, mock(PlanningFactoryAccessService.class),
                configuredFactories(), configMaster);
        var response = controller.createBatch(factory, 1L, 2L, null, null, null, null, null);
        assertEquals(200, response.getStatusCode().value());
        var batch = response.getBody().data();
        assertNull(batch.getInventorySnapshotGroupId());
        assertNull(batch.getBomSnapshotId()); assertEquals("current-bom", batch.getBomSourceChecksum()); assertEquals(4L, batch.getCategoryRoleVersionId());
        assertEquals(ProductionCapacityRules.VERSION, batch.getRuleVersion());
        for (String selected : List.of("爱培科", "永惠")) {
            String warehouse = selected.equals("爱培科") ? "APK" : "YH";
            if (factory.equals("ALL") || factory.equals(selected)) {
                verify(batches).insertFactoryScope(30L, selected, warehouse, 13L, 2L);
                verify(normalization).createPoolsFromSnapshot(30L, 2L, selected, 11L, java.util.Set.of(warehouse));
            } else {
                verify(batches, never()).insertFactoryScope(anyLong(), eq(selected), anyString(), anyLong(), anyLong());
                verify(normalization, never()).createPoolsFromSnapshot(anyLong(), anyLong(), eq(selected), anyLong(), anySet());
            }
        }
        verify(normalization).normalizeSnapshot(2L, "ALL", null);
        verify(normalization, never()).createSnapshotGroup(anyMap(), any());
        clearInvocations(batches, normalization);
        var rejected = controller.createBatch(factory, 1L, 2L, null, null, null, null, List.of(2L, 5L));
        assertEquals(400, rejected.getStatusCode().value());
        assertEquals("SINGLE_INVENTORY_VERSION_REQUIRED", rejected.getBody().error().code());
        verify(batches, never()).insert(any()); verifyNoInteractions(normalization);

        when(items.selectWarehouseCodesBySnapshotId(2L)).thenReturn(List.of("APK", "YH", "EXTRA"));
        var outside = controller.createBatch(factory, 1L, 2L, null, null, null, null, null);
        assertEquals(400, outside.getStatusCode().value());
        assertEquals("INVENTORY_WAREHOUSE_MISMATCH", outside.getBody().error().code());
        assertTrue(outside.getBody().error().message().contains("EXTRA"));
        verify(batches, never()).insert(any());
        verifyNoInteractions(normalization);

    }

    private static FactoryMasterService configuredFactories() {
        var factories = mock(FactoryMasterService.class);
        when(factories.enabled(anyString())).thenReturn(true);
        when(factories.enabledCodes()).thenReturn(List.of("爱培科", "永惠"));
        return factories;
    }

}
