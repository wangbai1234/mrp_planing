package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpDailyPlanDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MrpDailyPlanDetailMapper {
    boolean hasDistributionResult(@Param("runId") Long runId);
    int insert(MrpDailyPlanDetail detail);
    int insertBatch(@Param("list") List<MrpDailyPlanDetail> list);
    List<MrpDailyPlanDetail> selectByRunId(@Param("runId") Long runId);
    void streamCapacityFacts(@Param("runId") Long runId,
            org.apache.ibatis.session.ResultHandler<MrpDailyPlanDetail> handler);
    List<MrpDailyPlanDetail> selectConservationTotals(@Param("runId") Long runId, @Param("batchId") Long batchId);
    List<MrpDailyPlanDetail> selectByRunIdAndDateRange(@Param("runId") Long runId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    List<MrpDailyPlanDetail> selectByBatchId(@Param("batchId") Long batchId);
    List<MrpDailyPlanDetail> selectByBatchIdAndDateRange(@Param("batchId") Long batchId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    MrpDailyPlanDetail selectByRowKey(@Param("batchId") Long batchId, @Param("rowKey") String rowKey, @Param("runId") Long runId);

    int deleteByRunId(@Param("runId") Long runId);
    int deleteMaterialByRunId(@Param("runId") Long runId);
}
