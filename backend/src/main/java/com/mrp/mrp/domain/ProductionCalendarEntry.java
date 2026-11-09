package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ProductionCalendarEntry extends ConfigAudit {
    private String factoryCode;
    private LocalDate calendarDate;
    private Boolean productionDay;
    private BigDecimal allocationWeight;
    private BigDecimal dailyCapacity;
    private Integer revision;
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    private String capacityUnit;
    private String reason;

    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public LocalDate getCalendarDate() { return calendarDate; }
    public void setCalendarDate(LocalDate calendarDate) { this.calendarDate = calendarDate; }
    public Boolean getProductionDay() { return productionDay; }
    public void setProductionDay(Boolean productionDay) { this.productionDay = productionDay; }
    public BigDecimal getAllocationWeight() { return allocationWeight; }
    public void setAllocationWeight(BigDecimal allocationWeight) { this.allocationWeight = allocationWeight; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public BigDecimal getDailyCapacity() { return dailyCapacity; }
    public void setDailyCapacity(BigDecimal dailyCapacity) { this.dailyCapacity = dailyCapacity; }
    public String getCapacityUnit() { return capacityUnit; }
    public void setCapacityUnit(String capacityUnit) { this.capacityUnit = capacityUnit; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
