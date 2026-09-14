package com.mrp.task.domain;

import java.time.Instant;

public class ExportTask {
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
    private String filePath;

    public ExportTask() {}

    public ExportTask(Long id, String taskType, String businessScope, String requestKey, String status,
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getBusinessScope() { return businessScope; }
    public void setBusinessScope(String businessScope) { this.businessScope = businessScope; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public Instant getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(Instant heartbeatAt) { this.heartbeatAt = heartbeatAt; }
    public Integer getProgressCurrent() { return progressCurrent; }
    public void setProgressCurrent(Integer progressCurrent) { this.progressCurrent = progressCurrent; }
    public Integer getProgressTotal() { return progressTotal; }
    public void setProgressTotal(Integer progressTotal) { this.progressTotal = progressTotal; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public String getInputPayload() { return inputPayload; }
    public void setInputPayload(String inputPayload) { this.inputPayload = inputPayload; }
    public String getInputChecksum() { return inputChecksum; }
    public void setInputChecksum(String inputChecksum) { this.inputChecksum = inputChecksum; }
    public Long getResultResourceId() { return resultResourceId; }
    public void setResultResourceId(Long resultResourceId) { this.resultResourceId = resultResourceId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
