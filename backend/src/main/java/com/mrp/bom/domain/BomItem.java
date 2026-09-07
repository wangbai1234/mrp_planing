package com.mrp.bom.domain;

import java.time.LocalDate;

public record BomItem(
    String orgName,
    String parentCode,
    String parentName,
    String version,
    Long parentQty,
    LocalDate effectiveDate,
    String childCode,
    String childName,
    String materialSpec,
    String makeFactory,
    Long childQty,
    String childNote,
    String replacePriority,
    String altCode,
    String altName,
    String altSpec,
    String altFactory,
    Long altQty,
    String isDeliver,
    String isDefault,
    String mainStatus,
    String altStatus,
    String mainRhosStatus,
    String altRhosStatus,
    String admitNote,
    String bodyCode
) {}
