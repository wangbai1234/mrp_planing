package com.mrp.shipment.domain;

import java.time.Instant;

public record ShipmentBatch(
        Long id,
        String source,
        String status,
        Long createdBy,
        Instant createdAt
) {
    public static final String SOURCE_CRM_API = "CRM_API";
    public static final String SOURCE_IMPORT = "IMPORT";
    public static final String STATUS_IMPORTED = "IMPORTED";
}
