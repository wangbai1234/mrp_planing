package com.mrp.capacity.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public class CapacityLine {

    private Long id;
    private Long versionId;
    private String factoryCode;
    private String lineCode;
    private String lineName;
    private Long weeklyCapacity;
    private LocalDate effectiveDate;
    private Boolean isActive;
    private String remark;

    public CapacityLine() {}

    public CapacityLine(Long id, Long versionId, String factoryCode, String lineCode,
                        String lineName, Long weeklyCapacity, LocalDate effectiveDate,
                        Boolean isActive, String remark) {
        this.id = id;
        this.versionId = versionId;
        this.factoryCode = factoryCode;
        this.lineCode = lineCode;
        this.lineName = lineName;
        this.weeklyCapacity = weeklyCapacity;
        this.effectiveDate = effectiveDate;
        this.isActive = isActive;
        this.remark = remark;
    }

    public Long id() { return id; }
    public Long versionId() { return versionId; }
    public String factoryCode() { return factoryCode; }
    public String lineCode() { return lineCode; }
    public String lineName() { return lineName; }
    public Long weeklyCapacity() { return weeklyCapacity; }
    public LocalDate effectiveDate() { return effectiveDate; }
    public String remark() { return remark; }

    public Long getId() { return id; }
    public Long getVersionId() { return versionId; }
    public String getFactoryCode() { return factoryCode; }
    public String getLineCode() { return lineCode; }
    public String getLineName() { return lineName; }
    public Long getWeeklyCapacity() { return weeklyCapacity; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    @JsonProperty("isActive")
    public Boolean getIsActive() { return isActive; }
    public String getRemark() { return remark; }

    public void setId(Long id) { this.id = id; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }
    public void setLineName(String lineName) { this.lineName = lineName; }
    public void setWeeklyCapacity(Long weeklyCapacity) { this.weeklyCapacity = weeklyCapacity; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setRemark(String remark) { this.remark = remark; }
}
