package com.mrp.planning.domain;

import java.time.LocalDate;

public record PlanDetail(
        Long id,
        Long planVersionId,
        String factoryCode,
        String materialId,
        String materialName,
        String model,
        String meMaterialId,
        LocalDate weekStartDate,
        LocalDate physicalMonth,
        LocalDate sourceMonth,
        int slot,
        boolean isCarry,
        boolean isLocked,
        long systemQuantity,
        Long manualQuantity,
        long effectiveQuantity,
        boolean capacityExceeded,
        long capacityExcessQty,
        String rootMaterialCode,
        String rootMaterialName
) {}
