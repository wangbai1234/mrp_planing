package com.mrp.planning.domain;

import java.time.Instant;
import java.time.LocalDate;

public record PlanVersion(
        Long id,
        Integer versionNo,
        String factoryCode,
        Long forecastVersionId,
        Long inventorySnapshotId,
        Long shipmentBatchId,
        Long capacityVersionId,
        String ruleVersion,
        LocalDate currentWeekStart,
        String inputChecksum,
        String resultChecksum,
        String status,
        Boolean autoRecalcForecast,
        Boolean autoRecalcInventory,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String STATUS_CALCULATING = "CALCULATING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_FAILED = "FAILED";
}
