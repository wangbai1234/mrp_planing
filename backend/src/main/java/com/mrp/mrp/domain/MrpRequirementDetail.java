package com.mrp.mrp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MrpRequirementDetail {
    private Long id;
    private Long runId;
    private Long batchId;
    private String factoryCode;
    private String rootMaterialCode;
    private LocalDate sourceMonth;
    private String parentMaterialCode;
    private String currentMaterialCode;
    private Integer levelNo;
    private String pathText;
    private String pathHash;
    private String categoryRole;
    private String supplyMode;
    private String explosionPolicy;
    private String effectiveExplosionPolicy;
    private String policySource;
    private BigDecimal grossQty;
    private BigDecimal inventoryBeforeQty;
    private BigDecimal inventoryUsedQty;
    private BigDecimal inventoryAfterQty;
    private BigDecimal netQty;
    private Boolean terminalFlag;
    private String terminalReason;

    public String getEffectiveExplosionPolicy() { return effectiveExplosionPolicy; }
    public void setEffectiveExplosionPolicy(String value) { effectiveExplosionPolicy = value; }
    public String getPolicySource() { return policySource; }
    public void setPolicySource(String value) { policySource = value; }

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
    public LocalDate getSourceMonth() { return sourceMonth; }
    public void setSourceMonth(LocalDate sourceMonth) { this.sourceMonth = sourceMonth; }
    public String getParentMaterialCode() { return parentMaterialCode; }
    public void setParentMaterialCode(String parentMaterialCode) { this.parentMaterialCode = parentMaterialCode; }
    public String getCurrentMaterialCode() { return currentMaterialCode; }
    public void setCurrentMaterialCode(String currentMaterialCode) { this.currentMaterialCode = currentMaterialCode; }
    public Integer getLevelNo() { return levelNo; }
    public void setLevelNo(Integer levelNo) { this.levelNo = levelNo; }
    public String getPathText() { return pathText; }
    public void setPathText(String pathText) { this.pathText = pathText; }
    public String getPathHash() { return pathHash; }
    public void setPathHash(String pathHash) { this.pathHash = pathHash; }
    public String getCategoryRole() { return categoryRole; }
    public void setCategoryRole(String categoryRole) { this.categoryRole = categoryRole; }
    public String getSupplyMode() { return supplyMode; }
    public void setSupplyMode(String supplyMode) { this.supplyMode = supplyMode; }
    public String getExplosionPolicy() { return explosionPolicy; }
    public void setExplosionPolicy(String explosionPolicy) { this.explosionPolicy = explosionPolicy; }
    public BigDecimal getGrossQty() { return grossQty; }
    public void setGrossQty(BigDecimal grossQty) { this.grossQty = grossQty; }
    public BigDecimal getInventoryBeforeQty() { return inventoryBeforeQty; }
    public void setInventoryBeforeQty(BigDecimal inventoryBeforeQty) { this.inventoryBeforeQty = inventoryBeforeQty; }
    public BigDecimal getInventoryUsedQty() { return inventoryUsedQty; }
    public void setInventoryUsedQty(BigDecimal inventoryUsedQty) { this.inventoryUsedQty = inventoryUsedQty; }
    public BigDecimal getInventoryAfterQty() { return inventoryAfterQty; }
    public void setInventoryAfterQty(BigDecimal inventoryAfterQty) { this.inventoryAfterQty = inventoryAfterQty; }
    public BigDecimal getNetQty() { return netQty; }
    public void setNetQty(BigDecimal netQty) { this.netQty = netQty; }
    public Boolean getTerminalFlag() { return terminalFlag; }
    public void setTerminalFlag(Boolean terminalFlag) { this.terminalFlag = terminalFlag; }
    public String getTerminalReason() { return terminalReason; }
    public void setTerminalReason(String terminalReason) { this.terminalReason = terminalReason; }
}
