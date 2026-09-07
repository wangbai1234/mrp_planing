package com.mrp.forecast.domain;

import java.time.LocalDate;

public record ForecastDetail(
        Long id,
        Long versionId,
        String factoryCode,
        String materialId,
        String materialName,
        String businessLine,
        String formType,
        String project,
        String platform,
        String mold,
        String status,
        LocalDate planMonth,
        Long forecastQty
) {}
