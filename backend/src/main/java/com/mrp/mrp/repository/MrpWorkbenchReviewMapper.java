package com.mrp.mrp.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface MrpWorkbenchReviewMapper {
    record Review(Long id, Long batchId, String reviewType, Long runId,
                  String scheduleFingerprint, String materialFingerprint,
                  Long operatorId, LocalDateTime createdAt) {}
    int insert(@Param("batchId") Long batchId, @Param("reviewType") String reviewType,
               @Param("runId") Long runId, @Param("scheduleFingerprint") String scheduleFingerprint,
               @Param("materialFingerprint") String materialFingerprint, @Param("operatorId") Long operatorId);
    Review latest(@Param("batchId") Long batchId, @Param("reviewType") String reviewType);
    int copyPublished(@Param("sourceBatchId") Long sourceBatchId, @Param("targetBatchId") Long targetBatchId,
                      @Param("targetRunId") Long targetRunId);
}
