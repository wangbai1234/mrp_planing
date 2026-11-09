package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MrpDailyPlanOverride {
    private Long id;
    private Long batchId;
    private String factoryCode;
    private String rootMaterialCode;
    private String terminalMaterialCode;
    private String terminalType;
    private String supplyMode;
    private String pathGroupKey;
    private LocalDate scheduleDate;
    private BigDecimal manualQuantity;
    private String reason;
    private Integer revision;
    private Long operatorId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    public BigDecimal getManualQuantity() { return manualQuantity; }
    public void setManualQuantity(BigDecimal manualQuantity) { this.manualQuantity = manualQuantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
