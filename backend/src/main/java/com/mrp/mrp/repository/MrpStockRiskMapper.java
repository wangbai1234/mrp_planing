package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpStockRisk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpStockRiskMapper {
    int copyRun(@Param("sourceRunId") Long sourceRunId, @Param("batchId") Long batchId, @Param("runId") Long runId);
    int deleteByRunId(@Param("runId") Long runId);
    int countResolvedByRunId(@Param("runId") Long runId);
    int insertBatch(@Param("list") List<MrpStockRisk> risks);
    List<MrpStockRisk> selectByBatchId(@Param("batchId") Long batchId, @Param("runId") Long runId);
    List<MrpStockRisk> selectPageByBatchId(@Param("batchId") Long batchId, @Param("runId") Long runId,
                                            @Param("offset") long offset, @Param("pageSize") int pageSize);
    int countByBatchId(@Param("batchId") Long batchId, @Param("runId") Long runId);
    int resolvePendingByRunId(@Param("batchId") Long batchId, @Param("runId") Long runId,
                              @Param("resolvedBy") Long resolvedBy, @Param("resolutionCode") String resolutionCode);
}
