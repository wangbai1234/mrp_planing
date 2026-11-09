package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpTerminalDemand;
import com.mrp.mrp.domain.MrpInventoryPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpTerminalDemandMapper {
    int insert(MrpTerminalDemand demand);
    int insertBatch(@Param("list") List<MrpTerminalDemand> list);
    List<MrpTerminalDemand> selectByRunId(@Param("runId") Long runId);
    List<MrpTerminalDemand> selectPageByRunId(@Param("runId") Long runId, @Param("keyword") String keyword,
                                               @Param("offset") long offset, @Param("pageSize") int pageSize);
    int countByRunId(@Param("runId") Long runId, @Param("keyword") String keyword);
    List<MrpTerminalDemand> selectShortagePage(@Param("runId") Long runId, @Param("factoryCode") String factoryCode,
            @Param("rootMaterialCode") String rootMaterialCode, @Param("terminalMaterialCode") String terminalMaterialCode,
            @Param("sourceMonth") java.time.LocalDate sourceMonth, @Param("keyword") String keyword,
            @Param("offset") long offset, @Param("pageSize") int pageSize);
    long countShortagePage(@Param("runId") Long runId, @Param("factoryCode") String factoryCode,
            @Param("rootMaterialCode") String rootMaterialCode, @Param("terminalMaterialCode") String terminalMaterialCode,
            @Param("sourceMonth") java.time.LocalDate sourceMonth, @Param("keyword") String keyword);
    List<MrpTerminalDemand> selectShortageSummary(@Param("runId") Long runId,
            @Param("poolBatchId") Long poolBatchId, @Param("riskBatchId") Long riskBatchId,
            @Param("issueBatchId") Long issueBatchId,
            @Param("factoryCode") String factoryCode, @Param("terminalMaterialCode") String terminalMaterialCode,
            @Param("sourceMonth") java.time.LocalDate sourceMonth, @Param("keyword") String keyword,
            @Param("offset") long offset, @Param("pageSize") int pageSize);
    long countShortageSummary(@Param("runId") Long runId,
            @Param("factoryCode") String factoryCode, @Param("terminalMaterialCode") String terminalMaterialCode,
            @Param("sourceMonth") java.time.LocalDate sourceMonth, @Param("keyword") String keyword);
    List<MrpInventoryPool> selectShortagePools(@Param("batchId") Long batchId,
            @Param("factoryCode") String factoryCode, @Param("materialCode") String materialCode,
            @Param("offset") long offset, @Param("pageSize") int pageSize);
    long countShortagePools(@Param("batchId") Long batchId,
            @Param("factoryCode") String factoryCode, @Param("materialCode") String materialCode);
    List<MrpTerminalDemand> selectByBatchId(@Param("batchId") Long batchId);
    List<MrpTerminalDemand> selectAggregatedByBatchId(@Param("batchId") Long batchId);
    List<MrpTerminalDemand> selectAggregatedByRunId(@Param("runId") Long runId);

    int deleteByRunId(@Param("runId") Long runId);
    int deleteMaterialByRunId(@Param("runId") Long runId);
}
