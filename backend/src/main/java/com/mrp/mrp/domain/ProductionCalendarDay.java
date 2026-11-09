package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductionCalendarDay {
    private Long id;
    private Long versionId;
    private String factoryCode;
    private LocalDate calendarDate;
    private Boolean isProductionDay;
    private BigDecimal allocationWeight;
    private BigDecimal dailyCapacity;
    private String capacityUnit;
    private String reason;
    private Integer revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public LocalDate getCalendarDate() { return calendarDate; }
    public void setCalendarDate(LocalDate calendarDate) { this.calendarDate = calendarDate; }
    public Boolean getIsProductionDay() { return isProductionDay; }
    public void setIsProductionDay(Boolean isProductionDay) { this.isProductionDay = isProductionDay; }
    public BigDecimal getAllocationWeight() { return allocationWeight; }
    public void setAllocationWeight(BigDecimal allocationWeight) { this.allocationWeight = allocationWeight; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public BigDecimal getDailyCapacity() { return dailyCapacity; }
    public void setDailyCapacity(BigDecimal dailyCapacity) { this.dailyCapacity = dailyCapacity; }
    public String getCapacityUnit() { return capacityUnit; }
    public void setCapacityUnit(String capacityUnit) { this.capacityUnit = capacityUnit; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
}
