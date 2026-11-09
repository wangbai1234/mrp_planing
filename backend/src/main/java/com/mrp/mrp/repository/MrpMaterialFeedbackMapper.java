package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpMaterialFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MrpMaterialFeedbackMapper {
    int insert(@Param("item") MrpMaterialFeedback item);
    List<MrpMaterialFeedback> page(@Param("batchId") Long batchId, @Param("offset") long offset, @Param("size") int size);
    int count(@Param("batchId") Long batchId);
    int countOpen(@Param("batchId") Long batchId);
    MrpMaterialFeedback byId(@Param("id") Long id);
    int respond(@Param("id") Long id, @Param("response") String response, @Param("operatorId") Long operatorId);
}
