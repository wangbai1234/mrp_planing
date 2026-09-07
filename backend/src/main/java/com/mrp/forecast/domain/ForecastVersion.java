package com.mrp.forecast.domain;

import java.time.Instant;
import java.time.LocalDate;

public record ForecastVersion(
        Long id,
        Integer versionNo,
        String fileName,
        String fileChecksum,
        String status,
        Long createdBy,
        Instant createdAt
) {
    public static final String STATUS_IMPORTED = "IMPORTED";
}
