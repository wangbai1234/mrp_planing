package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MrpTerminalDemand {
    private Long id;
    private Long runId;
    private Long batchId;
    private String factoryCode;
    private String rootMaterialCode;
    private String terminalMaterialCode;
    private String terminalType;
    private String supplyMode;
    private LocalDate sourceMonth;
    private BigDecimal quantity;
    private Integer inventoryPoolCount;
    private BigDecimal availableQty;
    private BigDecimal allocatedQty;
    private Integer warehouseRiskCount;
    private Integer affectedMachineCount;
    private Integer pendingIssueCount;
    private Integer pendingPolicyCount;

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
    public LocalDate getSourceMonth() { return sourceMonth; }
    public void setSourceMonth(LocalDate sourceMonth) { this.sourceMonth = sourceMonth; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public Integer getInventoryPoolCount() { return inventoryPoolCount; }
    public void setInventoryPoolCount(Integer inventoryPoolCount) { this.inventoryPoolCount = inventoryPoolCount; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(BigDecimal allocatedQty) { this.allocatedQty = allocatedQty; }
    public Integer getWarehouseRiskCount() { return warehouseRiskCount; }
    public void setWarehouseRiskCount(Integer warehouseRiskCount) { this.warehouseRiskCount = warehouseRiskCount; }
    public Integer getAffectedMachineCount() { return affectedMachineCount; }
    public void setAffectedMachineCount(Integer affectedMachineCount) { this.affectedMachineCount = affectedMachineCount; }
    public Integer getPendingIssueCount() { return pendingIssueCount; }
    public void setPendingIssueCount(Integer pendingIssueCount) { this.pendingIssueCount = pendingIssueCount; }
    public Integer getPendingPolicyCount() { return pendingPolicyCount; }
    public void setPendingPolicyCount(Integer pendingPolicyCount) { this.pendingPolicyCount = pendingPolicyCount; }
}
