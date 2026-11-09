package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpCalcRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MrpCalcRunMapper {
    int insert(MrpCalcRun run);
    int update(MrpCalcRun run);
    MrpCalcRun selectById(@Param("id") Long id);
}
