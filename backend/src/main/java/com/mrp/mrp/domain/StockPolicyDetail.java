package com.mrp.mrp.domain;

import java.math.BigDecimal;

public class StockPolicyDetail {
    private Long id;
    private Long versionId;
    private String warehouseCode;
    private String materialCode;
    private BigDecimal safetyStockQty;
    private BigDecimal warningStockQty;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public BigDecimal getSafetyStockQty() { return safetyStockQty; }
    public void setSafetyStockQty(BigDecimal safetyStockQty) { this.safetyStockQty = safetyStockQty; }
    public BigDecimal getWarningStockQty() { return warningStockQty; }
    public void setWarningStockQty(BigDecimal warningStockQty) { this.warningStockQty = warningStockQty; }
}
