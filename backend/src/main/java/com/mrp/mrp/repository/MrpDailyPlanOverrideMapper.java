package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpDailyPlanOverride;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpDailyPlanOverrideMapper {
    int insert(MrpDailyPlanOverride override);
    int update(MrpDailyPlanOverride override);
    int deleteById(@Param("id") Long id);
    MrpDailyPlanOverride selectById(@Param("id") Long id);
    MrpDailyPlanOverride selectByBusinessKey(MrpDailyPlanOverride override);
    List<MrpDailyPlanOverride> selectByBatchId(@Param("batchId") Long batchId);
}
