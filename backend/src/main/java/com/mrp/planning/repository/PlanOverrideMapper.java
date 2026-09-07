package com.mrp.planning.repository;

import com.mrp.planning.domain.PlanOverride;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PlanOverrideMapper {

    int insert(PlanOverride override);

    int upsert(PlanOverride override);

    PlanOverride selectByUnique(@Param("planVersionId") Long planVersionId,
                                @Param("materialId") String materialId,
                                @Param("weekStartDate") LocalDate weekStartDate);

    List<PlanOverride> selectByVersionId(@Param("planVersionId") Long planVersionId);

    int deleteByUnique(@Param("planVersionId") Long planVersionId,
                       @Param("materialId") String materialId,
                       @Param("weekStartDate") LocalDate weekStartDate);
}
