package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MrpStockRisk {
    private Long id;
    private Long batchId;
    private Long runId;
    private String factoryCode;
    private String riskType;
    private String warehouseCode;
    private String materialCode;
    private BigDecimal availableQty;
    private BigDecimal usedQty;
    private BigDecimal remainingQty;
    private BigDecimal safetyStockQty;
    private BigDecimal warningStockQty;
    private String rootMaterialCodes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
    private String resolutionCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getRiskType() { return riskType; }
    public void setRiskType(String riskType) { this.riskType = riskType; }
    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getUsedQty() { return usedQty; }
    public void setUsedQty(BigDecimal usedQty) { this.usedQty = usedQty; }
    public BigDecimal getRemainingQty() { return remainingQty; }
    public void setRemainingQty(BigDecimal remainingQty) { this.remainingQty = remainingQty; }
    public BigDecimal getSafetyStockQty() { return safetyStockQty; }
    public void setSafetyStockQty(BigDecimal safetyStockQty) { this.safetyStockQty = safetyStockQty; }
    public BigDecimal getWarningStockQty() { return warningStockQty; }
    public void setWarningStockQty(BigDecimal warningStockQty) { this.warningStockQty = warningStockQty; }
    public String getRootMaterialCodes() { return rootMaterialCodes; }
    public void setRootMaterialCodes(String rootMaterialCodes) { this.rootMaterialCodes = rootMaterialCodes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(String resolutionCode) { this.resolutionCode = resolutionCode; }
}
