package com.mrp.mrp.repository;

import com.mrp.mrp.domain.BusinessWeek;
import com.mrp.mrp.domain.FactoryWarehouseMapping;
import com.mrp.mrp.domain.MaterialPolicy;
import com.mrp.mrp.domain.ProductionCalendarEntry;
import com.mrp.mrp.domain.StockPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ConfigMasterMapper {

    List<FactoryWarehouseMapping> selectFactoryWarehouseMappings();
    List<FactoryWarehouseMapping> selectFactoryWarehouseMappingPage(@Param("factoryCodes") List<String> factoryCodes,
            @Param("keyword") String keyword, @Param("quality") String quality,
            @Param("offset") long offset, @Param("limit") int limit);
    int countFactoryWarehouseMappingPage(@Param("factoryCodes") List<String> factoryCodes,
            @Param("keyword") String keyword, @Param("quality") String quality);
    List<FactoryWarehouseMapping> selectFactoryWarehouseMappingsByFactory(@Param("factoryCode") String factoryCode);
    int countWarehousePriority(@Param("factoryCode") String factoryCode, @Param("priorityNo") Integer priorityNo,
                               @Param("excludeId") Long excludeId);
    int countWarehouseReferences(@Param("warehouseCode") String warehouseCode);
    FactoryWarehouseMapping selectFactoryWarehouseMappingById(@Param("id") Long id);
    FactoryWarehouseMapping selectFactoryWarehouseMappingByFactory(@Param("factoryCode") String factoryCode);
    FactoryWarehouseMapping selectFactoryWarehouseMappingByWarehouse(@Param("warehouseCode") String warehouseCode);
    int insertFactoryWarehouseMapping(FactoryWarehouseMapping row);
    int updateFactoryWarehouseMapping(FactoryWarehouseMapping row);
    int deleteFactoryWarehouseMapping(@Param("id") Long id);

    List<StockPolicy> selectStockPolicyPage(@Param("keyword") String keyword, @Param("warehouse") String warehouse,
            @Param("offset") int offset, @Param("limit") int limit);
    int countStockPolicyPage(@Param("keyword") String keyword, @Param("warehouse") String warehouse);
    List<MaterialPolicy> selectMaterialPolicyPage(@Param("keyword") String keyword, @Param("supply") String supply,
            @Param("explosion") String explosion, @Param("offset") int offset, @Param("limit") int limit);
    int countMaterialPolicyPage(@Param("keyword") String keyword, @Param("supply") String supply, @Param("explosion") String explosion);

    List<StockPolicy> selectStockPolicies();
    StockPolicy selectStockPolicyById(@Param("id") Long id);
    StockPolicy selectStockPolicyByKey(@Param("warehouseCode") String warehouseCode, @Param("materialCode") String materialCode);
    int countStockPolicies();
    int insertStockPolicy(StockPolicy row);
    int updateStockPolicy(StockPolicy row);
    int deleteStockPolicy(@Param("id") Long id);

    List<MaterialPolicy> selectMaterialPolicies();
    MaterialPolicy selectMaterialPolicyById(@Param("id") Long id);
    MaterialPolicy selectMaterialPolicyByMaterial(@Param("materialCode") String materialCode);
    int countMaterialPolicies();
    int insertMaterialPolicy(MaterialPolicy row);
    int updateMaterialPolicy(MaterialPolicy row);
    int deleteMaterialPolicy(@Param("id") Long id);

    List<ProductionCalendarEntry> selectProductionCalendar(@Param("factoryCode") String factoryCode);
    List<ProductionCalendarEntry> selectProductionCalendarRange(@Param("factoryCode") String factoryCode,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<ProductionCalendarEntry> selectProductionCalendarPage(@Param("factoryCode") String factoryCode,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword, @Param("status") String status,
            @Param("offset") int offset, @Param("limit") int limit);
    int countProductionCalendarPage(@Param("factoryCode") String factoryCode,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("keyword") String keyword, @Param("status") String status);
    List<ProductionCalendarEntry> lockProductionCalendarRange(@Param("factoryCode") String factoryCode,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    ProductionCalendarEntry selectProductionCalendarById(@Param("id") Long id);
    ProductionCalendarEntry selectProductionCalendarByKey(@Param("factoryCode") String factoryCode,
                                                          @Param("calendarDate") LocalDate calendarDate);
    List<String> selectProductionCalendarFactories();
    int insertProductionCalendar(ProductionCalendarEntry row);
    int updateProductionCalendar(ProductionCalendarEntry row);
    int deleteProductionCalendar(@Param("id") Long id);

    List<BusinessWeek> selectBusinessWeeks(@Param("businessMonth") LocalDate businessMonth);
    List<LocalDate> selectBusinessMonths();
    int deleteBusinessWeeksByMonth(@Param("businessMonth") LocalDate businessMonth);
    int insertBusinessWeeks(@Param("list") List<BusinessWeek> rows);

    /** 把当前主数据复制进既有版本表，status 固定为 SNAPSHOT，返回新版本 id。 */
    int insertFactoryWarehouseSnapshot(@Param("versionNo") Integer versionNo, @Param("createdBy") Long createdBy);
    int copyFactoryWarehouseSnapshotDetails(@Param("versionId") Long versionId);
    int insertStockPolicySnapshot(@Param("versionNo") Integer versionNo, @Param("createdBy") Long createdBy);
    int copyStockPolicySnapshotDetails(@Param("versionId") Long versionId);
    int insertMaterialPolicySnapshot(@Param("versionNo") Integer versionNo, @Param("createdBy") Long createdBy);
    int copyMaterialPolicySnapshotDetails(@Param("versionId") Long versionId);
    int insertProductionCalendarSnapshot(@Param("versionNo") Integer versionNo, @Param("factoryCode") String factoryCode,
                                         @Param("createdBy") Long createdBy);
    int copyProductionCalendarSnapshotDetails(@Param("versionId") Long versionId, @Param("factoryCode") String factoryCode);
    int insertBusinessWeekSnapshot(@Param("versionNo") Integer versionNo, @Param("businessMonth") LocalDate businessMonth,
                                   @Param("createdBy") Long createdBy);
    int copyBusinessWeekSnapshotDetails(@Param("versionId") Long versionId, @Param("businessMonth") LocalDate businessMonth);
    Long selectLastInsertId();
}
