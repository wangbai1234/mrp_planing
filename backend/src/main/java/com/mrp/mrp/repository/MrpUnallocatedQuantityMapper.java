package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpUnallocatedQuantity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpUnallocatedQuantityMapper {
    int insert(MrpUnallocatedQuantity unallocated);
    int insertBatch(@Param("list") List<MrpUnallocatedQuantity> list);
    int update(MrpUnallocatedQuantity unallocated);
    List<MrpUnallocatedQuantity> selectByRunId(@Param("runId") Long runId);
    List<MrpUnallocatedQuantity> selectByRunIdAndStatus(@Param("runId") Long runId, @Param("status") String status);
    List<MrpUnallocatedQuantity> selectPageByRunIdAndStatus(@Param("runId") Long runId, @Param("status") String status, @Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);
    int countByRunIdAndStatus(@Param("runId") Long runId, @Param("status") String status, @Param("keyword") String keyword);
    List<MrpUnallocatedQuantity> selectByBatchId(@Param("batchId") Long batchId);
    List<MrpUnallocatedQuantity> selectByBatchIdAndStatus(@Param("batchId") Long batchId, @Param("status") String status);

    int deleteByRunId(@Param("runId") Long runId);
    int deleteMaterialByRunId(@Param("runId") Long runId);
    int countResolvedMaterialByRunId(@Param("runId") Long runId);
}
