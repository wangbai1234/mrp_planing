package com.mrp.mrp.repository;

import com.mrp.mrp.domain.ProductionCalendarVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductionCalendarVersionMapper {
    int insert(ProductionCalendarVersion version);
    int update(ProductionCalendarVersion version);
    ProductionCalendarVersion selectById(@Param("id") Long id);
    List<ProductionCalendarVersion> selectAll();
    List<ProductionCalendarVersion> selectVisible(@Param("userId") Long userId);
    int canAccess(@Param("id") Long id, @Param("userId") Long userId);
    int hasAllScope(@Param("userId") Long userId);
    int canUseFactory(@Param("factoryCode") String factoryCode, @Param("userId") Long userId);
    int countReferences(@Param("id") Long id);
    int markDeleted(@Param("id") Long id);
}
