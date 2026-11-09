package com.mrp.mrp.repository;

import com.mrp.mrp.domain.MrpMaterialPolicyNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MrpMaterialPolicyNoticeMapper {
    int insertBatch(@Param("list") List<MrpMaterialPolicyNotice> rows);
    int deleteByRunId(@Param("runId") Long runId);
    int copyRun(@Param("sourceRunId") Long sourceRunId, @Param("batchId") Long batchId, @Param("runId") Long runId);
    List<MrpMaterialPolicyNotice> selectPage(@Param("runId") Long runId, @Param("keyword") String keyword,
            @Param("missingType") String missingType, @Param("offset") int offset, @Param("limit") int limit);
    int countPage(@Param("runId") Long runId, @Param("keyword") String keyword, @Param("missingType") String missingType);
    int countPendingDefaultExplosion(@Param("runId") Long runId);
    int countConfirmedByRunId(@Param("runId") Long runId);
    int confirmDefaultExplosion(@Param("runId") Long runId, @Param("operatorId") Long operatorId);
    int canReadFactory(@Param("userId") Long userId, @Param("factory") String factory);
}
