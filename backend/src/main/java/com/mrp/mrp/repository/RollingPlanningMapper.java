package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpDailyPlanDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RollingPlanningMapper {
    List<LocalDate> selectMonths(@Param("forecastVersionId") Long forecastVersionId);
    List<LocalDate> selectFactDates(@Param("runId") Long runId, @Param("batchId") Long batchId);
    List<MrpDailyPlanDetail> selectRowPage(@Param("runId") Long runId, @Param("factoryCode") String factoryCode,
            @Param("keyword") String keyword, @Param("rootMaterialCode") String rootMaterialCode, @Param("view") String view, @Param("offset") long offset, @Param("pageSize") int pageSize);
    long countRows(@Param("runId") Long runId, @Param("factoryCode") String factoryCode, @Param("keyword") String keyword, @Param("rootMaterialCode") String rootMaterialCode, @Param("view") String view);
    List<MrpDailyPlanDetail> selectMachineRowPage(@Param("runId") Long runId, @Param("factoryCode") String factoryCode,
            @Param("keyword") String keyword, @Param("rootMaterialCode") String rootMaterialCode,
            @Param("offset") long offset, @Param("pageSize") int pageSize);
    long countMachineRows(@Param("runId") Long runId, @Param("factoryCode") String factoryCode,
            @Param("keyword") String keyword, @Param("rootMaterialCode") String rootMaterialCode);
    List<MrpDailyPlanDetail> selectMachineRowFacts(@Param("runId") Long runId, @Param("rows") List<MrpDailyPlanDetail> rows);
    List<MrpDailyPlanDetail> selectRowFacts(@Param("runId") Long runId, @Param("rows") List<MrpDailyPlanDetail> rows);
    List<MrpDailyPlanDetail> selectMachineFacts(@Param("runId") Long runId);
    boolean hasOverrides(@Param("batchId") Long batchId);
    List<MrpDailyPlanDetail> selectMachineLoads(@Param("runId") Long runId, @Param("batchId") Long batchId,
            @Param("rolling") boolean rolling);
    List<MrpDailyPlanDetail> selectDetails(@Param("runId") Long runId, @Param("batchId") Long batchId,
            @Param("factoryCode") String factoryCode, @Param("rootMaterialCode") String rootMaterialCode,
            @Param("terminalMaterialCode") String terminalMaterialCode, @Param("terminalType") String terminalType,
            @Param("sourceMonth") LocalDate sourceMonth,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
            @Param("rolling") boolean rolling, @Param("hasOverrides") boolean hasOverrides,
            @Param("offset") long offset, @Param("pageSize") int pageSize, @Param("virtualCount") Long virtualCount);
    long countVirtualDetails(@Param("runId") Long runId, @Param("batchId") Long batchId,
            @Param("factoryCode") String factoryCode, @Param("rootMaterialCode") String rootMaterialCode,
            @Param("terminalMaterialCode") String terminalMaterialCode, @Param("terminalType") String terminalType,
            @Param("sourceMonth") LocalDate sourceMonth,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    long countDetails(@Param("runId") Long runId, @Param("batchId") Long batchId,
            @Param("factoryCode") String factoryCode, @Param("rootMaterialCode") String rootMaterialCode,
            @Param("terminalMaterialCode") String terminalMaterialCode, @Param("terminalType") String terminalType,
            @Param("sourceMonth") LocalDate sourceMonth,
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("rolling") boolean rolling,
            @Param("hasOverrides") boolean hasOverrides);
}
