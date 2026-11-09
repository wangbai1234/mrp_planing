package com.mrp.mrp.repository;

import com.mrp.mrp.domain.BusinessWeekDetail;
import com.mrp.mrp.domain.FactoryWarehouseMappingDetail;
import com.mrp.mrp.domain.StockPolicyDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface Phase2ScopeConfigMapper {
    Long selectLastInsertId();
    Integer nextFactoryWarehouseVersionNo();
    int insertFactoryWarehouseVersion(@Param("versionNo") Integer versionNo,
                                      @Param("effectiveDate") LocalDate effectiveDate,
                                      @Param("createdBy") Long createdBy);
    List<Map<String, Object>> selectFactoryWarehouseVersions();
    Map<String, Object> selectFactoryWarehouseVersion(@Param("id") Long id);
    String selectFactoryWarehouseVersionStatus(@Param("id") Long id);
    List<FactoryWarehouseMappingDetail> selectFactoryWarehouseDetails(@Param("versionId") Long versionId);
    Long selectLatestPublishedFactoryWarehouseVersionId();
    String selectPublishedFactoryByWarehouse(@Param("warehouseCode") String warehouseCode);
    int deleteFactoryWarehouseDetails(@Param("versionId") Long versionId);
    int insertFactoryWarehouseDetails(@Param("list") List<FactoryWarehouseMappingDetail> details);
    int publishFactoryWarehouseVersion(@Param("id") Long id, @Param("checksum") String checksum);
    int countFactoryWarehouseVersionReferences(@Param("id") Long id);
    int markFactoryWarehouseVersionDeleted(@Param("id") Long id);

    Integer nextStockPolicyVersionNo();
    int insertStockPolicyVersion(@Param("versionNo") Integer versionNo,
                                 @Param("effectiveDate") LocalDate effectiveDate,
                                 @Param("createdBy") Long createdBy);
    List<Map<String, Object>> selectStockPolicyVersions();
    Map<String, Object> selectStockPolicyVersion(@Param("id") Long id);
    String selectStockPolicyVersionStatus(@Param("id") Long id);
    List<StockPolicyDetail> selectStockPolicyDetails(@Param("versionId") Long versionId);
    Long selectLatestPublishedStockPolicyVersionId();
    StockPolicyDetail selectPublishedStockPolicy(@Param("warehouseCode") String warehouseCode,
                                                 @Param("materialCode") String materialCode);
    int deleteStockPolicyDetails(@Param("versionId") Long versionId);
    int insertStockPolicyDetails(@Param("list") List<StockPolicyDetail> details);
    int publishStockPolicyVersion(@Param("id") Long id, @Param("checksum") String checksum);
    int countStockPolicyVersionReferences(@Param("id") Long id);
    int markStockPolicyVersionDeleted(@Param("id") Long id);

    Integer nextBusinessWeekVersionNo(@Param("businessMonth") LocalDate businessMonth);
    int insertBusinessWeekVersion(@Param("versionNo") Integer versionNo,
                                  @Param("businessMonth") LocalDate businessMonth,
                                  @Param("createdBy") Long createdBy);
    List<Map<String, Object>> selectBusinessWeekVersions();
    Map<String, Object> selectBusinessWeekVersion(@Param("id") Long id);
    String selectBusinessWeekVersionStatus(@Param("id") Long id);
    List<BusinessWeekDetail> selectBusinessWeekDetails(@Param("versionId") Long versionId);
    Long selectLatestPublishedBusinessWeekVersionId(@Param("businessMonth") LocalDate businessMonth);
    List<BusinessWeekDetail> selectPublishedBusinessWeeksByRange(@Param("startDate") LocalDate startDate,
                                                                 @Param("endDate") LocalDate endDate);
    int deleteBusinessWeekDetails(@Param("versionId") Long versionId);
    int insertBusinessWeekDetails(@Param("list") List<BusinessWeekDetail> details);
    int publishBusinessWeekVersion(@Param("id") Long id, @Param("checksum") String checksum);
    int countBusinessWeekVersionReferences(@Param("id") Long id);
    int markBusinessWeekVersionDeleted(@Param("id") Long id);
}
