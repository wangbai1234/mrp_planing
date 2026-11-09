package com.mrp.mrp.service;

import com.mrp.audit.service.AuditService;
import com.mrp.common.security.CurrentUser;
import com.mrp.masterdata.domain.Material;
import com.mrp.masterdata.repository.MaterialMapper;
import com.mrp.mrp.domain.BusinessWeek;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.StockPolicy;
import com.mrp.mrp.repository.ConfigMasterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigMasterServiceTest {

    @Mock private ConfigMasterMapper mapper;
    @Mock private AuditService audit;
    @Mock private MaterialMapper materials;
    @Mock private FactoryMasterService factories;

    private ConfigMasterService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(factories.enabled(any(String.class)))
                .thenAnswer(invocation -> !"UNKNOWN".equals(invocation.getArgument(0)));
        service = new ConfigMasterService(mapper, audit, materials, factories);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser.UserPrincipal(9L, "admin"), null));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void createStockPolicyWritesOneLogWithEmptyBefore() {
        allow("YH", "M001");
        StockPolicy row = policy("YH", "M001", "10", "40");
        when(mapper.selectStockPolicyById(null)).thenReturn(row);
        scope("YH", "永惠");

        service.createStockPolicy(row);

        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        verify(audit).log(eq(9L), isNull(), eq("永惠"), eq("CREATE"), eq("stock_policy"), eq("YH|M001"),
                before.capture(), eq(row), isNull(), isNull());
        assertNull(before.getValue());
    }

    @Test
    void updateStockPolicyLogsBeforeAndAfter() {
        StockPolicy before = policy("YH", "M001", "10", "40");
        before.setId(3L);
        StockPolicy after = policy("YH", "M001", "20", "50");
        when(mapper.selectStockPolicyById(3L)).thenReturn(before, after);
        scope("YH", "永惠");

        service.updateStockPolicy(3L, policy("YH", "M001", "20", "50"));

        verify(audit).log(eq(9L), isNull(), eq("永惠"), eq("UPDATE"), eq("stock_policy"), eq("YH|M001"),
                eq(before), eq(after), isNull(), isNull());
    }

    @Test
    void deleteStockPolicyKeepsTheDeletedRowInTheLog() {
        StockPolicy before = policy("YH", "M001", "10", "40");
        before.setId(3L);
        when(mapper.selectStockPolicyById(3L)).thenReturn(before);
        scope("YH", "永惠");

        service.deleteStockPolicy(3L);

        verify(mapper).deleteStockPolicy(3L);
        verify(audit).log(eq(9L), isNull(), eq("永惠"), eq("DELETE"), eq("stock_policy"), eq("YH|M001"),
                eq(before), isNull(), isNull(), isNull());
    }

    @Test
    void importUpdatesMatchingKeysAndDoesNotDeleteAbsentRows() {
        allow("YH", "M001", "M002");
        StockPolicy existing = policy("YH", "M001", "10", "40");
        existing.setId(3L);
        when(mapper.selectStockPolicyByKey("YH", "M001")).thenReturn(existing);
        when(mapper.selectStockPolicyByKey("YH", "M002")).thenReturn(null);
        scope("YH", "永惠");

        var result = service.importStockPolicies(List.of(
                policy("YH", "M001", "30", "60"), policy("YH", "M002", "5", "15")), "策略.xlsx");

        assertEquals(1, result.get("inserted"));
        assertEquals(1, result.get("updated"));
        verify(mapper, never()).deleteStockPolicy(any());
        verify(audit).log(eq(9L), isNull(), eq("永惠"), eq("IMPORT"), eq("stock_policy"), eq("IMPORT"),
                isNull(), eq(java.util.Map.of("fileName", "策略.xlsx", "inserted", 1, "updated", 1)), isNull(), isNull());
    }

    @Test
    void importsWriteLogsForExactKeysWithPreviousValuesAndFileName() {
        allow("YH", "M001");
        StockPolicy before = policy("YH", "M001", "10", "40"); before.setId(1L);
        StockPolicy after = policy("YH", "M001", "20", "50");
        when(mapper.selectStockPolicyByKey("YH", "M001")).thenReturn(before);
        service.importStockPolicies(List.of(after), "stock.xlsx");
        verifyImport("stock_policy", "YH|M001", before, after, "stock.xlsx");

        var material = new com.mrp.mrp.domain.MaterialPolicy(); material.setMaterialCode("M001");
        material.setSupplyMode("SELF_MADE"); material.setExplosionPolicy("STOP");
        service.importMaterialPolicies(List.of(material), "material.xlsx");
        verifyImport("material_policy", "M001", null, material, "material.xlsx");

        var mapping = new FactoryWarehouseMapping(); mapping.setFactoryCode("爱培科"); mapping.setWarehouseCode("YH");
        mapping.setWarehouseName("测试仓"); mapping.setGoodWarehouse(true); mapping.setWarehouseAttribute("自有仓");
        service.importFactoryWarehouseMappings(List.of(mapping), "mapping.xlsx");
        verifyImport("factory_warehouse_mapping", "爱培科|YH", null, mapping, "mapping.xlsx");

        var calendar = new com.mrp.mrp.domain.ProductionCalendarEntry();
        calendar.setFactoryCode("爱培科"); calendar.setCalendarDate(LocalDate.of(2027, 2, 1));
        calendar.setProductionDay(true); calendar.setAllocationWeight(BigDecimal.ONE);
        calendar.setDailyCapacity(new BigDecimal("1000")); calendar.setCapacityUnit("台");
        when(mapper.insertProductionCalendar(any())).thenReturn(1);
        service.importProductionCalendar(List.of(calendar), "calendar.xlsx");
        verifyImport("mrp_production_calendar", "爱培科|2027-02-01", null, calendar, "calendar.xlsx");

        LocalDate month = LocalDate.of(2027, 2, 1);
        BusinessWeek week = week("W1", "2027-02-01", "2027-02-28"); week.setBusinessMonth(month);
        when(mapper.selectBusinessWeeks(month)).thenReturn(List.of(), List.of(week));
        service.importBusinessWeeks(List.of(week), "week.xlsx");
        verifyImport("business_week", "2027-02-01", List.of(), List.of(week), "week.xlsx");
    }

    private void verifyImport(String type, String key, Object before, Object after, String file) {
        String scope = type.equals("factory_warehouse_mapping") ? key.substring(0, key.indexOf('|'))
                : type.equals("mrp_production_calendar") ? key.substring(0, key.indexOf('|'))
                : type.equals("stock_policy") ? "ALL" : null;
        verify(audit).log(eq(9L), isNull(), eq(scope), eq("IMPORT"), eq(type), eq(key),
                eq(before), eq(java.util.Map.of("fileName", file, "data", after)), isNull(), isNull());
    }

    @Test
    void mappingRejectsUnknownFactoriesBeforeAnyImportWrites() {
        var valid = new FactoryWarehouseMapping(); valid.setFactoryCode("爱培科"); valid.setWarehouseCode("WH1");
        var invalid = new FactoryWarehouseMapping(); invalid.setFactoryCode("UNKNOWN"); invalid.setWarehouseCode("WH2");
        assertThrows(IllegalStateException.class, () -> service.createFactoryWarehouseMapping(invalid));
        assertThrows(IllegalStateException.class, () -> service.importFactoryWarehouseMappings(List.of(valid, invalid), "mapping.xlsx"));
        verify(mapper, never()).insertFactoryWarehouseMapping(any());
        verify(mapper, never()).updateFactoryWarehouseMapping(any());
    }

    @Test
    void defaultEnabledWarehouseRejectsDuplicateGoodPriority() {
        var row = new FactoryWarehouseMapping();
        row.setFactoryCode("爱培科"); row.setWarehouseCode("WH2"); row.setWarehouseName("测试仓");
        row.setGoodWarehouse(true); row.setWarehouseAttribute("自有仓"); row.setPriorityNo(1);
        when(mapper.countWarehousePriority("爱培科", 1, null)).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> service.createFactoryWarehouseMapping(row));
        verify(mapper, never()).insertFactoryWarehouseMapping(any());
    }

    @Test
    void updatingWarehouseWithoutEnabledRejectsDuplicateGoodPriority() {
        var before = new FactoryWarehouseMapping();
        before.setId(7L); before.setFactoryCode("爱培科"); before.setWarehouseCode("WH2");
        before.setEnabled(false);
        var row = new FactoryWarehouseMapping();
        row.setWarehouseName("测试仓"); row.setGoodWarehouse(true);
        row.setWarehouseAttribute("自有仓"); row.setPriorityNo(1);
        when(mapper.selectFactoryWarehouseMappingById(7L)).thenReturn(before);
        when(mapper.countWarehousePriority("爱培科", 1, 7L)).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> service.updateFactoryWarehouseMapping(7L, row));
        verify(mapper, never()).updateFactoryWarehouseMapping(any());
    }

    @Test
    void warehousePreviewRejectsExistingCrossFactoryCodeAndDuplicatePriority() {
        var crossFactory = new FactoryWarehouseMapping();
        crossFactory.setFactoryCode("爱培科"); crossFactory.setWarehouseCode("WH1");
        crossFactory.setWarehouseName("测试仓"); crossFactory.setGoodWarehouse(true);
        crossFactory.setWarehouseAttribute("自有仓"); crossFactory.setPriorityNo(1);
        var existing = new FactoryWarehouseMapping(); existing.setFactoryCode("永惠"); existing.setWarehouseCode("WH1");
        when(mapper.selectFactoryWarehouseMappingByWarehouse("WH1")).thenReturn(existing);
        var parsed = new com.mrp.importexport.excel.ParseResult<>(List.of(crossFactory), List.of(), List.of(), 1, 1, 0,
                List.of(List.of("爱培科", "WH1")), List.of("工厂编码", "仓库编码"));
        var result = service.validateFactoryWarehouseImport(parsed);
        assertEquals(1, result.errorRows());
        assertEquals(3, result.errors().getFirst().rowNumber());
        assertEquals(List.of(), result.rows());
    }

    @Test
    void warehousePreviewRejectsPriorityAlreadyAssignedInDatabase() {
        var row = new FactoryWarehouseMapping();
        row.setFactoryCode("爱培科"); row.setWarehouseCode("WH2"); row.setWarehouseName("测试仓");
        row.setGoodWarehouse(true); row.setWarehouseAttribute("自有仓"); row.setPriorityNo(1);
        when(mapper.countWarehousePriority("爱培科", 1, null)).thenReturn(1);
        var parsed = new com.mrp.importexport.excel.ParseResult<>(List.of(row), List.of(), List.of(), 1, 1, 0,
                List.of(List.of("爱培科", "WH2")), List.of("工厂编码", "仓库编码"));
        var result = service.validateFactoryWarehouseImport(parsed);
        assertEquals(1, result.errorRows());
        assertEquals(3, result.errors().getFirst().rowNumber());
        assertEquals(List.of(), result.rows());
    }

    @Test
    void savingBusinessWeeksLogsOncePerMonth() {
        LocalDate month = LocalDate.of(2027, 2, 1);
        when(mapper.selectBusinessWeeks(month)).thenReturn(List.of());

        service.saveBusinessWeeks(month, List.of(
                week("W1", "2027-02-01", "2027-02-14"),
                week("W2", "2027-02-15", "2027-02-28")));

        verify(mapper).insertBusinessWeeks(any());
        verify(audit).log(eq(9L), isNull(), isNull(), eq("CREATE"), eq("business_week"), eq("2027-02-01"),
                eq(List.of()), any(), isNull(), isNull());
    }

    @Test
    void createStockPolicyRejectsWarehouseOutsideFactoryMapping() {
        when(mapper.selectFactoryWarehouseMappings()).thenReturn(List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.createStockPolicy(policy("YH", "M001", "10", "40")));

        assertEquals("仓库不在工厂仓库映射中或未启用", error.getMessage());
        verify(mapper, never()).insertStockPolicy(any());
    }

    @Test
    void createStockPolicyRejectsUnknownMaterial() {
        allow("YH");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.createStockPolicy(policy("YH", "MISSING", "10", "40")));

        assertEquals("物料料号不存在", error.getMessage());
        verify(mapper, never()).insertStockPolicy(any());
    }

    @Test
    void searchStockPolicyMaterialsUsesSqlPagination() {
        when(materials.selectPage(null, null, true, "电机", null, 0, 20)).thenReturn(
                java.util.stream.IntStream.range(0, 20).mapToObj(index -> material("M" + index)).toList());

        assertEquals(20, service.searchStockPolicyMaterials("电机").size());
        verify(materials).selectPage(null, null, true, "电机", null, 0, 20);
    }

    @Test
    void importStockPolicyRejectsUnknownMaterial() {
        allow("YH", "M001");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.importStockPolicies(
                List.of(policy("YH", "MISSING", "10", "40")), "策略.xlsx"));

        assertEquals("物料料号不存在: MISSING", error.getMessage());
        verify(audit, never()).log(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void businessWeeksMustCoverTheWholeMonth() {
        assertThrows(IllegalStateException.class, () -> service.saveBusinessWeeks(LocalDate.of(2027, 2, 1),
                List.of(week("W1", "2027-02-01", "2027-02-14"))));
    }

    @Test void materialPolicyRejectsUnmaintainedAndUnknownOrInactiveMaterial() {
        var row = new com.mrp.mrp.domain.MaterialPolicy(); row.setMaterialCode("M1"); row.setSupplyMode("UNMAINTAINED"); row.setExplosionPolicy("STOP");
        assertThrows(IllegalStateException.class, () -> service.createMaterialPolicy(row));
        row.setSupplyMode("PURCHASED");
        assertThrows(IllegalStateException.class, () -> service.createMaterialPolicy(row));
        when(materials.selectByCodes(List.of("M1"))).thenReturn(List.of(new Material(1L, "M1", "M1", null, null, null, null, null, null, null, null, false,
                null, null, null, null, false, null, null, null)));
        assertThrows(IllegalStateException.class, () -> service.createMaterialPolicy(row));
        verify(mapper, never()).insertMaterialPolicy(any());
    }
    @Test void materialPolicyUpdatePreservesItsOriginalMaterialCode() {
        var before = new com.mrp.mrp.domain.MaterialPolicy(); before.setMaterialCode("M1"); before.setId(1L);
        var update = new com.mrp.mrp.domain.MaterialPolicy(); update.setMaterialCode("OTHER"); update.setSupplyMode("PURCHASED"); update.setExplosionPolicy("STOP");
        when(mapper.selectMaterialPolicyById(1L)).thenReturn(before);
        when(materials.selectByCodes(List.of("M1"))).thenReturn(List.of(material("M1")));
        service.updateMaterialPolicy(1L, update);
        assertEquals("M1", update.getMaterialCode()); verify(mapper).updateMaterialPolicy(update);
    }
    @Test void importReferenceValidationKeepsOriginalRowNumbersAndEnumErrors() {
        var raw = List.of(List.of("示例M", "自制", "继续下钻"), List.of("", "", ""), List.of("UNKNOWN", "未维护", "STOP"));
        var parsed = new com.mrp.importexport.excel.ParseResult<com.mrp.mrp.domain.MaterialPolicy>(List.of(), List.of(com.mrp.importexport.excel.ParseError.of(5, "供应方式", "未维护", "ENUM", "非法枚举")), List.of(), 1, 0, 1, raw, List.of("物料料号", "供应方式", "下钻策略"));
        var validated = service.validateMaterialPolicyImport(parsed);
        assertEquals(2, validated.errors().size()); assertEquals(1, validated.errorRows());
        assertEquals(5, validated.errors().getLast().rowNumber()); assertEquals("REFERENCE", validated.errors().getLast().errorCode());
    }

    private void scope(String warehouse, String factory) {
        var mapping=new FactoryWarehouseMapping(); mapping.setWarehouseCode(warehouse); mapping.setFactoryCode(factory);
        when(mapper.selectFactoryWarehouseMappingByWarehouse(warehouse)).thenReturn(mapping);
    }

    private void allow(String warehouse, String... materialCodes) {
        FactoryWarehouseMapping mapping = new FactoryWarehouseMapping();
        mapping.setWarehouseCode(warehouse);
        mapping.setEnabled(true);
        when(mapper.selectFactoryWarehouseMappings()).thenReturn(List.of(mapping));
        when(materials.selectByCodes(any())).thenReturn(java.util.Arrays.stream(materialCodes).map(this::material).toList());
    }

    private Material material(String code) {
        return new Material(1L, code, code, null, null, null, null, null, null, null, null, true,
                null, null, null, null, false, null, null, null);
    }

    private static StockPolicy policy(String warehouse, String material, String safety, String warning) {
        StockPolicy row = new StockPolicy();
        row.setWarehouseCode(warehouse);
        row.setMaterialCode(material);
        row.setSafetyStockQty(new BigDecimal(safety));
        row.setWarningStockQty(new BigDecimal(warning));
        return row;
    }

    private static BusinessWeek week(String code, String start, String end) {
        BusinessWeek row = new BusinessWeek();
        row.setWeekCode(code);
        row.setStartDate(LocalDate.parse(start));
        row.setEndDate(LocalDate.parse(end));
        return row;
    }
}
