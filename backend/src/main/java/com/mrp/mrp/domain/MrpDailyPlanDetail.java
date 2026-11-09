package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MrpDailyPlanDetail {
    private Long id;
    private Long runId;
    private Long batchId;
    private String factoryCode;
    private String rootMaterialCode;
    private String terminalMaterialCode;
    private String terminalType;
    private String supplyMode;
    private String pathGroupKey;
    private LocalDate sourceMonth;
    private LocalDate sourceDate;
    private LocalDate scheduleDate;
    private BigDecimal systemQuantity;
    private Boolean isProductionDay;
    private Integer revision;
    private String rowKey;
    /** Effective value after applying the latest manual override; systemQuantity remains immutable. */
    private BigDecimal effectiveQuantity;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getRootMaterialCode() { return rootMaterialCode; }
    public void setRootMaterialCode(String rootMaterialCode) { this.rootMaterialCode = rootMaterialCode; }
    public String getTerminalMaterialCode() { return terminalMaterialCode; }
    public void setTerminalMaterialCode(String terminalMaterialCode) { this.terminalMaterialCode = terminalMaterialCode; }
    public String getTerminalType() { return terminalType; }
    public void setTerminalType(String terminalType) { this.terminalType = terminalType; }
    public String getSupplyMode() { return supplyMode; }
    public void setSupplyMode(String supplyMode) { this.supplyMode = supplyMode; }
    public String getPathGroupKey() { return pathGroupKey; }
    public void setPathGroupKey(String pathGroupKey) { this.pathGroupKey = pathGroupKey; }
    public LocalDate getSourceMonth() { return sourceMonth; }
    public void setSourceMonth(LocalDate sourceMonth) { this.sourceMonth = sourceMonth; }
    public LocalDate getSourceDate() { return sourceDate; }
    public void setSourceDate(LocalDate sourceDate) { this.sourceDate = sourceDate; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public BigDecimal getSystemQuantity() { return systemQuantity; }
    public void setSystemQuantity(BigDecimal systemQuantity) { this.systemQuantity = systemQuantity; }
    public Boolean getIsProductionDay() { return isProductionDay; }
    public void setIsProductionDay(Boolean isProductionDay) { this.isProductionDay = isProductionDay; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public String getRowKey() { return rowKey; }
    public void setRowKey(String rowKey) { this.rowKey = rowKey; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public BigDecimal getEffectiveQuantity() { return effectiveQuantity != null ? effectiveQuantity : systemQuantity; }
    public void setEffectiveQuantity(BigDecimal effectiveQuantity) { this.effectiveQuantity = effectiveQuantity; }
}
