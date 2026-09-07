package com.mrp.capacity.domain;

import java.time.LocalDate;

public record CapacityLine(
        Long id,
        Long versionId,
        String factoryCode,
        String lineCode,
        String lineName,
        Long weeklyCapacity,
        LocalDate effectiveDate,
        Boolean isActive,
        String remark
) {}
