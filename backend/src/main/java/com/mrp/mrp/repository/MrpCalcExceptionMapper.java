package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpCalcException;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MrpCalcExceptionMapper {
    int insert(MrpCalcException exception);
    int insertBatch(@Param("list") List<MrpCalcException> list);
    List<MrpCalcException> selectByRunId(@Param("runId") Long runId);
}
