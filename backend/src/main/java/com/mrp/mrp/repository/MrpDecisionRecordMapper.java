package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpDecisionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MrpDecisionRecordMapper {
    int insert(MrpDecisionRecord record);
    MrpDecisionRecord selectById(@Param("id") Long id);
}
