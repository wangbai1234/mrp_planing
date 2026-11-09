package com.mrp.mrp.repository;

import com.mrp.mrp.domain.ProductionCalendarDay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ProductionCalendarDayMapper {
    int insert(ProductionCalendarDay day);
    int insertBatch(@Param("list") List<ProductionCalendarDay> list);
    int update(ProductionCalendarDay day);
    List<ProductionCalendarDay> selectByVersionId(@Param("versionId") Long versionId);
    List<ProductionCalendarDay> selectByFactoryAndDateRange(@Param("versionId") Long versionId, @Param("factoryCode") String factoryCode, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    int deleteByVersionId(@Param("versionId") Long versionId);
}
