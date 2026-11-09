package com.mrp.mrp.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FactoryMasterMapper {
    List<Map<String, Object>> selectAll();
    List<Map<String, Object>> selectPage(@Param("visible") List<String> visible, @Param("keyword") String keyword,
            @Param("status") String status, @Param("limit") int limit, @Param("offset") long offset);
    int countPage(@Param("visible") List<String> visible, @Param("keyword") String keyword,
            @Param("status") String status);
    List<String> selectEnabledCodes();
    int countEnabled(@Param("code") String code);
    Map<String, Object> selectByCode(@Param("code") String code);
    int countReferences(@Param("code") String code);
    int insert(@Param("code") String code, @Param("name") String name,
            @Param("enabled") boolean enabled, @Param("userId") Long userId);
    int update(@Param("code") String code, @Param("name") String name,
            @Param("enabled") boolean enabled, @Param("userId") Long userId);
    int delete(@Param("code") String code);
}
