package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpInventoryAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpInventoryAllocationMapper {
    int insert(MrpInventoryAllocation allocation);
    int insertBatch(@Param("list") List<MrpInventoryAllocation> list);
    int update(MrpInventoryAllocation allocation);
    MrpInventoryAllocation selectById(@Param("id") Long id);
    List<MrpInventoryAllocation> selectByPoolId(@Param("poolId") Long poolId);
    int countByPoolId(@Param("poolId") Long poolId);
    List<MrpInventoryAllocation> selectByBatchId(@Param("batchId") Long batchId);
    int deleteAutoByBatchId(@Param("batchId") Long batchId);
    int deleteAutoMaterialByBatchId(@Param("batchId") Long batchId);
}
