package com.mrp.task.repository;

import com.mrp.task.domain.CalcTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CalcTaskMapper {

    int insert(CalcTask task);

    CalcTask selectById(@Param("id") Long id);

    CalcTask selectByRequestKey(@Param("requestKey") String requestKey);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("version") Integer version);

    int updateResult(@Param("id") Long id, @Param("status") String status, @Param("resultResourceId") Long resultResourceId, @Param("version") Integer version);

    int updateFailure(@Param("id") Long id, @Param("status") String status, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("version") Integer version);
}
