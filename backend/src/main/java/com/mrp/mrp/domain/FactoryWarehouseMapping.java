package com.mrp.mrp.domain;

public class FactoryWarehouseMapping extends ConfigAudit {
    private String factoryCode;
    private String factoryName;
    private String warehouseCode;
    private String warehouseName;
    private Boolean enabled;
    private String warehouseManager;
    private String supplierName;
    private Boolean goodWarehouse;
    private String warehouseAttribute;
    private Integer priorityNo;
    private Boolean qualityConfigured;

    public String getFactoryCode() { return factoryCode; }
    public void setFactoryCode(String factoryCode) { this.factoryCode = factoryCode; }
    public String getFactoryName() { return factoryName; }
    public void setFactoryName(String factoryName) { this.factoryName = factoryName; }
    public String getWarehouseCode() { return warehouseCode; }
    public void setWarehouseCode(String warehouseCode) { this.warehouseCode = warehouseCode; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getWarehouseManager() { return warehouseManager; }
    public void setWarehouseManager(String value) { warehouseManager = value; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String value) { supplierName = value; }
    public Boolean getGoodWarehouse() { return goodWarehouse; }
    public void setGoodWarehouse(Boolean value) { goodWarehouse = value; }
    public String getWarehouseAttribute() { return warehouseAttribute; }
    public void setWarehouseAttribute(String value) { warehouseAttribute = value; }
    public Integer getPriorityNo() { return priorityNo; }
    public void setPriorityNo(Integer value) { priorityNo = value; }
    public Boolean getQualityConfigured() { return qualityConfigured; }
    public void setQualityConfigured(Boolean value) { qualityConfigured = value; }
}
