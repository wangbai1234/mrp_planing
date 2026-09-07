package com.mrp.inventory.domain;

import java.time.Instant;
import java.time.LocalDate;

public record InventorySnapshot(
        Long id,
        LocalDate snapshotDate,
        String fileName,
        String fileChecksum,
        String status,
        Long createdBy,
        Instant createdAt
) {
    public static final String STATUS_IMPORTED = "IMPORTED";
}
