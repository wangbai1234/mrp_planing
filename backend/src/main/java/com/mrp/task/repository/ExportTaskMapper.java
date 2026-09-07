package com.mrp.task.repository;

import com.mrp.task.domain.ExportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExportTaskMapper {

    int insert(ExportTask task);

    ExportTask selectById(@Param("id") Long id);

    ExportTask selectByRequestKey(@Param("requestKey") String requestKey);

    int updateResult(@Param("id") Long id, @Param("status") String status, @Param("resultResourceId") Long resultResourceId, @Param("version") Integer version);

    int updateFailure(@Param("id") Long id, @Param("status") String status, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("version") Integer version);
}
