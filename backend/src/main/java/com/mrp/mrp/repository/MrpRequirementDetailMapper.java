package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpRequirementDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpRequirementDetailMapper {
    int insert(MrpRequirementDetail detail);
    int insertBatch(@Param("list") List<MrpRequirementDetail> list);
    List<MrpRequirementDetail> selectByRunId(@Param("runId") Long runId);
    List<MrpRequirementDetail> selectPageByRunId(@Param("runId") Long runId, @Param("offset") long offset, @Param("pageSize") int pageSize);
    int countByRunId(@Param("runId") Long runId);
    List<MrpRequirementDetail> selectByRunIdAndRoot(@Param("runId") Long runId, @Param("rootMaterialCode") String rootMaterialCode);
    List<MrpRequirementDetail> selectByBatchId(@Param("batchId") Long batchId);

    int deleteByRunId(@Param("runId") Long runId);
    int deleteMaterialByRunId(@Param("runId") Long runId);
}
