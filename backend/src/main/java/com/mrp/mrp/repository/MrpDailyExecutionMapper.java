package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpDailyExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MrpDailyExecutionMapper {

    int insert(MrpDailyExecution execution);

    MrpDailyExecution selectByBatchRowDate(@Param("batchId") Long batchId,
                                           @Param("rowKey") String rowKey,
                                           @Param("scheduleDate") LocalDate scheduleDate);

    List<MrpDailyExecution> selectByBatchId(@Param("batchId") Long batchId);

    List<MrpDailyExecution> selectPage(@Param("batchId") Long batchId,
                                       @Param("runId") Long runId,
                                       @Param("keyword") String keyword,
                                       @Param("scheduleDate") LocalDate scheduleDate,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    int countPage(@Param("batchId") Long batchId,
                  @Param("runId") Long runId,
                  @Param("keyword") String keyword,
                  @Param("scheduleDate") LocalDate scheduleDate);

    List<MrpDailyExecution> selectByBatchAndDateRange(@Param("batchId") Long batchId,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);

    int updateStatus(@Param("id") Long id,
                     @Param("executionStatus") String executionStatus,
                     @Param("actualQuantity") java.math.BigDecimal actualQuantity,
                     @Param("confirmedBy") Long confirmedBy,
                     @Param("remark") String remark,
                     @Param("expectedRevision") Integer expectedRevision);

    int deleteByBatchId(@Param("batchId") Long batchId);

    int freezeStatus(@Param("id") Long id,
                     @Param("executionStatus") String executionStatus,
                     @Param("plannedQuantity") java.math.BigDecimal plannedQuantity,
                     @Param("actualQuantity") java.math.BigDecimal actualQuantity,
                     @Param("confirmedBy") Long confirmedBy,
                     @Param("remark") String remark,
                     @Param("expectedRevision") Integer expectedRevision);
}
