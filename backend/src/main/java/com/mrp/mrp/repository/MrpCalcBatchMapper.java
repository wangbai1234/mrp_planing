package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpCalcBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;
import com.mrp.mrp.domain.BusinessWeekDetail;
import com.mrp.mrp.domain.FactoryWarehouseMappingDetail;

@Mapper
public interface MrpCalcBatchMapper {
    record SummaryCounts(int totalIssues, int openIssues, int dailyPlanCount, int unallocatedCount,
            int pendingUnallocated, int poolCount, java.math.BigDecimal totalAvailable, java.math.BigDecimal totalAllocated) {}
    SummaryCounts selectSummaryCounts(@Param("batchId") Long batchId, @Param("runId") Long runId);
    int insert(MrpCalcBatch batch);
    int update(MrpCalcBatch batch);
    MrpCalcBatch selectForUpdate(@Param("id") Long id);
    MrpCalcBatch selectById(@Param("id") Long id);
    MrpCalcBatch selectByRequestKey(@Param("requestKey") String requestKey);
    List<MrpCalcBatch> selectByStatus(@Param("status") String status);
    List<MrpCalcBatch> selectAll();
    MrpCalcBatch selectCurrent();
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("version") Integer version);
    int updateStatusForce(@Param("id") Long id, @Param("status") String status);
    int resetAfterCalculationFailure(@Param("id") Long id,
                                     @Param("expectedVersion") Integer expectedVersion,
                                     @Param("expectedActiveRunId") Long expectedActiveRunId);
    int restoreExpiredMaterialTask(@Param("id") Long id, @Param("runId") Long runId,
                                   @Param("status") String status);
    int activateRunAndStatus(@Param("id") Long id, @Param("runId") Long runId, @Param("status") String status);
    
    // Pagination methods
    List<MrpCalcBatch> selectPage(@Param("status") String status, @Param("keyword") String keyword,
                                   @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countPage(@Param("status") String status, @Param("keyword") String keyword);
    int insertFactoryScope(@Param("batchId") Long batchId,
                           @Param("factoryCode") String factoryCode,
                           @Param("warehouseCode") String warehouseCode,
                           @Param("productionCalendarVersionId") Long productionCalendarVersionId,
                           @Param("inventorySnapshotId") Long inventorySnapshotId);
    int insertBusinessWeekScope(@Param("batchId") Long batchId,
                                @Param("businessMonth") LocalDate businessMonth,
                                @Param("businessWeekVersionId") Long businessWeekVersionId);
    List<LocalDate> selectBusinessWeekMonthsByBatchId(@Param("batchId") Long batchId);
    List<BusinessWeekDetail> selectBusinessWeeksByBatchAndRange(@Param("batchId") Long batchId,
                                                                @Param("startDate") LocalDate startDate,
                                                                @Param("endDate") LocalDate endDate);
    int updateFactoryScopeCalendar(@Param("batchId") Long batchId, @Param("calendarVersionId") Long calendarVersionId);

    int copyFactoryScopes(@Param("sourceBatchId") Long sourceBatchId,
                          @Param("targetBatchId") Long targetBatchId);
    int copyBusinessWeekScopes(@Param("sourceBatchId") Long sourceBatchId,
                               @Param("targetBatchId") Long targetBatchId);
    List<FactoryWarehouseMappingDetail> selectFactoryWarehousesByBatchId(@Param("batchId") Long batchId);
    List<String> selectFactoryCodesByBatchId(@Param("batchId") Long batchId);
}
