package com.mrp.task.domain;

import java.time.Instant;

public class CalcTask {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private String taskType;
    private String businessScope;
    private String requestKey;
    private String status;
    private Integer priority;
    private String workerId;
    private Instant leaseUntil;
    private Instant heartbeatAt;
    private Integer progressCurrent;
    private Integer progressTotal;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Instant nextRunAt;
    private String inputPayload;
    private String inputChecksum;
    private Long resultResourceId;
    private String errorCode;
    private String errorMessage;
    private Long createdBy;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer version;

    public CalcTask() {}

    public CalcTask(Long id, String taskType, String businessScope, String requestKey, String status,
                    Integer priority, String workerId, Instant leaseUntil, Instant heartbeatAt,
                    Integer progressCurrent, Integer progressTotal, Integer attemptCount, Integer maxAttempts,
                    Instant nextRunAt, String inputPayload, String inputChecksum, Long resultResourceId,
                    String errorCode, String errorMessage, Long createdBy, Instant createdAt,
                    Instant startedAt, Instant finishedAt, Integer version) {
        this.id = id;
        this.taskType = taskType;
        this.businessScope = businessScope;
        this.requestKey = requestKey;
        this.status = status;
        this.priority = priority;
        this.workerId = workerId;
        this.leaseUntil = leaseUntil;
        this.heartbeatAt = heartbeatAt;
        this.progressCurrent = progressCurrent;
        this.progressTotal = progressTotal;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.nextRunAt = nextRunAt;
        this.inputPayload = inputPayload;
        this.inputChecksum = inputChecksum;
        this.resultResourceId = resultResourceId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.version = version;
    }

    public Long id() { return id; }
    public String taskType() { return taskType; }
    public String businessScope() { return businessScope; }
    public String requestKey() { return requestKey; }
    public String status() { return status; }
    public Integer priority() { return priority; }
    public String workerId() { return workerId; }
    public Instant leaseUntil() { return leaseUntil; }
    public Instant heartbeatAt() { return heartbeatAt; }
    public Integer progressCurrent() { return progressCurrent; }
    public Integer progressTotal() { return progressTotal; }
    public Integer attemptCount() { return attemptCount; }
    public Integer maxAttempts() { return maxAttempts; }
    public Instant nextRunAt() { return nextRunAt; }
    public String inputPayload() { return inputPayload; }
    public String inputChecksum() { return inputChecksum; }
    public Long resultResourceId() { return resultResourceId; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public Long createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public Integer version() { return version; }

    public void setId(Long id) { this.id = id; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public void setBusinessScope(String businessScope) { this.businessScope = businessScope; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public void setStatus(String status) { this.status = status; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public void setProgressCurrent(Integer progressCurrent) { this.progressCurrent = progressCurrent; }
    public void setProgressTotal(Integer progressTotal) { this.progressTotal = progressTotal; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public void setInputPayload(String inputPayload) { this.inputPayload = inputPayload; }
    public void setInputChecksum(String inputChecksum) { this.inputChecksum = inputChecksum; }
    public void setResultResourceId(Long resultResourceId) { this.resultResourceId = resultResourceId; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public void setVersion(Integer version) { this.version = version; }
}
