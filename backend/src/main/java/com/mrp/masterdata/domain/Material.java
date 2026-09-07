package com.mrp.masterdata.domain;

import java.time.Instant;

public record Material(
        Long id,
        String materialCode,
        String materialName,
        String projectModel,
        String specModel,
        String unit,
        Long moq,
        Long mpq,
        String region,
        String attribute,
        String category,
        Boolean isActive,
        Integer leadTimeDays,
        String originPlace,
        String dataSource,
        String externalId,
        Boolean isDeleted,
        Instant createdAt,
        Instant updatedAt
) {
    public static final String SOURCE_EXCEL_IMPORT = "EXCEL_IMPORT";
    public static final String SOURCE_API_SYNC = "API_SYNC";

    public Material withId(Long id) {
        return new Material(id, materialCode, materialName, projectModel, specModel, unit,
                moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                dataSource, externalId, isDeleted, createdAt, updatedAt);
    }

    public Material markDeleted() {
        return new Material(id, materialCode, materialName, projectModel, specModel, unit,
                moq, mpq, region, attribute, category, isActive, leadTimeDays, originPlace,
                dataSource, externalId, true, createdAt, updatedAt);
    }
}
