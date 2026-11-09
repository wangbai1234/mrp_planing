package com.mrp.mrp.domain;

import java.math.BigDecimal;

public class MrpInventoryPool {
    private Long id;
    private Long batchId;
    private Long snapshotId;
    private String factoryCode;
    private String warehouseCode;
    private String inventoryOrg;
    private String materialCode;
    private BigDecimal availableQty;
    private BigDecimal reservedQty;
    private BigDecimal warningQty;
    private BigDecimal allocatedQty;
    private Integer version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public String getInventoryOrg() { return inventoryOrg; }
    public void setInventoryOrg(String inventoryOrg) { this.inventoryOrg = inventoryOrg; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public BigDecimal getReservedQty() { return reservedQty; }
    public void setReservedQty(BigDecimal reservedQty) { this.reservedQty = reservedQty; }
    public BigDecimal getWarningQty() { return warningQty; }
    public void setWarningQty(BigDecimal warningQty) { this.warningQty = warningQty; }
    public BigDecimal getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(BigDecimal allocatedQty) { this.allocatedQty = allocatedQty; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
