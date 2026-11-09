package com.mrp.task.repository;

import com.mrp.task.domain.CalcTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CalcTaskMapper {

    int insert(CalcTask task);

    CalcTask selectById(@Param("id") Long id);
    CalcTask selectLatestCalculationForBatch(@Param("batchId") Long batchId);
    CalcTask selectLatestLifecycleForBatch(@Param("batchId") Long batchId,
            @Param("taskType") String taskType, @Param("requestPrefix") String requestPrefix);
    java.util.List<CalcTask> selectPendingLifecycle();

    int updateOwnedProgress(@Param("id") Long id, @Param("workerId") String workerId,
                            @Param("version") int version, @Param("percent") int percent,
                            @Param("phase") String phase);

    CalcTask selectByRequestKey(@Param("requestKey") String requestKey);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("version") Integer version);

    int updateResult(@Param("id") Long id, @Param("status") String status, @Param("resultResourceId") Long resultResourceId, @Param("version") Integer version);

    int updateFailure(@Param("id") Long id, @Param("status") String status, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("version") Integer version);

    int failOwnedTask(@Param("id") Long id, @Param("workerId") String workerId,
                      @Param("version") Integer version, @Param("errorMessage") String errorMessage);
    int finishOwnedLifecycle(@Param("id") Long id, @Param("workerId") String workerId,
                             @Param("version") Integer version, @Param("resultId") Long resultId);

    int updateProgress(@Param("id") Long id, @Param("progressCurrent") Integer progressCurrent,
                       @Param("progressTotal") Integer progressTotal, @Param("phase") String phase,
                       @Param("successRoots") Integer successRoots, @Param("failedRoots") Integer failedRoots);

    int deleteById(@Param("id") Long id);

    int failStuckTasks(@Param("timeoutMinutes") int timeoutMinutes);

    java.util.List<CalcTask> selectExpiredRunning();

    int requeueExpired(@Param("id") Long id, @Param("version") Integer version);

    int failExpired(@Param("id") Long id, @Param("version") Integer version);

    java.util.List<CalcTask> selectPending();

    int updateInputPayload(@Param("id") Long id, @Param("inputPayload") String inputPayload);

    int claimTask(@Param("id") Long id, @Param("workerId") String workerId,
                  @Param("leaseMinutes") int leaseMinutes, @Param("version") Integer version);

    int heartbeat(@Param("id") Long id, @Param("workerId") String workerId,
                  @Param("leaseMinutes") int leaseMinutes);

    int heartbeatForWorker(@Param("workerId") String workerId, @Param("leaseMinutes") int leaseMinutes);

    java.util.List<CalcTask> selectByWorkerAndStatus(@Param("workerId") String workerId, @Param("status") String status);
}
