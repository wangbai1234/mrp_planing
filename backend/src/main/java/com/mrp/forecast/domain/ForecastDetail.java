package com.mrp.forecast.domain;

import java.math.BigDecimal;
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
        BigDecimal forecastQty,
        Integer prioritySeq
) {
    /** Backward-compatible constructor for callers that build forecast rows before persistence. */
    public ForecastDetail(Long id, Long versionId, String factoryCode, String materialId,
                          String materialName, String businessLine, String formType, String project,
                          String platform, String mold, String status, LocalDate planMonth,
                          BigDecimal forecastQty) {
        this(id, versionId, factoryCode, materialId, materialName, businessLine, formType, project,
                platform, mold, status, planMonth, forecastQty,
                id != null && id <= Integer.MAX_VALUE ? id.intValue() : 0);
    }
}
