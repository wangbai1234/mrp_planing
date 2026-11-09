package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpCalcBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PlanningAccessMapper {
    List<String> batchFactories(@Param("batchId") Long batchId);
    Long ownerBatch(@Param("resource") String resource, @Param("id") Long id);
    List<MrpCalcBatch> visibleBatches(@Param("userId") Long userId, @Param("status") String status,
            @Param("keyword") String keyword, @Param("current") boolean current,
            @Param("offset") Long offset, @Param("limit") Integer limit);
    long countVisibleBatches(@Param("userId") Long userId, @Param("status") String status, @Param("keyword") String keyword);
}
