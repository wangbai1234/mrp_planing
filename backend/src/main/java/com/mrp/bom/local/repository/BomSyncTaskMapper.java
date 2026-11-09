package com.mrp.bom.local.repository;

import com.mrp.bom.domain.BomSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BomSyncTaskMapper {
    int insertPending(BomSyncTask task);
    BomSyncTask selectActive();
    BomSyncTask selectLatest();
    BomSyncTask selectById(@Param("id") long id);
    BomSyncTask selectPending();
    int claim(@Param("id") long id, @Param("workerId") String workerId);
    int updateProgress(@Param("id") long id, @Param("workerId") String workerId,
                       @Param("current") long current, @Param("total") long total, @Param("phase") String phase);
    int heartbeat(@Param("workerId") String workerId);
    int isOwned(@Param("id") long id, @Param("workerId") String workerId);
    int finish(@Param("id") long id, @Param("workerId") String workerId, @Param("parentCount") long parentCount,
               @Param("zeroQty") long zeroQty, @Param("selfReference") long selfReference,
               @Param("dataValid") boolean dataValid);
    int fail(@Param("id") long id, @Param("workerId") String workerId, @Param("message") String message);
    int failStale();
}
