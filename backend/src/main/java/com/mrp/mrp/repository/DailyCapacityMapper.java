package com.mrp.mrp.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DailyCapacityMapper {
    int freezeUnits(@Param("versionId") Long versionId);
    List<MaterialUnit> selectUnits(@Param("versionId") Long versionId);
    record MaterialUnit(String materialCode, String unit) {}
}
