package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MrpDailyExecution {
    private Long id;
    private Long batchId;
    private String rowKey;
    private String factoryCode;
    private String rootMaterialCode;
    private LocalDate scheduleDate;
    private String executionStatus;
    private BigDecimal actualQuantity;
    private BigDecimal plannedQuantity;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private String remark;
    private Integer revision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getRowKey() { return rowKey; }
    public void setRowKey(String rowKey) { this.rowKey = rowKey; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getRootMaterialCode() { return rootMaterialCode; }
    public void setRootMaterialCode(String rootMaterialCode) { this.rootMaterialCode = rootMaterialCode; }
    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }
    public BigDecimal getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(BigDecimal actualQuantity) { this.actualQuantity = actualQuantity; }
    public BigDecimal getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(BigDecimal plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
