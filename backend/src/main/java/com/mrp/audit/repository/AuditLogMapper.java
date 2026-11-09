package com.mrp.audit.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper {
    long countVisible(@Param("userId") Long userId, @Param("filters") Map<String, String> filters);

    List<Map<String, Object>> selectVisible(@Param("userId") Long userId,
            @Param("filters") Map<String, String> filters, @Param("limit") int limit,
            @Param("offset") long offset);
}
