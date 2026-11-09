package com.mrp.mrp.domain;

public class MaterialPolicy extends ConfigAudit {
    private String materialCode;
    private String supplyMode;
    private String explosionPolicy;

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getSupplyMode() { return supplyMode; }
    public void setSupplyMode(String supplyMode) { this.supplyMode = supplyMode; }
    public String getExplosionPolicy() { return explosionPolicy; }
    public void setExplosionPolicy(String explosionPolicy) { this.explosionPolicy = explosionPolicy; }
}
