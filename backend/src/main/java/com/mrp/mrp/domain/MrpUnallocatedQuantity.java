package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MrpUnallocatedQuantity {
    private Long id;
    private Long runId;
    private Long batchId;
    private String demandKey;
    private LocalDate sourceDate;
    private LocalDate attemptedScheduleDate;
    private BigDecimal quantity;
    private String reasonCode;
    private String status;
    private LocalDate resolvedScheduleDate;
    private Integer revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getDemandKey() { return demandKey; }
    public void setDemandKey(String demandKey) { this.demandKey = demandKey; }
    public LocalDate getSourceDate() { return sourceDate; }
    public void setSourceDate(LocalDate sourceDate) { this.sourceDate = sourceDate; }
    public LocalDate getAttemptedScheduleDate() { return attemptedScheduleDate; }
    public void setAttemptedScheduleDate(LocalDate attemptedScheduleDate) { this.attemptedScheduleDate = attemptedScheduleDate; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getResolvedScheduleDate() { return resolvedScheduleDate; }
    public void setResolvedScheduleDate(LocalDate resolvedScheduleDate) { this.resolvedScheduleDate = resolvedScheduleDate; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
}
