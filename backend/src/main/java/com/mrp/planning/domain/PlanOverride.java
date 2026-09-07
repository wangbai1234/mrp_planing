package com.mrp.planning.domain;

import java.time.Instant;
import java.time.LocalDate;

public record PlanOverride(
        Long id,
        Long planVersionId,
        String materialId,
        LocalDate weekStartDate,
        Long systemQuantity,
        Long manualQuantity,
        String reason,
        Long operatedBy,
        Instant operatedAt
) {}
