package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MrpInventoryAllocation {
    private Long id;
    private Long poolId;
    private String demandKey;
    private String rootMaterialCode;
    private LocalDate sourceMonth;
    private Integer prioritySeq;
    private String pathHash;
    private BigDecimal grossQty;
    private BigDecimal allocatedQty;
    private String allocationType;
    private String adjustmentReason;
    private Boolean manualConfirmed;
    private Integer revision;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPoolId() { return poolId; }
    public void setPoolId(Long poolId) { this.poolId = poolId; }
    public String getDemandKey() { return demandKey; }
    public void setDemandKey(String demandKey) { this.demandKey = demandKey; }
    public String getRootMaterialCode() { return rootMaterialCode; }
    public void setRootMaterialCode(String rootMaterialCode) { this.rootMaterialCode = rootMaterialCode; }
    public LocalDate getSourceMonth() { return sourceMonth; }
    public void setSourceMonth(LocalDate sourceMonth) { this.sourceMonth = sourceMonth; }
    public Integer getPrioritySeq() { return prioritySeq; }
    public void setPrioritySeq(Integer prioritySeq) { this.prioritySeq = prioritySeq; }
    public String getPathHash() { return pathHash; }
    public void setPathHash(String pathHash) { this.pathHash = pathHash; }
    public BigDecimal getGrossQty() { return grossQty; }
    public void setGrossQty(BigDecimal grossQty) { this.grossQty = grossQty; }
    public BigDecimal getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(BigDecimal allocatedQty) { this.allocatedQty = allocatedQty; }
    public String getAllocationType() { return allocationType; }
    public void setAllocationType(String allocationType) { this.allocationType = allocationType; }
    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String adjustmentReason) { this.adjustmentReason = adjustmentReason; }
    public Boolean getManualConfirmed() { return manualConfirmed; }
    public void setManualConfirmed(Boolean manualConfirmed) { this.manualConfirmed = manualConfirmed; }
    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }
}
