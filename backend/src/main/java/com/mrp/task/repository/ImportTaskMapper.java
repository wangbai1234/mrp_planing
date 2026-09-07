package com.mrp.task.repository;

import com.mrp.task.domain.ImportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ImportTaskMapper {

    int insert(ImportTask task);

    ImportTask selectById(@Param("id") Long id);

    ImportTask selectByRequestKey(@Param("requestKey") String requestKey);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("version") Integer version);

    int updateProgress(@Param("id") Long id, @Param("current") Integer current, @Param("total") Integer total);

    int updateResult(@Param("id") Long id, @Param("status") String status, @Param("resultResourceId") Long resultResourceId, @Param("version") Integer version);

    int updateFailure(@Param("id") Long id, @Param("status") String status, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("version") Integer version);
}
