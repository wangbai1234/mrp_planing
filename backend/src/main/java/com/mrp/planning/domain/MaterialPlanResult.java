package com.mrp.planning.domain;

import java.time.LocalDate;

public record MaterialPlanResult(
        String factoryCode,
        String materialId,
        String materialName,
        String model,
        String meMaterialId,
        long forecastQty,
        long inventoryQty,
        long shippedQty,
        long monthlyAvailable,
        java.util.List<WeekPlan> weekPlans
) {}
