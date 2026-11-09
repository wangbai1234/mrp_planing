package com.mrp.mrp.domain;

import java.math.BigDecimal;

public class MrpInventoryBalance {
    private Long id;
    private Long inventorySnapshotId;
    private String factoryCode;
    private String inventoryOrg;
    private String warehouseScope;
    private String materialCode;
    private String inventoryQuality;
    private BigDecimal availableQuantity;
    private String sourceRowRef;
    private Boolean includedFlag;
    private String normalizationRuleVersion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInventorySnapshotId() { return inventorySnapshotId; }
    public void setInventorySnapshotId(Long inventorySnapshotId) { this.inventorySnapshotId = inventorySnapshotId; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getInventoryOrg() { return inventoryOrg; }
    public void setInventoryOrg(String inventoryOrg) { this.inventoryOrg = inventoryOrg; }
    public String getWarehouseScope() { return warehouseScope; }
    public void setWarehouseScope(String warehouseScope) { this.warehouseScope = warehouseScope; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getInventoryQuality() { return inventoryQuality; }
    public void setInventoryQuality(String inventoryQuality) { this.inventoryQuality = inventoryQuality; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal availableQuantity) { this.availableQuantity = availableQuantity; }
    public String getSourceRowRef() { return sourceRowRef; }
    public void setSourceRowRef(String sourceRowRef) { this.sourceRowRef = sourceRowRef; }
    public Boolean getIncludedFlag() { return includedFlag; }
    public void setIncludedFlag(Boolean includedFlag) { this.includedFlag = includedFlag; }
    public String getNormalizationRuleVersion() { return normalizationRuleVersion; }
    public void setNormalizationRuleVersion(String normalizationRuleVersion) { this.normalizationRuleVersion = normalizationRuleVersion; }
}
