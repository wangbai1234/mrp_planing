package com.mrp.task.domain;

import java.time.Instant;

public record ExportTask(
        Long id,
        String taskType,
        String businessScope,
        String requestKey,
        String status,
        Integer priority,
        String workerId,
        Instant leaseUntil,
        Instant heartbeatAt,
        Integer progressCurrent,
        Integer progressTotal,
        Integer attemptCount,
        Integer maxAttempts,
        Instant nextRunAt,
        String inputPayload,
        String inputChecksum,
        Long resultResourceId,
        String errorCode,
        String errorMessage,
        Long createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer version
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
}
