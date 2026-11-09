package com.mrp.mrp.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface LegacyScopeVersionAccessMapper {
    List<Map<String, Object>> mappingVersions(@Param("userId") Long userId);
    List<Map<String, Object>> stockVersions(@Param("userId") Long userId);
    int canReadMapping(@Param("id") Long id, @Param("userId") Long userId);
    int canReadStock(@Param("id") Long id, @Param("userId") Long userId);
    int hasAllScope(@Param("userId") Long userId);
    int canUseFactory(@Param("factoryCode") String factoryCode, @Param("userId") Long userId);
}
