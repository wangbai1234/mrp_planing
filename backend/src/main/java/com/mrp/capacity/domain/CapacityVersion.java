package com.mrp.capacity.domain;

import java.time.Instant;

public record CapacityVersion(
        Long id,
        Integer versionNo,
        String status,
        Long createdBy,
        Instant createdAt
) {
    public static final String STATUS_ACTIVE = "ACTIVE";
}
