package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpInventoryPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpInventoryPoolMapper {
    int insert(MrpInventoryPool pool);
    int update(MrpInventoryPool pool);
    MrpInventoryPool selectById(@Param("id") Long id);
    List<MrpInventoryPool> selectByBatchId(@Param("batchId") Long batchId);
    List<MrpInventoryPool> selectPageByBatchId(@Param("batchId") Long batchId, @Param("offset") long offset, @Param("pageSize") int pageSize);
    int countByBatchId(@Param("batchId") Long batchId);
    MrpInventoryPool selectByBatchAndMaterial(@Param("batchId") Long batchId, @Param("materialCode") String materialCode);
    int rebuildAllocatedQuantitiesByBatchId(@Param("batchId") Long batchId);
}
